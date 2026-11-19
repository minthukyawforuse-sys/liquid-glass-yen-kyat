package com.liquidglass.yenkyat

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PriceAccessibilityService : AccessibilityService() {

    private var lastScanTimeMs = 0L

    private val prefixPriceRegex = Regex(
        pattern = """(?:[¥￥]|JPY|CNY|RMB)\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    private val suffixPriceRegex = Regex(
        pattern = """([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:円|yen)""",
        option = RegexOption.IGNORE_CASE
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        PriceBus.init(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !PriceBus.state.value.autoScanEnabled) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return

        val now = System.currentTimeMillis()
        if (now - lastScanTimeMs < 350L) return
        lastScanTimeMs = now

        val root = rootInActiveWindow ?: return
        val foundPrices = LinkedHashSet<Double>()

        try {
            scanNodeTree(root, foundPrices, depth = 0)
        } finally {
            root.recycle()
        }

        if (foundPrices.isNotEmpty()) {
            val appName = getReadableAppName(pkg)
            PriceBus.updateDetectedPrices(
                prices = foundPrices.take(5),
                sourceApp = "$appName မှ ဖတ်ရရှိ"
            )
        }
    }

    private fun scanNodeTree(
        node: AccessibilityNodeInfo?,
        results: MutableSet<Double>,
        depth: Int
    ) {
        if (node == null || depth > 28 || results.size >= 6) return

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        if (!text.isNullOrBlank()) extractPrices(text, results)
        if (!desc.isNullOrBlank()) extractPrices(desc, results)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                scanNodeTree(child, results, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun extractPrices(input: String, results: MutableSet<Double>) {
        prefixPriceRegex.findAll(input).forEach { match ->
            val raw = match.groupValues.getOrNull(1)?.replace(",", "")
            val value = raw?.toDoubleOrNull()
            if (value != null && value > 0.0 && value <= 99_999_999.0) {
                results.add(value)
            }
        }
        suffixPriceRegex.findAll(input).forEach { match ->
            val raw = match.groupValues.getOrNull(1)?.replace(",", "")
            val value = raw?.toDoubleOrNull()
            if (value != null && value > 0.0 && value <= 99_999_999.0) {
                results.add(value)
            }
        }
    }

    private fun getReadableAppName(pkg: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrElse { pkg.substringAfterLast('.') }
    }

    override fun onInterrupt() {
        // No-op
    }
}
