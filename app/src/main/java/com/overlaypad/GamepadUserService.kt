package com.overlaypad

import android.content.Context
import android.util.Log
import com.overlaypad.service.IGamepadService
import kotlin.system.exitProcess

class GamepadUserService() : IGamepadService.Stub() {

    constructor(context: Context) : this()

    init {
        try {
            System.loadLibrary("uinputpad")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load libuinputpad.so", t)
        }
    }

    override fun initGamepad(deviceName: String, vendorId: Int, productId: Int): Boolean {
        return nativeInitGamepad(deviceName, vendorId, productId)
    }

    override fun setButton(linuxKeyCode: Int, pressed: Boolean) {
        nativeSetButton(linuxKeyCode, pressed)
    }

    override fun setAbsAxis(linuxAbsCode: Int, value: Int) {
        nativeSetAbsAxis(linuxAbsCode, value)
    }

    override fun closeGamepad() {
        nativeCloseGamepad()
    }

    override fun destroy() {
        nativeCloseGamepad()
        exitProcess(0)
    }

    private external fun nativeInitGamepad(deviceName: String, vendorId: Int, productId: Int): Boolean
    private external fun nativeSetButton(linuxKeyCode: Int, pressed: Boolean)
    private external fun nativeSetAbsAxis(linuxAbsCode: Int, value: Int)
    private external fun nativeCloseGamepad()

    companion object {
        private const val TAG = "GamepadUserService"
    }
}
