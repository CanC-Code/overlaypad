package com.overlaypad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var store: OverlayLayoutStore
    private var overlayView: OverlayPadView? = null
    private var collapsedBubble: TextView? = null
    private var isCollapsed = false
    private var editMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        store = OverlayLayoutStore(this)
        showExpandedOverlay()
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun showExpandedOverlay() {
        removeCollapsedBubble()
        if (overlayView != null) {
            overlayView?.editMode = editMode
            overlayView?.stickSensitivity = store.getStickSensitivity()
            overlayView?.stickDeadzone = store.getStickDeadzone()
            overlayView?.reloadControls()
            isCollapsed = false
            return
        }

        val view = OverlayPadView(
            context = this,
            store = store,
            onButtonEvent = { code, pressed ->
                try {
                    ShizukuController.service?.setButton(code, pressed)
                } catch (_: Throwable) {
                }
            },
            onAxisEvent = { code, value ->
                try {
                    ShizukuController.service?.setAbsAxis(code, value)
                } catch (_: Throwable) {
                }
            },
            onEditModeToggleRequest = { enabled ->
                editMode = enabled
            },
            onCollapseRequest = {
                collapseOverlay()
            }
        ).apply {
            this.editMode = this@OverlayService.editMode
            this.stickSensitivity = store.getStickSensitivity()
            this.stickDeadzone = store.getStickDeadzone()
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(view, params)
        overlayView = view
        isCollapsed = false
    }

    fun collapseOverlay() {
        editMode = false
        overlayView?.editMode = false
        overlayView?.releaseAllInputs()
        removeExpandedOverlay()
        if (collapsedBubble != null) {
            isCollapsed = true
            return
        }

        val bubble = TextView(this).apply {
            text = "PAD"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(215, 25, 95, 185))
            setPadding(28, 20, 28, 20)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0

        bubble.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + (event.rawX - downX).toInt()).coerceAtLeast(0)
                    params.y = (startY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                    try {
                        windowManager.updateViewLayout(v, params)
                    } catch (_: Throwable) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dist = hypot(event.rawX - downX, event.rawY - downY)
                    if (dist < 18f) {
                        showExpandedOverlay()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        collapsedBubble = bubble
        isCollapsed = true
    }

    private fun removeExpandedOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {
            }
        }
        overlayView = null
    }

    private fun removeCollapsedBubble() {
        collapsedBubble?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {
            }
        }
        collapsedBubble = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_EDIT_MODE -> {
                editMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
                if (editMode && isCollapsed) {
                    showExpandedOverlay()
                } else {
                    overlayView?.editMode = editMode
                }
            }
            ACTION_COLLAPSE_OVERLAY -> {
                collapseOverlay()
            }
            ACTION_EXPAND_OVERLAY -> {
                showExpandedOverlay()
            }
            ACTION_UPDATE_SETTINGS -> {
                overlayView?.stickSensitivity = store.getStickSensitivity()
                overlayView?.stickDeadzone = store.getStickDeadzone()
                overlayView?.reloadControls()
            }
            ACTION_RESET_LAYOUT -> {
                overlayView?.resetLayout()
            }
            ACTION_STOP_OVERLAY -> {
                editMode = false
                overlayView?.editMode = false
                overlayView?.releaseAllInputs()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        editMode = false
        overlayView?.releaseAllInputs()
        removeExpandedOverlay()
        removeCollapsedBubble()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val channelId = "overlaypad_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "OverlayPad Gamepad",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val expandIntent = PendingIntent.getService(
            this,
            10,
            Intent(this, OverlayService::class.java).apply { action = ACTION_EXPAND_OVERLAY },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OverlayPad Active")
            .setContentText("Tap to expand overlay or open app to configure")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(expandIntent)
            .build()
        startForeground(1, notification)
    }

    companion object {
        const val ACTION_SET_EDIT_MODE = "com.overlaypad.ACTION_SET_EDIT_MODE"
        const val ACTION_COLLAPSE_OVERLAY = "com.overlaypad.ACTION_COLLAPSE_OVERLAY"
        const val ACTION_EXPAND_OVERLAY = "com.overlaypad.ACTION_EXPAND_OVERLAY"
        const val ACTION_UPDATE_SETTINGS = "com.overlaypad.ACTION_UPDATE_SETTINGS"
        const val ACTION_RESET_LAYOUT = "com.overlaypad.ACTION_RESET_LAYOUT"
        const val ACTION_STOP_OVERLAY = "com.overlaypad.ACTION_STOP_OVERLAY"
        const val EXTRA_EDIT_MODE = "extra_edit_mode"
    }
}
