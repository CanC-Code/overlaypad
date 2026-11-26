package com.overlaypad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var editButton: Button
    private lateinit var settingsToggleButton: Button
    private lateinit var settingsPanel: LinearLayout
    private lateinit var sensitivityLabel: TextView
    private lateinit var sensitivitySeek: SeekBar
    private lateinit var deadzoneLabel: TextView
    private lateinit var deadzoneSeek: SeekBar
    private lateinit var hideContainer: LinearLayout
    private lateinit var store: OverlayLayoutStore

    private var editMode = false
    private var settingsOpen = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        if (ShizukuController.hasPermission()) {
            ShizukuController.bindUserService(this) { _, _ ->
                runOnUiThread { refreshStatus() }
            }
        }
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = OverlayLayoutStore(this)
        statusText = findViewById(R.id.status_text)
        editButton = findViewById(R.id.btn_toggle_edit)
        settingsToggleButton = findViewById(R.id.btn_toggle_settings)
        settingsPanel = findViewById(R.id.settings_panel)
        sensitivityLabel = findViewById(R.id.tv_sensitivity)
        sensitivitySeek = findViewById(R.id.seek_sensitivity)
        deadzoneLabel = findViewById(R.id.tv_deadzone)
        deadzoneSeek = findViewById(R.id.seek_deadzone)
        hideContainer = findViewById(R.id.hide_controls_container)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        findViewById<Button>(R.id.btn_overlay_perm).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            refreshStatus()
        }

        findViewById<Button>(R.id.btn_shizuku_perm).setOnClickListener {
            if (!ShizukuController.hasPermission()) {
                ShizukuController.requestPermission()
            } else {
                ShizukuController.bindUserService(this) { _, _ ->
                    runOnUiThread { refreshStatus() }
                }
            }
            refreshStatus()
        }

        findViewById<Button>(R.id.btn_start_overlay).setOnClickListener {
            if (ShizukuController.hasPermission() && ShizukuController.service == null) {
                ShizukuController.bindUserService(this) { _, _ ->
                    runOnUiThread { refreshStatus() }
                }
            }
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
            refreshStatus()
        }

        findViewById<Button>(R.id.btn_collapse_overlay).setOnClickListener {
            editMode = false
            editButton.text = "Edit Layout: OFF"
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_COLLAPSE_OVERLAY
                }
            )
        }

        editButton.setOnClickListener {
            editMode = !editMode
            editButton.text = if (editMode) "Edit Layout: ON" else "Edit Layout: OFF"
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SET_EDIT_MODE
                putExtra(OverlayService.EXTRA_EDIT_MODE, editMode)
            }
            startService(intent)
        }

        settingsToggleButton.setOnClickListener {
            settingsOpen = !settingsOpen
            settingsToggleButton.text = if (settingsOpen) "Settings: ON" else "Settings: OFF"
            settingsPanel.visibility = if (settingsOpen) View.VISIBLE else View.GONE
            if (settingsOpen) {
                populateHideCheckboxes()
            }
        }

        findViewById<Button>(R.id.btn_show_all_controls).setOnClickListener {
            setAllHiddenState(false)
        }

        findViewById<Button>(R.id.btn_hide_all_controls).setOnClickListener {
            setAllHiddenState(true)
        }

        setupSliders()

        findViewById<Button>(R.id.btn_reset_layout).setOnClickListener {
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_RESET_LAYOUT
                }
            )
            if (settingsOpen) populateHideCheckboxes()
        }

        findViewById<Button>(R.id.btn_stop_overlay).setOnClickListener {
            editMode = false
            editButton.text = "Edit Layout: OFF"
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP_OVERLAY
                }
            )
            ShizukuController.unbindUserService(this)
            refreshStatus()
        }
    }

    private fun setAllHiddenState(hidden: Boolean) {
        val dm = resources.displayMetrics
        val controls = store.loadControls(dm.widthPixels, dm.heightPixels)
        for (c in controls) {
            c.hidden = hidden
        }
        store.saveControls(controls)
        notifyOverlaySettingsChanged()
        if (settingsOpen) populateHideCheckboxes()
    }

    private fun setupSliders() {
        val sens = store.getStickSensitivity()
        sensitivitySeek.progress = ((sens - 0.25f) * 100f).toInt().coerceIn(0, 225)
        sensitivityLabel.text = String.format(Locale.US, "Thumbstick Sensitivity: %.2fx", sens)

        sensitivitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.25f + (progress / 100f)
                sensitivityLabel.text = String.format(Locale.US, "Thumbstick Sensitivity: %.2fx", value)
                store.setStickSensitivity(value)
                notifyOverlaySettingsChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val dz = store.getStickDeadzone()
        deadzoneSeek.progress = (dz * 100f).toInt().coerceIn(0, 45)
        deadzoneLabel.text = "Thumbstick Deadzone: ${(dz * 100f).toInt()}%"

        deadzoneSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100f
                deadzoneLabel.text = "Thumbstick Deadzone: ${progress}%"
                store.setStickDeadzone(value)
                notifyOverlaySettingsChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun populateHideCheckboxes() {
        hideContainer.removeAllViews()
        val dm = resources.displayMetrics
        val controls = store.loadControls(dm.widthPixels, dm.heightPixels)
        for (c in controls) {
            val cb = CheckBox(this).apply {
                text = "Hide ${c.label}"
                isChecked = c.hidden
                setOnCheckedChangeListener { _, isChecked ->
                    val latest = store.loadControls(dm.widthPixels, dm.heightPixels)
                    latest.find { it.id == c.id }?.hidden = isChecked
                    store.saveControls(latest)
                    notifyOverlaySettingsChanged()
                }
            }
            hideContainer.addView(cb)
        }
    }

    private fun notifyOverlaySettingsChanged() {
        startService(
            Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_SETTINGS
            }
        )
    }

    override fun onResume() {
        super.onResume()
        if (ShizukuController.hasPermission() && ShizukuController.service == null) {
            ShizukuController.bindUserService(this) { _, _ ->
                runOnUiThread { refreshStatus() }
            }
        }
        refreshStatus()
        if (settingsOpen) populateHideCheckboxes()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun refreshStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val shizukuRunning = ShizukuController.isShizukuAvailable()
        val shizukuPerm = ShizukuController.hasPermission()
        val backend = ShizukuController.lastStatus
        statusText.text = "Overlay=$overlayOk | Shizuku=$shizukuRunning | Perm=$shizukuPerm\nBackend: $backend"
    }
}
