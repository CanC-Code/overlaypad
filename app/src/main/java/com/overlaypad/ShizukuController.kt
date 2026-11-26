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

    @Volatile
    var lastStatus: String = "Not bound"
        private set

    private var connection: ServiceConnection? = null
    private var bindingInProgress = false
    private val callbacks = mutableListOf<(Boolean, String) -> Unit>()
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

    fun bindUserService(context: Context, onResult: ((Boolean, String) -> Unit)? = null) {
        if (onResult != null) {
            synchronized(callbacks) {
                callbacks.add(onResult)
            }
        }

        val currentService = service
        if (currentService != null) {
            val ok = try {
                currentService.initGamepad("Xbox Wireless Controller", 0x045e, 0x02e0)
            } catch (t: Throwable) {
                service = null
                false
            }
            if (ok) {
                lastStatus = "Gamepad backend active"
                notifyCallbacks(true, lastStatus)
                return
            }
        }

        if (bindingInProgress) return
        bindingInProgress = true
        lastStatus = "Binding Shizuku service..."

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                bindingInProgress = false
                val iface = IGamepadService.Stub.asInterface(binder)
                service = iface
                mainHandler.postDelayed({
                    var errDetail = ""
                    val ok = try {
                        iface?.initGamepad("Xbox Wireless Controller", 0x045e, 0x02e0) ?: false
                    } catch (t: Throwable) {
                        errDetail = t.javaClass.simpleName + ": " + (t.message ?: "")
                        Log.e(TAG, "initGamepad failed", t)
                        false
                    }
                    lastStatus = if (ok) {
                        "Gamepad backend active"
                    } else {
                        "Bound, init failed ($errDetail)"
                    }
                    notifyCallbacks(ok, lastStatus)
                }, 150L)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bindingInProgress = false
                service = null
                lastStatus = "Service disconnected"
            }
        }
        connection = conn

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.applicationContext.packageName, GamepadUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("uinput_service")
            .tag("overlaypad_uinput")
            .version(2)

        try {
            Shizuku.bindUserService(args, conn)
        } catch (t: Throwable) {
            bindingInProgress = false
            lastStatus = "bindUserService error: ${t.javaClass.simpleName}"
            Log.e(TAG, "bindUserService failed", t)
            notifyCallbacks(false, lastStatus)
        }
    }

    private fun notifyCallbacks(ok: Boolean, status: String) {
        val snapshot: List<(Boolean, String) -> Unit>
        synchronized(callbacks) {
            snapshot = callbacks.toList()
            callbacks.clear()
        }
        for (cb in snapshot) {
            cb(ok, status)
        }
    }

    fun unbindUserService(context: Context) {
        val conn = connection ?: return
        try {
            service?.closeGamepad()
        } catch (_: Throwable) {
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.applicationContext.packageName, GamepadUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("uinput_service")
            .tag("overlaypad_uinput")
            .version(2)
        try {
            Shizuku.unbindUserService(args, conn, true)
        } catch (_: Throwable) {
        }
        service = null
        connection = null
        bindingInProgress = false
        lastStatus = "Unbound"
    }
}
