package com.overlaypad

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.overlaypad.service.IGamepadService
import rikka.shizuku.Shizuku

object ShizukuController {

    const val REQUEST_CODE_SHIZUKU = 1001
    private const val TAG = "ShizukuController"

    @Volatile
    var service: IGamepadService? = null
        private set

    private var connection: ServiceConnection? = null
    private var boundCallback: ((Boolean) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.isPreV11()) return true
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission() {
        if (isShizukuAvailable() && !Shizuku.isPreV11()) {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
        }
    }

    fun bindUserService(context: Context, onResult: (Boolean) -> Unit) {
        val currentService = service
        if (currentService != null) {
            val ok = try {
                currentService.initGamepad("Xbox Wireless Controller", 0x045e, 0x02e0)
            } catch (_: Throwable) {
                false
            }
            onResult(ok)
            return
        }
        boundCallback = onResult

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val iface = IGamepadService.Stub.asInterface(binder)
                service = iface
                mainHandler.postDelayed({
                    val ok = try {
                        iface?.initGamepad("Xbox Wireless Controller", 0x045e, 0x02e0) ?: false
                    } catch (t: Throwable) {
                        Log.e(TAG, "initGamepad failed", t)
                        false
                    }
                    boundCallback?.invoke(ok)
                    boundCallback = null
                }, 150L)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        connection = conn

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, GamepadUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("uinput_service")
            .tag("overlaypad_uinput")
            .version(1)

        try {
            Shizuku.bindUserService(args, conn)
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed", t)
            onResult(false)
        }
    }

    fun unbindUserService(context: Context) {
        val conn = connection ?: return
        try {
            service?.closeGamepad()
        } catch (_: Throwable) {
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, GamepadUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("uinput_service")
            .tag("overlaypad_uinput")
            .version(1)
        try {
            Shizuku.unbindUserService(args, conn, true)
        } catch (_: Throwable) {
        }
        service = null
        connection = null
    }
}
