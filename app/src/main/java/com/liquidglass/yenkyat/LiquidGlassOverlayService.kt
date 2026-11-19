package com.liquidglass.yenkyat

import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView
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
import kotlin.math.abs
import kotlin.math.roundToInt

class LiquidGlassOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayView: ComposeView? = null
    private var edgeAnimator: ValueAnimator? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var windowStartX = 0
    private var windowStartY = 0
    private var isDragging = false
    private var dockedRight = true

    private val screenWidth get() = resources.displayMetrics.widthPixels
    private val screenHeight get() = resources.displayMetrics.heightPixels
    private val ballSizePx get() = (52 * resources.displayMetrics.density).roundToInt()
    private val pillWidthPx get() = (242 * resources.displayMetrics.density).roundToInt()

    override fun onCreate() {
        super.onCreate()
        PriceBus.init(applicationContext)
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        startForegroundNotification()
        setupFloatingWindow()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        PriceBus.setOverlayRunning(true)
    }

    private fun startForegroundNotification() {
        val channelId = "liquid_glass_overlay_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Liquid Glass Quick Ball", NotificationManager.IMPORTANCE_LOW)
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Liquid ¥→Ks Quick Ball ဖွင့်ထားသည်")
            .setContentText("Quick Ball ကိုနှိပ်ပြီး Screen ပေါ်က ¥ ဈေးနှုန်း ဖတ်နိုင်ပါသည်")
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

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - ballSizePx - 8).coerceAtLeast(8)
            y = (screenHeight * 0.36f).roundToInt()
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LiquidGlassOverlayService)
            setViewTreeViewModelStoreOwner(this@LiquidGlassOverlayService)
            setViewTreeSavedStateRegistryOwner(this@LiquidGlassOverlayService)
            setContent {
                LiquidGlassQuickBallRoot(
                    onBallTouch = ::handleBallTouch,
                    onPillDragTouch = ::handlePillDragTouch,
                    onExpandedChanged = ::handleExpandedChanged,
                    onFocusableChange = ::setOverlayFocusable,
                    onCloseOverlay = { stopSelf() }
                )
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, layoutParams)
    }

    private fun handleBallTouch(event: MotionEvent, onTap: () -> Unit): Boolean {
        val view = overlayView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeAnimator?.cancel()
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                windowStartX = layoutParams.x
                windowStartY = layoutParams.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragStartRawX
                val dy = event.rawY - dragStartRawY
                if (!isDragging && (abs(dx) > 16f || abs(dy) > 16f)) {
                    isDragging = true
                }
                if (isDragging) {
                    layoutParams.x = (windowStartX + dx.roundToInt()).coerceIn(0, (screenWidth - ballSizePx).coerceAtLeast(0))
                    layoutParams.y = (windowStartY + dy.roundToInt()).coerceIn(70, (screenHeight - ballSizePx - 80).coerceAtLeast(70))
                    runCatching { windowManager.updateViewLayout(view, layoutParams) }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    dockedRight = (layoutParams.x + ballSizePx / 2) >= (screenWidth / 2)
                    snapBallToEdge()
                } else {
                    onTap()
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return false
    }

    private fun handlePillDragTouch(event: MotionEvent): Boolean {
        val view = overlayView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeAnimator?.cancel()
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                windowStartX = layoutParams.x
                windowStartY = layoutParams.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - dragStartRawX).roundToInt()
                val dy = (event.rawY - dragStartRawY).roundToInt()
                layoutParams.x = (windowStartX + dx).coerceIn(8, (screenWidth - pillWidthPx - 8).coerceAtLeast(8))
                layoutParams.y = (windowStartY + dy).coerceIn(70, (screenHeight - 240).coerceAtLeast(70))
                dockedRight = (layoutParams.x + pillWidthPx / 2) >= (screenWidth / 2)
                runCatching { windowManager.updateViewLayout(view, layoutParams) }
                return true
            }
        }
        return false
    }

    private fun handleExpandedChanged(expanded: Boolean) {
        if (expanded) {
            val maxX = (screenWidth - pillWidthPx - 12).coerceAtLeast(8)
            val targetX = if (dockedRight) maxX else 12
            animateWindowX(targetX)
        } else {
            setOverlayFocusable(false)
            snapBallToEdge()
        }
    }

    private fun snapBallToEdge() {
        val targetX = if (dockedRight) (screenWidth - ballSizePx - 6).coerceAtLeast(6) else 6
        animateWindowX(targetX)
    }

    private fun animateWindowX(targetX: Int) {
        val view = overlayView ?: return
        edgeAnimator?.cancel()
        val startX = layoutParams.x
        if (startX == targetX) return
        edgeAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, layoutParams) }
            }
            start()
        }
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val view = overlayView ?: return
        layoutParams.flags = if (focusable) {
            (layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            layoutParams.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        runCatching { windowManager.updateViewLayout(view, layoutParams) }
    }

    override fun onDestroy() {
        super.onDestroy()
        edgeAnimator?.cancel()
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
