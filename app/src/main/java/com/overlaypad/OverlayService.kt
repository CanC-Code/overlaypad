package com.overlaypad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayPadView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val store = OverlayLayoutStore(this)
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
            }
        )

        val typeFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            typeFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_EDIT_MODE -> {
                overlayView?.editMode = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)
            }
            ACTION_RESET_LAYOUT -> {
                overlayView?.resetLayout()
            }
            ACTION_STOP_OVERLAY -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {
            }
        }
        overlayView = null
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
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OverlayPad Active")
            .setContentText("Virtual gamepad overlay is running")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1, notification)
    }

    companion object {
        const val ACTION_SET_EDIT_MODE = "com.overlaypad.ACTION_SET_EDIT_MODE"
        const val ACTION_RESET_LAYOUT = "com.overlaypad.ACTION_RESET_LAYOUT"
        const val ACTION_STOP_OVERLAY = "com.overlaypad.ACTION_STOP_OVERLAY"
        const val EXTRA_EDIT_MODE = "extra_edit_mode"
    }
}
