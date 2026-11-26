package com.overlaypad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OverlayPadView(
    context: Context,
    private val store: OverlayLayoutStore,
    private val onButtonEvent: (linuxKeyCode: Int, pressed: Boolean) -> Unit,
    private val onAxisEvent: (linuxAbsCode: Int, value: Int) -> Unit,
    private val onEditModeToggleRequest: ((Boolean) -> Unit)? = null,
    private val onCollapseRequest: (() -> Unit)? = null
) : View(context) {

    var editMode: Boolean = false
        set(value) {
            if (field != value) {
                releaseAllInputs()
                field = value
                draggingEditPill = false
                draggingHidePill = false
                invalidate()
            }
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

    private var editPillRect = RectF()
    private var hidePillRect = RectF()
    private var pillNormX: Float = store.getPillNormX()
    private var pillNormY: Float = store.getPillNormY()
    private var pillTopX: Float = 0f
    private var pillTopY: Float = 0f
    private var pillInitialized = false
    private val pillW = 148f
    private val pillH = 62f
    private val pillGap = 16f

    private var draggingEditPill = false
    private var draggingHidePill = false
    private var pillDownX = 0f
    private var pillDownY = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.argb(195, 255, 255, 255)
    }
    private val hiddenRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(110, 255, 215, 0)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(92, 40, 140, 255)
    }
    private val activeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(175, 80, 220, 140)
    }
    private val editPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 45, 52, 68)
    }
    private val editActivePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 210, 155, 20)
    }
    private val hidePillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 30, 85, 165)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            releaseAllInputs()
            controls = store.loadControls(w, h)
            pillNormX = store.getPillNormX(w, h)
            pillNormY = store.getPillNormY(w, h)
            pillTopX = pillNormX * w
            pillTopY = pillNormY * h
            pillInitialized = true
            updatePillRects()
            invalidate()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            draggingEditPill = false
            draggingHidePill = false
            releaseAllInputs()
        }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            draggingEditPill = false
            draggingHidePill = false
            releaseAllInputs()
        }
    }

    private fun updatePillRects() {
        if (width <= 0 || height <= 0) return
        if (!pillInitialized) {
            pillTopX = store.getPillNormX(width, height) * width
            pillTopY = store.getPillNormY(width, height) * height
            pillInitialized = true
        }
        val maxX = max(12f, width - pillW - 12f)
        val maxY = max(12f, height - (pillH * 2f + pillGap + 12f))
        pillTopX = pillTopX.coerceIn(12f, maxX)
        pillTopY = pillTopY.coerceIn(12f, maxY)
        editPillRect.set(pillTopX, pillTopY, pillTopX + pillW, pillTopY + pillH)
        hidePillRect.set(
            pillTopX,
            pillTopY + pillH + pillGap,
            pillTopX + pillW,
            pillTopY + pillH * 2f + pillGap
        )
    }

    private fun persistPillPosition() {
        if (width <= 0 || height <= 0) return
        pillNormX = (pillTopX / width).coerceIn(0.05f, 0.92f)
        pillNormY = (pillTopY / height).coerceIn(0.08f, 0.82f)
        store.savePillPosition(pillNormX, pillNormY, width, height)
    }

    fun reloadControls() {
        if (width > 0 && height > 0) {
            releaseAllInputs()
            controls = store.loadControls(width, height)
            pillNormX = store.getPillNormX(width, height)
            pillNormY = store.getPillNormY(width, height)
            pillTopX = pillNormX * width
            pillTopY = pillNormY * height
            updatePillRects()
            invalidate()
        }
    }

    fun resetLayout() {
        if (width > 0 && height > 0) {
            releaseAllInputs()
            controls = store.defaultControls(width, height)
            store.saveControls(controls, width, height)
            pillNormX = 0.80f
            pillNormY = 0.14f
            store.savePillPosition(pillNormX, pillNormY, width, height)
            pillTopX = pillNormX * width
            pillTopY = pillNormY * height
            updatePillRects()
            invalidate()
        }
    }

    fun releaseAllInputs() {
        for (c in controls) {
            if (!c.isStick) {
                onButtonEvent(c.linuxCodePrimary, false)
                if (c.linuxCodeSecondary != 0) {
                    onAxisEvent(c.linuxCodeSecondary, 0)
                }
            }
        }
        onAxisEvent(0x00, 0)
        onAxisEvent(0x01, 0)
        onAxisEvent(0x03, 0)
        onAxisEvent(0x04, 0)
        onAxisEvent(0x10, 0)
        onAxisEvent(0x11, 0)
        activePointerToControl.clear()
        stickThumbOffset.clear()
        invalidate()
    }

    private fun dispatchControlPress(c: PadControl) {
        if (c.isStick) return
        onButtonEvent(c.linuxCodePrimary, true)
        if (c.linuxCodeSecondary != 0) {
            onAxisEvent(c.linuxCodeSecondary, 255)
        }
        when (c.id) {
            "dpad_l" -> onAxisEvent(0x10, -1)
            "dpad_r" -> onAxisEvent(0x10, 1)
            "dpad_u" -> onAxisEvent(0x11, -1)
            "dpad_d" -> onAxisEvent(0x11, 1)
        }
    }

    private fun dispatchControlRelease(c: PadControl) {
        if (c.isStick) {
            stickThumbOffset.remove(c.id)
            onAxisEvent(c.linuxCodePrimary, 0)
            onAxisEvent(c.linuxCodeSecondary, 0)
        } else {
            onButtonEvent(c.linuxCodePrimary, false)
            if (c.linuxCodeSecondary != 0) {
                onAxisEvent(c.linuxCodeSecondary, 0)
            }
            when (c.id) {
                "dpad_l", "dpad_r" -> onAxisEvent(0x10, 0)
                "dpad_u", "dpad_d" -> onAxisEvent(0x11, 0)
            }
        }
    }

    private fun centerX(c: PadControl): Float = c.normX * width
    private fun centerY(c: PadControl): Float = c.normY * height

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
        updatePillRects()

        val actionMasked = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        if (editMode) {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    if (editPillRect.contains(x, y)) {
                        draggingEditPill = true
                        pillDownX = x
                        pillDownY = y
                        return true
                    }
                    if (hidePillRect.contains(x, y)) {
                        draggingHidePill = true
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
                    if (draggingEditPill || draggingHidePill) {
                        val dx = event.getX(0) - pillDownX
                        val dy = event.getY(0) - pillDownY
                        pillTopX += dx
                        pillTopY += dy
                        pillDownX = event.getX(0)
                        pillDownY = event.getY(0)
                        updatePillRects()
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
                    if ((draggingEditPill || draggingHidePill) && pointerId == 0) {
                        val wasEdit = draggingEditPill
                        val wasHide = draggingHidePill
                        val moved = hypot(event.getX(pointerIndex) - pillDownX, event.getY(pointerIndex) - pillDownY)
                        draggingEditPill = false
                        draggingHidePill = false
                        persistPillPosition()
                        store.saveControls(controls, width, height)
                        if (moved < 20f && actionMasked != MotionEvent.ACTION_CANCEL) {
                            if (wasEdit) {
                                editMode = false
                                onEditModeToggleRequest?.invoke(false)
                            } else if (wasHide) {
                                editMode = false
                                onEditModeToggleRequest?.invoke(false)
                                onCollapseRequest?.invoke()
                            }
                        }
                        invalidate()
                        return true
                    }
                    activePointerToControl.remove(pointerId)
                    store.saveControls(controls, width, height)
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
                if (editPillRect.contains(x, y)) {
                    draggingEditPill = true
                    pillDownX = x
                    pillDownY = y
                    return true
                }
                if (hidePillRect.contains(x, y)) {
                    draggingHidePill = true
                    pillDownX = x
                    pillDownY = y
                    return true
                }
                val c = hitTest(x, y) ?: return false
                activePointerToControl[pointerId] = c
                if (c.isStick) {
                    updateStick(c, x, y)
                } else {
                    dispatchControlPress(c)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingEditPill || draggingHidePill) {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    pillTopX += x - pillDownX
                    pillTopY += y - pillDownY
                    pillDownX = x
                    pillDownY = y
                    updatePillRects()
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
                if ((draggingEditPill || draggingHidePill) && pointerId == 0) {
                    val wasEdit = draggingEditPill
                    val wasHide = draggingHidePill
                    val moved = hypot(event.getX(pointerIndex) - pillDownX, event.getY(pointerIndex) - pillDownY)
                    draggingEditPill = false
                    draggingHidePill = false
                    persistPillPosition()
                    if (moved < 20f && actionMasked != MotionEvent.ACTION_CANCEL) {
                        releaseAllInputs()
                        if (wasEdit) {
                            editMode = true
                            onEditModeToggleRequest?.invoke(true)
                        } else if (wasHide) {
                            onCollapseRequest?.invoke()
                        }
                    }
                    invalidate()
                    return true
                }
                val c = activePointerToControl.remove(pointerId) ?: return false
                dispatchControlRelease(c)
                invalidate()
                return true
            }
        }
        return false
    }

    private fun applyDeadzoneAndSensitivity(rawNorm: Float): Float {
        val mag = abs(rawNorm)
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
        if (width <= 0 || height <= 0) return
        updatePillRects()

        for (c in controls) {
            if (c.hidden && !editMode) continue
            val cx = centerX(c)
            val cy = centerY(c)
            val isPressed = activePointerToControl.values.any { it.id == c.id }

            if (c.hidden && editMode) {
                canvas.drawCircle(cx, cy, c.radiusPx, hiddenRingPaint)
                textPaint.textSize = min(28f, max(16f, c.radiusPx * 0.34f))
                canvas.drawText("${c.label}(H)", cx, cy + textPaint.textSize * 0.35f, textPaint)
                continue
            }

            canvas.drawCircle(cx, cy, c.radiusPx, if (isPressed) activeFillPaint else fillPaint)
            ringPaint.color = if (editMode) Color.YELLOW else Color.argb(195, 255, 255, 255)
            canvas.drawCircle(cx, cy, c.radiusPx, ringPaint)

            if (c.isStick) {
                val offset = stickThumbOffset[c.id]
                val tx = cx + (offset?.first ?: 0f)
                val ty = cy + (offset?.second ?: 0f)
                canvas.drawCircle(tx, ty, c.radiusPx * 0.36f, ringPaint)
            }

            textPaint.textSize = min(34f, max(18f, c.radiusPx * 0.40f))
            canvas.drawText(c.label, cx, cy + textPaint.textSize * 0.35f, textPaint)
        }

        canvas.drawRoundRect(
            editPillRect,
            24f,
            24f,
            if (editMode) editActivePillPaint else editPillPaint
        )
        ringPaint.color = if (editMode) Color.WHITE else Color.argb(210, 255, 215, 0)
        canvas.drawRoundRect(editPillRect, 24f, 24f, ringPaint)
        textPaint.textSize = 24f
        textPaint.color = if (editMode) Color.BLACK else Color.WHITE
        canvas.drawText(if (editMode) "DONE" else "EDIT", editPillRect.centerX(), editPillRect.centerY() + 8f, textPaint)

        canvas.drawRoundRect(hidePillRect, 24f, 24f, hidePillPaint)
        ringPaint.color = Color.argb(210, 140, 210, 255)
        canvas.drawRoundRect(hidePillRect, 24f, 24f, ringPaint)
        textPaint.color = Color.WHITE
        canvas.drawText("HIDE", hidePillRect.centerX(), hidePillRect.centerY() + 8f, textPaint)
    }
}
