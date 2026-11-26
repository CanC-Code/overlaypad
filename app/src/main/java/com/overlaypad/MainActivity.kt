package com.overlaypad

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var pillOverlay: TextView
    private lateinit var pillShizuku: TextView
    private lateinit var pillPerm: TextView
    private lateinit var statusText: TextView
    private lateinit var editButton: Button
    private lateinit var settingsToggleButton: Button
    private lateinit var settingsPanel: LinearLayout
    private lateinit var opacityLabel: TextView
    private lateinit var opacitySeek: SeekBar
    private lateinit var sensitivityLabel: TextView
    private lateinit var sensitivitySeek: SeekBar
    private lateinit var deadzoneLabel: TextView
    private lateinit var deadzoneSeek: SeekBar
    private lateinit var hideContainer: LinearLayout
    private lateinit var autoLoadCheck: CheckBox
    private lateinit var profileGameInfo: TextView
    private lateinit var profileSpinner: Spinner
    private lateinit var store: OverlayLayoutStore

    private var editMode = false
    private var settingsOpen = false
    private var profileNames: List<String> = emptyList()

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
        pillOverlay = findViewById(R.id.pill_overlay)
        pillShizuku = findViewById(R.id.pill_shizuku)
        pillPerm = findViewById(R.id.pill_perm)
        statusText = findViewById(R.id.status_text)
        editButton = findViewById(R.id.btn_toggle_edit)
        settingsToggleButton = findViewById(R.id.btn_toggle_settings)
        settingsPanel = findViewById(R.id.settings_panel)
        opacityLabel = findViewById(R.id.tv_opacity)
        opacitySeek = findViewById(R.id.seek_opacity)
        sensitivityLabel = findViewById(R.id.tv_sensitivity)
        sensitivitySeek = findViewById(R.id.seek_sensitivity)
        deadzoneLabel = findViewById(R.id.tv_deadzone)
        deadzoneSeek = findViewById(R.id.seek_deadzone)
        hideContainer = findViewById(R.id.hide_controls_container)
        autoLoadCheck = findViewById(R.id.chk_autoload_per_game)
        profileGameInfo = findViewById(R.id.tv_profile_game_info)
        profileSpinner = findViewById(R.id.spinner_profiles)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        findViewById<Button>(R.id.btn_open_shizuku).setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Shizuku app not installed", Toast.LENGTH_SHORT).show()
            }
        }

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
            } else {
                Toast.makeText(this, "Grant Overlay Permission first", Toast.LENGTH_SHORT).show()
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

        findViewById<Button>(R.id.btn_release_all).setOnClickListener {
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_RELEASE_ALL
                }
            )
            Toast.makeText(this, "Released all gamepad inputs", Toast.LENGTH_SHORT).show()
        }

        setupProfileControls()

        settingsToggleButton.setOnClickListener {
            settingsOpen = !settingsOpen
            settingsToggleButton.text = if (settingsOpen) "Collapse" else "Expand"
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

    private fun selectedProfileName(): String? {
        if (profileNames.isEmpty()) return null
        val idx = profileSpinner.selectedItemPosition
        return if (idx in profileNames.indices) profileNames[idx] else null
    }

    private fun setupProfileControls() {
        autoLoadCheck.isChecked = store.isAutoLoadPerGameEnabled()
        autoLoadCheck.setOnCheckedChangeListener { _, checked ->
            store.setAutoLoadPerGameEnabled(checked)
            notifyOverlaySettingsChanged()
        }

        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateProfileStatusText()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.btn_save_profile).setOnClickListener {
            val input = EditText(this).apply {
                hint = "e.g. Banjo-Kazooie or Retro"
                setText(store.getActiveProfileName())
            }
            AlertDialog.Builder(this)
                .setTitle("Save Layout Profile")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString().trim()
                    if (store.saveNamedProfile(name)) {
                        refreshProfilesSpinner(selectName = name)
                        Toast.makeText(this, "Saved profile '$name'", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<Button>(R.id.btn_load_profile).setOnClickListener {
            val name = selectedProfileName()
            if (name == null) {
                Toast.makeText(this, "No saved profile selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (store.loadNamedProfile(name)) {
                syncSlidersFromStore()
                notifyOverlaySettingsChanged()
                if (settingsOpen) populateHideCheckboxes()
                updateProfileStatusText()
                Toast.makeText(this, "Loaded '$name'", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_link_game_profile).setOnClickListener {
            val name = selectedProfileName()
            if (name == null) {
                Toast.makeText(this, "Save or select a profile first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val currentLinked = store.getLinkedPackageForProfile(name)
            val lastDetected = store.getLastDetectedGamePackage()
            val input = EditText(this).apply {
                hint = "com.example.game"
                setText(if (currentLinked.isNotEmpty()) currentLinked else lastDetected)
            }
            AlertDialog.Builder(this)
                .setTitle("Link Game Package to '$name'")
                .setMessage("When Auto-Load is checked, switching to this package loads '$name'.")
                .setView(input)
                .setPositiveButton("Link") { _, _ ->
                    val pkg = input.text.toString().trim()
                    store.linkPackageToProfile(name, pkg)
                    updateProfileStatusText()
                    notifyOverlaySettingsChanged()
                    Toast.makeText(
                        this,
                        if (pkg.isEmpty()) "Unlinked game from '$name'" else "Linked $pkg -> '$name'",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<Button>(R.id.btn_delete_profile).setOnClickListener {
            val name = selectedProfileName() ?: return@setOnClickListener
            store.deleteNamedProfile(name)
            refreshProfilesSpinner()
            Toast.makeText(this, "Deleted '$name'", Toast.LENGTH_SHORT).show()
        }

        refreshProfilesSpinner(selectName = store.getActiveProfileName())
    }

    private fun refreshProfilesSpinner(selectName: String? = null) {
        profileNames = store.listProfileNames()
        val displayList = if (profileNames.isEmpty()) {
            listOf("(No saved profiles yet)")
        } else {
            profileNames.map { name ->
                val linked = store.getLinkedPackageForProfile(name)
                if (linked.isNotEmpty()) "$name  [$linked]" else name
            }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayList)
        profileSpinner.adapter = adapter
        if (!selectName.isNullOrEmpty() && profileNames.contains(selectName)) {
            profileSpinner.setSelection(profileNames.indexOf(selectName))
        }
        updateProfileStatusText()
    }

    private fun updateProfileStatusText() {
        val active = store.getActiveProfileName().ifEmpty { "None" }
        val lastGame = store.getLastDetectedGamePackage().ifEmpty { "None" }
        profileGameInfo.text = "Active: $active | Last Game: $lastGame"
    }

    private fun setAllHiddenState(hidden: Boolean) {
        val dm = resources.displayMetrics
        val controls = store.loadControls(dm.widthPixels, dm.heightPixels)
        for (c in controls) {
            c.hidden = hidden
        }
        store.saveControls(controls, dm.widthPixels, dm.heightPixels)
        notifyOverlaySettingsChanged()
        if (settingsOpen) populateHideCheckboxes()
    }

    private fun syncSlidersFromStore() {
        val op = store.getOverlayOpacity()
        opacitySeek.progress = ((op - 0.25f) * 100f).toInt().coerceIn(0, 75)
        opacityLabel.text = "Overlay Opacity: ${(op * 100f).toInt()}%"

        val sens = store.getStickSensitivity()
        sensitivitySeek.progress = ((sens - 0.25f) * 100f).toInt().coerceIn(0, 225)
        sensitivityLabel.text = String.format(Locale.US, "Thumbstick Sensitivity: %.2fx", sens)

        val dz = store.getStickDeadzone()
        deadzoneSeek.progress = (dz * 100f).toInt().coerceIn(0, 45)
        deadzoneLabel.text = "Thumbstick Deadzone: ${(dz * 100f).toInt()}%"
    }

    private fun setupSliders() {
        syncSlidersFromStore()

        opacitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.25f + (progress / 100f)
                opacityLabel.text = "Overlay Opacity: ${(value * 100f).toInt()}%"
                store.setOverlayOpacity(value)
                notifyOverlaySettingsChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

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
                text = "Hide ${c.displayLabel()}"
                setTextColor(Color.parseColor("#E2E8F0"))
                isChecked = c.hidden
                setOnCheckedChangeListener { _, isChecked ->
                    val latest = store.loadControls(dm.widthPixels, dm.heightPixels)
                    latest.find { it.id == c.id }?.hidden = isChecked
                    store.saveControls(latest, dm.widthPixels, dm.heightPixels)
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
        refreshProfilesSpinner(selectName = store.getActiveProfileName())
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

        pillOverlay.text = if (overlayOk) "Overlay: ON" else "Overlay: OFF"
        pillOverlay.setBackgroundResource(if (overlayOk) R.drawable.bg_pill_ok else R.drawable.bg_pill_warn)

        pillShizuku.text = if (shizukuRunning) "Shizuku: ON" else "Shizuku: OFF"
        pillShizuku.setBackgroundResource(if (shizukuRunning) R.drawable.bg_pill_ok else R.drawable.bg_pill_warn)

        pillPerm.text = if (shizukuPerm) "Perm: YES" else "Perm: NO"
        pillPerm.setBackgroundResource(if (shizukuPerm) R.drawable.bg_pill_ok else R.drawable.bg_pill_warn)

        val hint = when {
            !shizukuRunning -> "Start Shizuku via ADB / Wireless Debugging, then tap Init Gamepad."
            !shizukuPerm -> "Tap 2. Init Gamepad to grant Shizuku permission."
            else -> "Backend: $backend"
        }
        statusText.text = hint
    }
}
