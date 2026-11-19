package com.liquidglass.yenkyat

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class PriceAccessibilityService : AccessibilityService() {

    private var lastPassiveScanMs = 0L
    private val textRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val prefixRegex = Regex(
        """(?:[¥￥Ұ]|JPY|CNY|RMB)\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val suffixRegex = Regex(
        """([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:円|元|yen|jpy|cny|rmb)""",
        RegexOption.IGNORE_CASE
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        PriceBus.init(applicationContext)
        PriceBus.setAccessibilityConnected(true)
        PriceBus.onRequestScan = { performFullScan() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (pkg == packageName || pkg == "com.android.systemui") return

        val now = System.currentTimeMillis()
        if (now - lastPassiveScanMs < 450L) return
        lastPassiveScanMs = now

        val found = scanAccessibilityWindows()
        if (found.isNotEmpty()) {
            PriceBus.updateDetectedPrices(found.take(6))
        }
    }

    private fun performFullScan() {
        PriceBus.setScanning(true, "ဈေးနှုန်း ဖတ်နေသည်...")

        val foundFromNodes = scanAccessibilityWindows()
        if (foundFromNodes.isNotEmpty()) {
            PriceBus.updateDetectedPrices(foundFromNodes.take(6))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runScreenOcrFallback()
        } else {
            PriceBus.setScanning(false, "¥ ဈေးနှုန်း မတွေ့ပါ")
        }
    }

    private fun runScreenOcrFallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        var softBitmap: Bitmap? = null
                        try {
                            val hwBitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            softBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hwBitmap?.recycle()
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }

                        if (softBitmap == null) {
                            PriceBus.setScanning(false, "Screen ဖတ်၍မရပါ")
                            return
                        }

                        val bitmapToClean = softBitmap
                        val image = InputImage.fromBitmap(bitmapToClean, 0)
                        textRecognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                val tokens = mutableListOf<String>()
                                visionText.textBlocks.forEach { block ->
                                    block.lines.forEach { line ->
                                        val cleaned = line.text.trim()
                                        if (cleaned.isNotEmpty()) tokens.add(cleaned)
                                    }
                                }
                                val found = LinkedHashSet<Double>()
                                extractPricesFromTokens(tokens, found)
                                if (found.isEmpty() && visionText.text.isNotBlank()) {
                                    findMatches(visionText.text.replace("\n", " "), found)
                                }
                                if (found.isNotEmpty()) {
                                    PriceBus.updateDetectedPrices(found.take(6))
                                } else {
                                    PriceBus.setScanning(false, "¥ ဈေးနှုန်း မတွေ့ပါ")
                                }
                            }
                            .addOnFailureListener {
                                PriceBus.setScanning(false, "OCR ဖတ်၍မရပါ")
                            }
                            .addOnCompleteListener {
                                bitmapToClean.recycle()
                            }
                    }

                    override fun onFailure(errorCode: Int) {
                        PriceBus.setScanning(false, "¥ ဈေးနှုန်း မတွေ့ပါ")
                    }
                }
            )
        }.onFailure {
            PriceBus.setScanning(false, "¥ ဈေးနှုန်း မတွေ့ပါ")
        }
    }

    private fun scanAccessibilityWindows(): List<Double> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        runCatching { windows }.getOrNull()?.forEach { win ->
            if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                val root = win.root ?: return@forEach
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg.isNotBlank() && pkg != packageName && pkg != "com.android.systemui") {
                    roots.add(root)
                } else {
                    root.recycle()
                }
            }
        }
        if (roots.isEmpty()) {
            rootInActiveWindow?.let { root ->
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg != packageName && pkg != "com.android.systemui") roots.add(root)
                else root.recycle()
            }
        }

        val results = LinkedHashSet<Double>()
        try {
            roots.forEach { root ->
                val tokens = ArrayList<String>(128)
                collectTokens(root, tokens, 0)
                extractPricesFromTokens(tokens, results)
            }
        } finally {
            roots.forEach { runCatching { it.recycle() } }
        }
        return results.toList()
    }

    private fun collectTokens(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 30 || out.size >= 360) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { collectTokens(child, out, depth + 1) } finally { runCatching { child.recycle() } }
        }
    }

    private fun extractPricesFromTokens(tokens: List<String>, results: MutableSet<Double>) {
        tokens.forEach { findMatches(it, results) }
        var i = 0
        while (i < tokens.size && results.size < 8) {
            val t0 = tokens[i]
            val t1 = tokens.getOrNull(i + 1)
            val t2 = tokens.getOrNull(i + 2)
            if (t1 != null && t2 != null && t0.length <= 8 && t1.length <= 12 && t2.length <= 6) {
                val before = results.size
                findMatches(t0 + t1 + t2, results)
                if (results.size > before) { i += 3; continue }
            }
            if (t1 != null && t0.length <= 10 && t1.length <= 12) {
                val before = results.size
                findMatches(t0 + t1, results)
                if (results.size > before) { i += 2; continue }
            }
            i++
        }
    }

    private fun findMatches(raw: String, results: MutableSet<Double>) {
        val text = raw.replace(Regex("""\s*\.\s*"""), ".")
        prefixRegex.findAll(text).forEach { m ->
            m.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
                ?.takeIf { it > 0.0 && it <= 99_999_999.0 }?.let { results.add(it) }
        }
        suffixRegex.findAll(text).forEach { m ->
            m.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
                ?.takeIf { it > 0.0 && it <= 99_999_999.0 }?.let { results.add(it) }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        PriceBus.setAccessibilityConnected(false)
        PriceBus.onRequestScan = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        PriceBus.setAccessibilityConnected(false)
        PriceBus.onRequestScan = null
        runCatching { textRecognizer.close() }
        super.onDestroy()
    }

    override fun onInterrupt() {}
}
