package com.overlaypad

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import com.overlaypad.service.IGamepadService
import java.lang.reflect.Method
import kotlin.system.exitProcess

class GamepadUserService() : IGamepadService.Stub() {

    constructor(context: Context) : this()

    private var uinputActive = false
    private var injectMethod: Method? = null
    private var inputManagerInstance: Any? = null

    private var axisX = 0f
    private var axisY = 0f
    private var axisZ = 0f
    private var axisRz = 0f
    private var axisLTrigger = 0f
    private var axisRTrigger = 0f
    private var axisHatX = 0f
    private var axisHatY = 0f

    init {
        try {
            System.loadLibrary("uinputpad")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load libuinputpad.so", t)
        }
        initInputInjector()
    }

    private fun initInputInjector() {
        try {
            val imGlobalClass = Class.forName("android.hardware.input.InputManagerGlobal")
            val getInstance = imGlobalClass.getMethod("getInstance")
            inputManagerInstance = getInstance.invoke(null)
            injectMethod = imGlobalClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            Log.i(TAG, "Initialized InputManagerGlobal injector")
        } catch (t: Throwable) {
            try {
                val getInstance = InputManager::class.java.getMethod("getInstance")
                inputManagerInstance = getInstance.invoke(null)
                injectMethod = InputManager::class.java.getMethod(
                    "injectInputEvent",
                    InputEvent::class.java,
                    Int::class.javaPrimitiveType
                )
                Log.i(TAG, "Initialized InputManager injector")
            } catch (t2: Throwable) {
                Log.e(TAG, "Failed to initialize InputManager injector", t2)
            }
        }
    }

    override fun initGamepad(deviceName: String, vendorId: Int, productId: Int): Boolean {
        uinputActive = try {
            nativeInitGamepad(deviceName, vendorId, productId)
        } catch (_: Throwable) {
            false
        }
        if (uinputActive) {
            Log.i(TAG, "Using /dev/uinput backend")
            return true
        }
        if (injectMethod != null && inputManagerInstance != null) {
            Log.i(TAG, "/dev/uinput unavailable; using InputManager injection fallback")
            return true
        }
        return false
    }

    override fun setButton(linuxKeyCode: Int, pressed: Boolean) {
        if (uinputActive) {
            nativeSetButton(linuxKeyCode, pressed)
            return
        }
        val androidKeyCode = mapLinuxToAndroidKeyCode(linuxKeyCode) ?: return
        val now = SystemClock.uptimeMillis()
        val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val event = KeyEvent(
            now,
            now,
            action,
            androidKeyCode,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            linuxKeyCode,
            0,
            InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        )
        inject(event)
    }

    override fun setAbsAxis(linuxAbsCode: Int, value: Int) {
        if (uinputActive) {
            nativeSetAbsAxis(linuxAbsCode, value)
            return
        }
        when (linuxAbsCode) {
            0x00 -> axisX = (value / 32767f).coerceIn(-1f, 1f)
            0x01 -> axisY = (value / 32767f).coerceIn(-1f, 1f)
            0x03 -> axisZ = (value / 32767f).coerceIn(-1f, 1f)
            0x04 -> axisRz = (value / 32767f).coerceIn(-1f, 1f)
            0x02 -> axisLTrigger = (value / 255f).coerceIn(0f, 1f)
            0x05 -> axisRTrigger = (value / 255f).coerceIn(0f, 1f)
            0x10 -> axisHatX = value.coerceIn(-1, 1).toFloat()
            0x11 -> axisHatY = value.coerceIn(-1, 1).toFloat()
            else -> return
        }
        injectJoystickMotion()
    }

    private fun injectJoystickMotion() {
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_UNKNOWN
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = axisX
                y = axisY
                setAxisValue(MotionEvent.AXIS_X, axisX)
                setAxisValue(MotionEvent.AXIS_Y, axisY)
                setAxisValue(MotionEvent.AXIS_Z, axisZ)
                setAxisValue(MotionEvent.AXIS_RZ, axisRz)
                setAxisValue(MotionEvent.AXIS_LTRIGGER, axisLTrigger)
                setAxisValue(MotionEvent.AXIS_RTRIGGER, axisRTrigger)
                setAxisValue(MotionEvent.AXIS_BRAKE, axisLTrigger)
                setAxisValue(MotionEvent.AXIS_GAS, axisRTrigger)
                setAxisValue(MotionEvent.AXIS_HAT_X, axisHatX)
                setAxisValue(MotionEvent.AXIS_HAT_Y, axisHatY)
            }
        )
        val event = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_MOVE,
            1,
            props,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_JOYSTICK,
            0
        )
        try {
            inject(event)
        } finally {
            event.recycle()
        }
    }

    private fun inject(event: InputEvent) {
        try {
            injectMethod?.invoke(inputManagerInstance, event, 0)
        } catch (t: Throwable) {
            Log.e(TAG, "injectInputEvent failed", t)
        }
    }

    private fun mapLinuxToAndroidKeyCode(code: Int): Int? = when (code) {
        304 -> KeyEvent.KEYCODE_BUTTON_A
        305 -> KeyEvent.KEYCODE_BUTTON_B
        307 -> KeyEvent.KEYCODE_BUTTON_X
        308 -> KeyEvent.KEYCODE_BUTTON_Y
        310 -> KeyEvent.KEYCODE_BUTTON_L1
        311 -> KeyEvent.KEYCODE_BUTTON_R1
        312 -> KeyEvent.KEYCODE_BUTTON_L2
        313 -> KeyEvent.KEYCODE_BUTTON_R2
        314 -> KeyEvent.KEYCODE_BUTTON_SELECT
        315 -> KeyEvent.KEYCODE_BUTTON_START
        316 -> KeyEvent.KEYCODE_BUTTON_MODE
        317 -> KeyEvent.KEYCODE_BUTTON_THUMBL
        318 -> KeyEvent.KEYCODE_BUTTON_THUMBR
        544 -> KeyEvent.KEYCODE_DPAD_UP
        545 -> KeyEvent.KEYCODE_DPAD_DOWN
        546 -> KeyEvent.KEYCODE_DPAD_LEFT
        547 -> KeyEvent.KEYCODE_DPAD_RIGHT
        else -> null
    }

    override fun closeGamepad() {
        uinputActive = false
        try {
            nativeCloseGamepad()
        } catch (_: Throwable) {
        }
    }

    override fun destroy() {
        closeGamepad()
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
