package com.overlaypad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OverlayPadView(
    context: Context,
    private val store: OverlayLayoutStore,
    private val onButtonEvent: (linuxKeyCode: Int, pressed: Boolean) -> Unit,
    private val onAxisEvent: (linuxAbsCode: Int, value: Int) -> Unit,
    private val onCollapseRequest: (() -> Unit)? = null
) : View(context) {

    var editMode: Boolean = false
        set(value) {
            field = value
            activePointerToControl.clear()
            invalidate()
        }

    var stickSensitivity: Float = store.getStickSensitivity()
        set(value) {
            field = value.coerceIn(0.25f, 2.5f)
            store.setStickSensitivity(field)
        }

    var stickDeadzone: Float = store.getStickDeadzone()
        set(value) {
            field = value.coerceIn(0.0f, 0.45f)
            store.setStickDeadzone(field)
        }

    private var controls: MutableList<PadControl> = mutableListOf()
    private val activePointerToControl = HashMap<Int, PadControl>()
    private val stickThumbOffset = HashMap<String, Pair<Float, Float>>()

    private var pillCenterX = 140f
    private var pillCenterY = 220f
    private val pillRadius = 64f
    private var draggingPill = false
    private var pillDownX = 0f
    private var pillDownY = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.argb(190, 255, 255, 255)
    }
    private val hiddenRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(90, 255, 215, 0)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(90, 40, 140, 255)
    }
    private val activeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(165, 80, 220, 140)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 30, 35, 45)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (controls.isEmpty()) {
                controls = store.loadControls(w, h)
            }
            pillCenterX = (w - 110f).coerceAtLeast(pillRadius + 16f)
            pillCenterY = 180f.coerceAtLeast(pillRadius + 16f)
        }
    }

    fun reloadControls() {
        if (width > 0 && height > 0) {
            releaseAllInputs()
            controls = store.loadControls(width, height)
            invalidate()
        }
    }

    fun resetLayout() {
        if (width > 0 && height > 0) {
            releaseAllInputs()
            controls = store.defaultControls(width, height)
            store.saveControls(controls)
            invalidate()
        }
    }

    fun releaseAllInputs() {
        for (c in activePointerToControl.values) {
            if (c.isStick) {
                stickThumbOffset.remove(c.id)
                onAxisEvent(c.linuxCodePrimary, 0)
                onAxisEvent(c.linuxCodeSecondary, 0)
            } else {
                onButtonEvent(c.linuxCodePrimary, false)
            }
        }
        activePointerToControl.clear()
        invalidate()
    }

    private fun centerX(c: PadControl): Float = c.normX * width
    private fun centerY(c: PadControl): Float = c.normY * height

    private fun isPillHit(x: Float, y: Float): Boolean {
        return hypot(x - pillCenterX, y - pillCenterY) <= pillRadius * 1.2f
    }

    private fun hitTest(x: Float, y: Float): PadControl? {
        for (c in controls.asReversed()) {
            if (c.hidden && !editMode) continue
            val dist = hypot(x - centerX(c), y - centerY(c))
            if (dist <= c.radiusPx * 1.15f) return c
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0 || height <= 0) return false

        val actionMasked = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        if (editMode) {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    if (isPillHit(x, y)) {
                        draggingPill = true
                        pillDownX = x
                        pillDownY = y
                        return true
                    }
                    val c = hitTest(x, y)
                    if (c != null) {
                        activePointerToControl[pointerId] = c
                        invalidate()
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    var handled = false
                    if (draggingPill) {
                        pillCenterX = event.getX(0).coerceIn(pillRadius + 12f, width - pillRadius - 12f)
                        pillCenterY = event.getY(0).coerceIn(pillRadius + 12f, height - pillRadius - 12f)
                        handled = true
                    }
                    for (i in 0 until event.pointerCount) {
                        val pid = event.getPointerId(i)
                        val c = activePointerToControl[pid] ?: continue
                        c.normX = (event.getX(i) / width).coerceIn(0.05f, 0.95f)
                        c.normY = (event.getY(i) / height).coerceIn(0.08f, 0.92f)
                        handled = true
                    }
                    if (handled) invalidate()
                    return handled
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (draggingPill && pointerId == 0) {
                        draggingPill = false
                    }
                    activePointerToControl.remove(pointerId)
                    store.saveControls(controls)
                    invalidate()
                    return true
                }
            }
            return false
        }

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                if (isPillHit(x, y)) {
                    draggingPill = true
                    pillDownX = x
                    pillDownY = y
                    return true
                }
                val c = hitTest(x, y) ?: return false
                activePointerToControl[pointerId] = c
                if (c.isStick) {
                    updateStick(c, x, y)
                } else {
                    onButtonEvent(c.linuxCodePrimary, true)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingPill) {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    pillCenterX = x.coerceIn(pillRadius + 12f, width - pillRadius - 12f)
                    pillCenterY = y.coerceIn(pillRadius + 12f, height - pillRadius - 12f)
                    invalidate()
                    return true
                }
                var handled = false
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val c = activePointerToControl[pid] ?: continue
                    if (c.isStick) {
                        updateStick(c, event.getX(i), event.getY(i))
                        handled = true
                    }
                }
                if (handled) invalidate()
                return handled
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingPill && pointerId == 0) {
                    val moved = hypot(event.getX(pointerIndex) - pillDownX, event.getY(pointerIndex) - pillDownY)
                    draggingPill = false
                    if (moved < 24f && actionMasked != MotionEvent.ACTION_CANCEL) {
                        releaseAllInputs()
                        onCollapseRequest?.invoke()
                    }
                    return true
                }
                val c = activePointerToControl.remove(pointerId) ?: return false
                if (c.isStick) {
                    stickThumbOffset.remove(c.id)
                    onAxisEvent(c.linuxCodePrimary, 0)
                    onAxisEvent(c.linuxCodeSecondary, 0)
                } else {
                    onButtonEvent(c.linuxCodePrimary, false)
                }
                invalidate()
                return true
            }
        }
        return false
    }

    private fun applyDeadzoneAndSensitivity(rawNorm: Float): Float {
        val mag = kotlin.math.abs(rawNorm)
        if (mag <= stickDeadzone) return 0f
        val sign = if (rawNorm >= 0f) 1f else -1f
        val rescaled = ((mag - stickDeadzone) / max(0.001f, 1f - stickDeadzone)).coerceIn(0f, 1f)
        return (sign * rescaled * stickSensitivity).coerceIn(-1f, 1f)
    }

    private fun updateStick(c: PadControl, touchX: Float, touchY: Float) {
        val cx = centerX(c)
        val cy = centerY(c)
        val maxR = max(1f, c.radiusPx * 0.75f)
        var dx = touchX - cx
        var dy = touchY - cy
        val dist = hypot(dx, dy)
        if (dist > maxR) {
            dx = dx / dist * maxR
            dy = dy / dist * maxR
        }
        stickThumbOffset[c.id] = dx to dy

        val outX = applyDeadzoneAndSensitivity((dx / maxR).coerceIn(-1f, 1f))
        val outY = applyDeadzoneAndSensitivity((dy / maxR).coerceIn(-1f, 1f))
        val axisX = (outX * 32767f).toInt().coerceIn(-32768, 32767)
        val axisY = (outY * 32767f).toInt().coerceIn(-32768, 32767)
        onAxisEvent(c.linuxCodePrimary, axisX)
        onAxisEvent(c.linuxCodeSecondary, axisY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (c in controls) {
            if (c.hidden && !editMode) continue
            val cx = centerX(c)
            val cy = centerY(c)
            val isPressed = activePointerToControl.values.any { it.id == c.id }

            if (c.hidden && editMode) {
                canvas.drawCircle(cx, cy, c.radiusPx, hiddenRingPaint)
                textPaint.textSize = min(34f, max(20f, c.radiusPx * 0.36f))
                canvas.drawText("${c.label} (H)", cx, cy + textPaint.textSize * 0.35f, textPaint)
                continue
            }

            canvas.drawCircle(cx, cy, c.radiusPx, if (isPressed) activeFillPaint else fillPaint)
            ringPaint.color = if (editMode) Color.YELLOW else Color.argb(190, 255, 255, 255)
            canvas.drawCircle(cx, cy, c.radiusPx, ringPaint)

            if (c.isStick) {
                val offset = stickThumbOffset[c.id]
                val tx = cx + (offset?.first ?: 0f)
                val ty = cy + (offset?.second ?: 0f)
                canvas.drawCircle(tx, ty, c.radiusPx * 0.36f, ringPaint)
            }

            textPaint.textSize = min(40f, max(22f, c.radiusPx * 0.42f))
            canvas.drawText(c.label, cx, cy + textPaint.textSize * 0.35f, textPaint)
        }

        // Collapse / minimize pill handle
        canvas.drawCircle(pillCenterX, pillCenterY, pillRadius, pillPaint)
        ringPaint.color = Color.argb(220, 120, 200, 255)
        canvas.drawCircle(pillCenterX, pillCenterY, pillRadius, ringPaint)
        textPaint.textSize = 26f
        canvas.drawText("HIDE", pillCenterX, pillCenterY + 9f, textPaint)
    }
}
