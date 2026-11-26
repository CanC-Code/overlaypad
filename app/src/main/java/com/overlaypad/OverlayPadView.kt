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
    private val onAxisEvent: (linuxAbsCode: Int, value: Int) -> Unit
) : View(context) {

    var editMode: Boolean = false
        set(value) {
            field = value
            activePointerToControl.clear()
            invalidate()
        }

    private var controls: MutableList<PadControl> = mutableListOf()
    private val activePointerToControl = HashMap<Int, PadControl>()
    private val stickThumbOffset = HashMap<String, Pair<Float, Float>>()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.argb(190, 255, 255, 255)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(90, 40, 140, 255)
    }
    private val activeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(165, 80, 220, 140)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && controls.isEmpty()) {
            controls = store.loadControls(w, h)
        }
    }

    fun resetLayout() {
        if (width > 0 && height > 0) {
            controls = store.defaultControls(width, height)
            store.saveControls(controls)
            invalidate()
        }
    }

    private fun centerX(c: PadControl): Float = c.normX * width
    private fun centerY(c: PadControl): Float = c.normY * height

    private fun hitTest(x: Float, y: Float): PadControl? {
        for (c in controls.asReversed()) {
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
                    val c = hitTest(event.getX(pointerIndex), event.getY(pointerIndex))
                    if (c != null) {
                        activePointerToControl[pointerId] = c
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) {
                        val pid = event.getPointerId(i)
                        val c = activePointerToControl[pid] ?: continue
                        c.normX = (event.getX(i) / width).coerceIn(0.05f, 0.95f)
                        c.normY = (event.getY(i) / height).coerceIn(0.08f, 0.92f)
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
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
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val c = activePointerToControl[pid] ?: continue
                    if (c.isStick) {
                        updateStick(c, event.getX(i), event.getY(i))
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val c = activePointerToControl.remove(pointerId)
                if (c != null) {
                    if (c.isStick) {
                        stickThumbOffset.remove(c.id)
                        onAxisEvent(c.linuxCodePrimary, 0)
                        onAxisEvent(c.linuxCodeSecondary, 0)
                    } else {
                        onButtonEvent(c.linuxCodePrimary, false)
                    }
                    invalidate()
                }
                return true
            }
        }
        return false
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

        val normX = (dx / maxR).coerceIn(-1f, 1f)
        val normY = (dy / maxR).coerceIn(-1f, 1f)
        val axisX = (normX * 32767f).toInt().coerceIn(-32768, 32767)
        val axisY = (normY * 32767f).toInt().coerceIn(-32768, 32767)
        onAxisEvent(c.linuxCodePrimary, axisX)
        onAxisEvent(c.linuxCodeSecondary, axisY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (c in controls) {
            val cx = centerX(c)
            val cy = centerY(c)
            val isPressed = activePointerToControl.values.any { it.id == c.id }

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
    }
}
