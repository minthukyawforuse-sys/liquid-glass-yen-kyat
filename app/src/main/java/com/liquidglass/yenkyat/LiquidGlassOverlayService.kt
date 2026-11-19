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
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Liquid Glass Overlay", NotificationManager.IMPORTANCE_LOW)
        )

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurBehindRadius = 56
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
