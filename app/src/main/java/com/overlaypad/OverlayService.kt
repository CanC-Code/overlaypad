package com.overlaypad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var store: OverlayLayoutStore
    private var overlayView: OverlayPadView? = null
    private var collapsedBubble: TextView? = null
    private var inspectorView: View? = null
    private var isCollapsed = false
    private var editMode = false

    private val handler = Handler(Looper.getMainLooper())
    private var lastObservedPackage = ""

    private val gameWatcherRunnable = object : Runnable {
        override fun run() {
            checkForegroundGameAndAutoLoad()
            handler.postDelayed(this, 1600L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        store = OverlayLayoutStore(this)
        showExpandedOverlay()
        handler.postDelayed(gameWatcherRunnable, 1200L)
    }

    private fun checkForegroundGameAndAutoLoad() {
        val svc = ShizukuController.service ?: return
        val pkg = try {
            svc.foregroundPackage ?: ""
        } catch (_: Throwable) {
            ""
        }
        if (pkg.isBlank() || pkg == packageName || pkg == "com.android.systemui" || pkg.contains("launcher", ignoreCase = true)) return

        if (pkg != store.getLastDetectedGamePackage()) {
            store.setLastDetectedGamePackage(pkg)
        }

        if (!store.isAutoLoadPerGameEnabled() || editMode) return
        if (pkg == lastObservedPackage) return
        lastObservedPackage = pkg

        val matchedProfile = store.findProfileForPackage(pkg) ?: return
        overlayView?.releaseAllInputs()
        if (store.loadNamedProfile(matchedProfile)) {
            overlayView?.overlayOpacity = store.getOverlayOpacity()
            overlayView?.stickSensitivity = store.getStickSensitivity()
            overlayView?.stickDeadzone = store.getStickDeadzone()
            overlayView?.reloadControls()
        }
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
        removeInspector()
        if (overlayView != null) {
            overlayView?.editMode = editMode
            overlayView?.overlayOpacity = store.getOverlayOpacity()
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
                if (!enabled) removeInspector()
            },
            onCollapseRequest = {
                collapseOverlay()
            },
            onInspectControlRequest = { control, allControls ->
                showControlInspector(control, allControls)
            }
        ).apply {
            this.editMode = this@OverlayService.editMode
            this.overlayOpacity = store.getOverlayOpacity()
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

    private fun showControlInspector(control: PadControl, allControls: List<PadControl>) {
        removeInspector()

        val cardBg = GradientDrawable().apply {
            setColor(Color.argb(245, 22, 27, 38))
            cornerRadius = 28f
            setStroke(3, Color.argb(235, 250, 204, 21))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(36, 28, 36, 28)
        }

        val title = TextView(this).apply {
            text = "Configure ${control.defaultLabel}"
            setTextColor(Color.WHITE)
            textSize = 18f
        }
        root.addView(title)

        val sizeLabel = TextView(this).apply {
            text = "Size: ${control.radiusPx.toInt()}px"
            setTextColor(Color.argb(220, 186, 230, 253))
            textSize = 14f
            setPadding(0, 10, 0, 6)
        }
        root.addView(sizeLabel)

        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        val smallerBtn = Button(this).apply {
            text = "- Smaller"
            setOnClickListener {
                control.radiusPx = (control.radiusPx - 12f).coerceIn(36f, 360f)
                sizeLabel.text = "Size: ${control.radiusPx.toInt()}px"
                overlayView?.saveCurrentControls()
            }
        }
        sizeRow.addView(smallerBtn)

        val largerBtn = Button(this).apply {
            text = "+ Larger"
            setOnClickListener {
                control.radiusPx = (control.radiusPx + 12f).coerceIn(36f, 360f)
                sizeLabel.text = "Size: ${control.radiusPx.toInt()}px"
                overlayView?.saveCurrentControls()
            }
        }
        sizeRow.addView(largerBtn)
        root.addView(sizeRow)

        val hideCheck = CheckBox(this).apply {
            text = "Hide ${control.defaultLabel} during play"
            setTextColor(Color.WHITE)
            isChecked = control.hidden
            setOnCheckedChangeListener { _, checked ->
                control.hidden = checked
                overlayView?.saveCurrentControls()
            }
        }
        root.addView(hideCheck)

        if (!control.isStick) {
            val stickyCheck = CheckBox(this).apply {
                text = "Sticky Toggle (1st tap = hold, 2nd tap = release)"
                setTextColor(Color.argb(255, 253, 224, 71))
                isChecked = control.sticky
                setOnCheckedChangeListener { _, checked ->
                    control.sticky = checked
                    if (!checked) overlayView?.releaseAllInputs()
                    overlayView?.saveCurrentControls()
                }
            }
            root.addView(stickyCheck)

            val mapLabel = TextView(this).apply {
                text = "Mapped Output Action:"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, 14, 0, 6)
            }
            root.addView(mapLabel)

            val options = OverlayLayoutStore.ACTION_OPTIONS
            val labels = options.map { "${it.label} (${it.id.uppercase()})" }
            val currentIdx = options.indexOfFirst { it.id == (control.mappedActionId ?: control.id) }.coerceAtLeast(0)

            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@OverlayService,
                    android.R.layout.simple_spinner_dropdown_item,
                    labels
                )
                setSelection(currentIdx, false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val chosen = options[position]
                        control.mappedActionId = chosen.id
                        control.linuxCodePrimary = chosen.primaryCode
                        control.linuxCodeSecondary = chosen.secondaryCode
                        overlayView?.saveCurrentControls()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
            root.addView(spinner)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 18, 0, 0)
        }

        val releaseBtn = Button(this).apply {
            text = "Release All"
            setOnClickListener {
                overlayView?.releaseAllInputs()
            }
        }
        btnRow.addView(releaseBtn)

        val showAllBtn = Button(this).apply {
            text = "Unhide All"
            setOnClickListener {
                for (c in allControls) c.hidden = false
                hideCheck.isChecked = false
                overlayView?.saveCurrentControls()
            }
        }
        btnRow.addView(showAllBtn)

        val closeBtn = Button(this).apply {
            text = "Close"
            setOnClickListener {
                removeInspector()
            }
        }
        btnRow.addView(closeBtn)

        root.addView(btnRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(root, params)
        inspectorView = root
    }

    private fun removeInspector() {
        inspectorView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {
            }
        }
        inspectorView = null
    }

    fun collapseOverlay() {
        editMode = false
        removeInspector()
        overlayView?.editMode = false
        overlayView?.releaseAllInputs()
        removeExpandedOverlay()
        if (collapsedBubble != null) {
            isCollapsed = true
            return
        }

        val bubbleBg = GradientDrawable().apply {
            setColor(Color.argb(215, 22, 29, 44))
            cornerRadius = 999f
            setStroke(2, Color.argb(215, 56, 189, 248))
        }

        val bubble = TextView(this).apply {
            text = "🎮"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = bubbleBg
            minWidth = 84
            minHeight = 84
            setPadding(16, 14, 16, 14)
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
                if (!editMode) removeInspector()
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
            ACTION_RELEASE_ALL -> {
                overlayView?.releaseAllInputs()
            }
            ACTION_UPDATE_SETTINGS -> {
                lastObservedPackage = ""
                overlayView?.overlayOpacity = store.getOverlayOpacity()
                overlayView?.stickSensitivity = store.getStickSensitivity()
                overlayView?.stickDeadzone = store.getStickDeadzone()
                overlayView?.reloadControls()
            }
            ACTION_RESET_LAYOUT -> {
                removeInspector()
                overlayView?.resetLayout()
            }
            ACTION_STOP_OVERLAY -> {
                editMode = false
                removeInspector()
                overlayView?.editMode = false
                overlayView?.releaseAllInputs()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(gameWatcherRunnable)
        editMode = false
        removeInspector()
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
        const val ACTION_RELEASE_ALL = "com.overlaypad.ACTION_RELEASE_ALL"
        const val ACTION_UPDATE_SETTINGS = "com.overlaypad.ACTION_UPDATE_SETTINGS"
        const val ACTION_RESET_LAYOUT = "com.overlaypad.ACTION_RESET_LAYOUT"
        const val ACTION_STOP_OVERLAY = "com.overlaypad.ACTION_STOP_OVERLAY"
        const val EXTRA_EDIT_MODE = "extra_edit_mode"
    }
}
