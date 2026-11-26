package com.overlaypad

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

object ShizukuController {
    var service: IGamepadService? = null
        private set

    var lastStatus: String = "Not connected"
        private set

    private var connection: ServiceConnection? = null

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(context.packageName, GamepadUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("gamepad")
            .debuggable(false)
            .version(1)
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            isShizukuAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        if (!isShizukuAvailable()) {
            lastStatus = "Shizuku service not running"
            return
        }
        if (Shizuku.isPreV11()) {
            lastStatus = "Shizuku v11+ required"
            return
        }
        Shizuku.requestPermission(requestCode)
    }

    fun bindUserService(context: Context, onReady: ((Boolean, String) -> Unit)? = null) {
        if (!hasPermission()) {
            lastStatus = "Shizuku permission missing"
            onReady?.invoke(false, lastStatus)
            return
        }
        if (service != null) {
            lastStatus = try {
                service?.status ?: "Connected"
            } catch (_: Throwable) {
                "Connected"
            }
            onReady?.invoke(true, lastStatus)
            return
        }

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    service = IGamepadService.Stub.asInterface(binder)
                    lastStatus = try {
                        service?.status ?: "Gamepad service bound"
                    } catch (t: Throwable) {
                        "Bound (${t.message})"
                    }
                    onReady?.invoke(true, lastStatus)
                } else {
                    lastStatus = "Invalid binder"
                    onReady?.invoke(false, lastStatus)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                lastStatus = "UserService disconnected"
            }
        }
        connection = conn
        try {
            Shizuku.bindUserService(userServiceArgs(context), conn)
        } catch (t: Throwable) {
            lastStatus = "Bind failed: ${t.message}"
            onReady?.invoke(false, lastStatus)
        }
    }

    fun unbindUserService(context: Context) {
        try {
            service?.resetAll()
        } catch (_: Throwable) {
        }
        connection?.let {
            try {
                Shizuku.unbindUserService(userServiceArgs(context), it, true)
            } catch (_: Throwable) {
            }
        }
        connection = null
        service = null
        lastStatus = "Disconnected"
    }
}
