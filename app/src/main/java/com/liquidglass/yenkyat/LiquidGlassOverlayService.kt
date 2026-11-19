package com.liquidglass.yenkyat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

class LiquidGlassOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var windowStartX = 0
    private var windowStartY = 0

    override fun onCreate() {
        super.onCreate()
        PriceBus.init(applicationContext)
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        startForegroundNotification()
        setupFloatingGlassWindow()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        PriceBus.setOverlayRunning(true)
    }

    private fun startForegroundNotification() {
        val channelId = "liquid_glass_overlay_channel"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "Liquid Glass ¥→MMK Overlay",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Liquid Glass ¥ → ကျပ် ဖွင့်ထားသည်")
            .setContentText("Screen ပေါ်က ဈေးနှုန်းများကို အလိုအလျောက်ဖတ်ပေးနေပါသည်")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    private fun setupFloatingGlassWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            } else 0

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 36
            y = 220
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 56
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LiquidGlassOverlayService)
            setViewTreeViewModelStoreOwner(this@LiquidGlassOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LiquidGlassOverlayService)

            setContent {
                LiquidGlassFloatingCard(
                    onDragEvent = ::handleWindowDragEvent,
                    onFocusableChange = ::setOverlayFocusable,
                    onCloseOverlay = { stopSelf() }
                )
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, layoutParams)
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val view = overlayView ?: return
        layoutParams.flags = if (focusable) {
            layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { windowManager.updateViewLayout(view, layoutParams) }
    }

    private fun handleWindowDragEvent(event: MotionEvent): Boolean {
        val view = overlayView ?: return false
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                windowStartX = layoutParams.x
                windowStartY = layoutParams.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                layoutParams.x = windowStartX + (event.rawX - dragStartRawX).roundToInt()
                layoutParams.y = windowStartY + (event.rawY - dragStartRawY).roundToInt()
                runCatching { windowManager.updateViewLayout(view, layoutParams) }
                true
            }
            else -> false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PriceBus.setOverlayRunning(false)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        store.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LiquidGlassFloatingCard(
    onDragEvent: (MotionEvent) -> Boolean,
    onFocusableChange: (Boolean) -> Unit,
    onCloseOverlay: () -> Unit
) {
    val state by PriceBus.state.collectAsState()
    val context = LocalContext.current

    var isCollapsed by remember { mutableStateOf(false) }
    var isEditingRate by remember { mutableStateOf(false) }
    var rateDraft by remember(state.currency, state.activeRate) {
        mutableStateOf(formatDecimal(state.activeRate))
    }

    LaunchedEffect(isEditingRate) {
        onFocusableChange(isEditingRate)
    }

    val animatedMmk by animateIntAsState(
        targetValue = state.convertedMmk.roundToInt(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "mmkTicker"
    )

    val sweepProgress = remember { Animatable(-0.5f) }
    val popScale = remember { Animatable(1f) }

    LaunchedEffect(state.detectedAmount, state.activeRate, state.currency) {
        popScale.snapTo(1.06f)
        popScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f)
        )
    }

    LaunchedEffect(state.detectedAmount, state.activeRate, state.currency) {
        sweepProgress.snapTo(-0.4f)
        sweepProgress.animateTo(
            targetValue = 1.5f,
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
        )
    }

    val glassShape = if (isCollapsed) CircleShape else RoundedCornerShape(28.dp)

    val glassBgBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x521E293B),
            Color(0x3D0F172A),
            Color(0x4A1E293B)
        )
    )
    val glassRimBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xCCFFFFFF),
            Color(0x807DD3FC),
            Color(0x2EFFFFFF),
            Color(0x88D8B4FE),
            Color(0xB3FFFFFF)
        )
    )

    Box(
        modifier = Modifier
            .widthIn(min = if (isCollapsed) 220.dp else 306.dp, max = 324.dp)
            .clip(glassShape)
            .background(glassBgBrush)
            .drawWithContent {
                drawContent()
                val p = sweepProgress.value
                if (p > -0.35f && p < 1.45f) {
                    val xCenter = size.width * p
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            start = Offset(xCenter - 90f, 0f),
                            end = Offset(xCenter + 90f, size.height)
                        )
                    )
                }
            }
            .border(width = 1.25.dp, brush = glassRimBrush, shape = glassShape)
            .padding(horizontal = 14.dp, vertical = if (isCollapsed) 10.dp else 12.dp)
    ) {
        if (isCollapsed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInteropFilter { onDragEvent(it) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.autoScanEnabled) Color(0xFF34D399) else Color(0xFFFBBF24))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "¥${formatInt(state.detectedAmount.roundToInt())} → ${formatInt(animatedMmk)} Ks",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                GlassPillButton(text = "▼") { isCollapsed = false }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInteropFilter { onDragEvent(it) }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.45f))
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (state.autoScanEnabled) Color(0xFF34D399)
                                        else Color(0xFFFBBF24)
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.autoScanEnabled) "Auto-Read ON" else "Paused",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassPillButton(
                                text = if (state.autoScanEnabled) "Auto" else "Off",
                                active = state.autoScanEnabled
                            ) { PriceBus.toggleAutoScan() }
                            GlassPillButton(text = "—") {
                                isEditingRate = false
                                isCollapsed = true
                            }
                            GlassPillButton(text = "✕") { onCloseOverlay() }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x45090D16))
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    YenCurrency.entries.forEach { curr ->
                        val selected = state.currency == curr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.24f)
                                    else Color.Transparent
                                )
                                .clickable { PriceBus.setCurrency(context, curr) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = curr.label,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = state.sourceLabel,
                    color = Color(0xFFBAE6FD),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (state.detectedPrices.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.detectedPrices.forEach { price ->
                            val isSelected = price == state.detectedAmount
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) Color(0x6638BDF8)
                                        else Color.White.copy(alpha = 0.12f)
                                    )
                                    .border(
                                        0.8.dp,
                                        if (isSelected) Color(0xFF7DD3FC) else Color.White.copy(alpha = 0.2f),
             
