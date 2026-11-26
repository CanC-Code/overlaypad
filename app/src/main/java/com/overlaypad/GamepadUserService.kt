package com.overlaypad

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class GamepadUserService : IGamepadService.Stub() {

    private var injectTarget: Any? = null
    private var injectMethod: Method? = null
    private var statusMessage = "Initializing InputManager..."

    private var axisLx = 0f
    private var axisLy = 0f
    private var axisRx = 0f
    private var axisRy = 0f
    private var axisLt = 0f
    private var axisRt = 0f
    private var axisHatX = 0f
    private var axisHatY = 0f

    private var metaState = 0
    private val heldKeys = HashSet<Int>()

    private val motionExecutor = Executors.newSingleThreadScheduledExecutor()
    private var motionTicker: ScheduledFuture<*>? = null

    init {
        initInputManager()
        startMotionTicker()
    }

    private fun initInputManager() {
        try {
            val imgClass = Class.forName("android.hardware.input.InputManagerGlobal")
            val getInstance = imgClass.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val method = imgClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            injectTarget = instance
            injectMethod = method
            statusMessage = "Gamepad backend active"
        } catch (t: Throwable) {
            try {
                val imClass = Class.forName("android.hardware.input.InputManager")
                val getInstance = imClass.getMethod("getInstance")
                val instance = getInstance.invoke(null)
                val method = imClass.getMethod(
                    "injectInputEvent",
                    InputEvent::class.java,
                    Int::class.javaPrimitiveType
                )
                injectTarget = instance
                injectMethod = method
                statusMessage = "Gamepad backend active"
            } catch (t2: Throwable) {
                statusMessage = "InputManager reflection failed: ${t2.message}"
            }
        }
    }

    private fun hasActiveAxes(): Boolean {
        return abs(axisLx) > 0.001f ||
            abs(axisLy) > 0.001f ||
            abs(axisRx) > 0.001f ||
            abs(axisRy) > 0.001f ||
            axisLt > 0.001f ||
            axisRt > 0.001f ||
            abs(axisHatX) > 0.001f ||
            abs(axisHatY) > 0.001f
    }

    private fun startMotionTicker() {
        motionTicker = motionExecutor.scheduleAtFixedRate({
            try {
                if (hasActiveAxes()) {
                    emitMotionSnapshot()
                }
            } catch (_: Throwable) {
            }
        }, 16L, 16L, TimeUnit.MILLISECONDS)
    }

    private fun linuxToAndroidKeyCode(linuxCode: Int): Int {
        return when (linuxCode) {
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
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }

    private fun inject(event: InputEvent) {
        val target = injectTarget ?: return
        val method = injectMethod ?: return
        try {
            method.invoke(target, event, 0)
        } catch (t: Throwable) {
            statusMessage = "Inject error: ${t.cause?.message ?: t.message}"
        }
    }

    override fun setButton(linuxKeyCode: Int, pressed: Boolean) {
        val keyCode = linuxToAndroidKeyCode(linuxKeyCode)
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return
        val now = SystemClock.uptimeMillis()
        val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        if (pressed) {
            heldKeys.add(keyCode)
        } else {
            heldKeys.remove(keyCode)
        }
        val source = if (keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) {
            InputDevice.SOURCE_DPAD or InputDevice.SOURCE_GAMEPAD
        } else {
            InputDevice.SOURCE_GAMEPAD
        }
        val event = KeyEvent(
            now,
            now,
            action,
            keyCode,
            0,
            metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            linuxKeyCode,
            KeyEvent.FLAG_FROM_SYSTEM,
            source
        )
        inject(event)
    }

    override fun setAbsAxis(linuxAbsCode: Int, value: Int) {
        when (linuxAbsCode) {
            0x00 -> axisLx = (value / 32767f).coerceIn(-1f, 1f)
            0x01 -> axisLy = (value / 32767f).coerceIn(-1f, 1f)
            0x03 -> axisRx = (value / 32767f).coerceIn(-1f, 1f)
            0x04 -> axisRy = (value / 32767f).coerceIn(-1f, 1f)
            0x02 -> axisLt = (value / 255f).coerceIn(0f, 1f)
            0x05 -> axisRt = (value / 255f).coerceIn(0f, 1f)
            0x10 -> axisHatX = value.coerceIn(-1, 1).toFloat()
            0x11 -> axisHatY = value.coerceIn(-1, 1).toFloat()
        }
        emitMotionSnapshot()
    }

    private fun emitMotionSnapshot() {
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_UNKNOWN
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = axisLx
                y = axisLy
                setAxisValue(MotionEvent.AXIS_X, axisLx)
                setAxisValue(MotionEvent.AXIS_Y, axisLy)
                setAxisValue(MotionEvent.AXIS_Z, axisRx)
                setAxisValue(MotionEvent.AXIS_RZ, axisRy)
                setAxisValue(MotionEvent.AXIS_RX, axisRx)
                setAxisValue(MotionEvent.AXIS_RY, axisRy)
                setAxisValue(MotionEvent.AXIS_LTRIGGER, axisLt)
                setAxisValue(MotionEvent.AXIS_RTRIGGER, axisRt)
                setAxisValue(MotionEvent.AXIS_BRAKE, axisLt)
                setAxisValue(MotionEvent.AXIS_GAS, axisRt)
                setAxisValue(MotionEvent.AXIS_HAT_X, axisHatX)
                setAxisValue(MotionEvent.AXIS_HAT_Y, axisHatY)
            }
        )
        val motion = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_MOVE,
            1,
            props,
            coords,
            metaState,
            0,
            1.0f,
            1.0f,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_GAMEPAD,
            0
        )
        inject(motion)
        motion.recycle()
    }

    override fun resetAll() {
        axisLx = 0f
        axisLy = 0f
        axisRx = 0f
        axisRy = 0f
        axisLt = 0f
        axisRt = 0f
        axisHatX = 0f
        axisHatY = 0f
        val now = SystemClock.uptimeMillis()
        for (keyCode in heldKeys.toList()) {
            val event = KeyEvent(
                now,
                now,
                KeyEvent.ACTION_UP,
                keyCode,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_GAMEPAD
            )
            inject(event)
        }
        heldKeys.clear()
        emitMotionSnapshot()
    }

    override fun getStatus(): String = statusMessage

    override fun getForegroundPackage(): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -n 1"))
            val line = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readLine() } ?: ""
            proc.waitFor()
            val match = Regex("([a-zA-Z0-9_]+(?:\\.[a-zA-Z0-9_]+)+)/").find(line)
            match?.groupValues?.getOrNull(1) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    fun destroy() {
        motionTicker?.cancel(true)
        motionExecutor.shutdownNow()
        resetAll()
        System.exit(0)
    }
}
