package com.overlaypad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var editMode = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
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
                ShizukuController.bindUserService(this) { ok ->
                    runOnUiThread {
                        statusText.text = if (ok) {
                            "Status: uinput gamepad active"
                        } else {
                            "Status: Shizuku bound, /dev/uinput failed"
                        }
                    }
                }
            }
            refreshStatus()
        }

        findViewById<Button>(R.id.btn_start_overlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startForegroundService(Intent(this, OverlayService::class.java))
            }
        }

        findViewById<Button>(R.id.btn_toggle_edit).setOnClickListener {
            editMode = !editMode
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SET_EDIT_MODE
                putExtra(OverlayService.EXTRA_EDIT_MODE, editMode)
            }
            startService(intent)
        }

        findViewById<Button>(R.id.btn_reset_layout).setOnClickListener {
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_RESET_LAYOUT
                }
            )
        }

        findViewById<Button>(R.id.btn_stop_overlay).setOnClickListener {
            startService(
                Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP_OVERLAY
                }
            )
            ShizukuController.unbindUserService(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun refreshStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val shizukuRunning = ShizukuController.isShizukuAvailable()
        val shizukuPerm = ShizukuController.hasPermission()
        statusText.text = "OverlayPerm=$overlayOk | Shizuku=$shizukuRunning | Perm=$shizukuPerm"
    }
}
