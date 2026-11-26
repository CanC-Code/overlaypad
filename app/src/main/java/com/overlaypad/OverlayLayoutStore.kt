package com.overlaypad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PadControl(
    val id: String,
    val label: String,
    val isStick: Boolean,
    val linuxCodePrimary: Int,
    val linuxCodeSecondary: Int = 0,
    var normX: Float,
    var normY: Float,
    var radiusPx: Float,
    var hidden: Boolean = false
)

class OverlayLayoutStore(context: Context) {

    private val prefs = context.getSharedPreferences("overlay_layout", Context.MODE_PRIVATE)

    fun defaultControls(screenWidth: Int, screenHeight: Int): MutableList<PadControl> {
        val base = minOf(screenWidth, screenHeight).toFloat()
        val btnR = base * 0.056f
        val smallBtnR = base * 0.044f
        val stickR = base * 0.125f

        return mutableListOf(
            // Dual thumbsticks
            PadControl("ls", "LS", true, 0x00, 0x01, 0.16f, 0.72f, stickR, false),
            PadControl("rs", "RS", true, 0x03, 0x04, 0.70f, 0.72f, stickR * 0.90f, false),

            // Stick clicks (L3 / R3)
            PadControl("l3", "L3", false, 317, 0, 0.27f, 0.84f, smallBtnR, false),
            PadControl("r3", "R3", false, 318, 0, 0.81f, 0.84f, smallBtnR, false),

            // Face buttons (A, B, X, Y)
            PadControl("a", "A", false, 304, 0, 0.86f, 0.56f, btnR, false),
            PadControl("b", "B", false, 305, 0, 0.93f, 0.46f, btnR, false),
            PadControl("x", "X", false, 307, 0, 0.79f, 0.46f, btnR, false),
            PadControl("y", "Y", false, 308, 0, 0.86f, 0.36f, btnR, false),

            // D-pad (Up, Down, Left, Right)
            PadControl("dpad_u", "UP", false, 544, 0, 0.16f, 0.44f, smallBtnR * 1.05f, false),
            PadControl("dpad_d", "DN", false, 545, 0, 0.16f, 0.58f, smallBtnR * 1.05f, false),
            PadControl("dpad_l", "LT-", false, 546, 0, 0.09f, 0.51f, smallBtnR * 1.05f, false),
            PadControl("dpad_r", "RT+", false, 547, 0, 0.23f, 0.51f, smallBtnR * 1.05f, false),

            // Shoulder bumpers & triggers
            PadControl("lb", "LB", false, 310, 0, 0.14f, 0.20f, btnR * 0.92f, false),
            PadControl("rb", "RB", false, 311, 0, 0.86f, 0.20f, btnR * 0.92f, false),
            PadControl("lt", "LT", false, 312, 0x02, 0.25f, 0.20f, btnR * 0.92f, false),
            PadControl("rt", "RT", false, 313, 0x05, 0.75f, 0.20f, btnR * 0.92f, false),

            // System buttons
            PadControl("select", "BACK", false, 314, 0, 0.42f, 0.16f, smallBtnR, false),
            PadControl("guide", "HOME", false, 316, 0, 0.50f, 0.16f, smallBtnR, false),
            PadControl("start", "START", false, 315, 0, 0.58f, 0.16f, smallBtnR, false)
        )
    }

    fun loadControls(screenWidth: Int, screenHeight: Int): MutableList<PadControl> {
        val defaults = defaultControls(screenWidth, screenHeight)
        val raw = prefs.getString("controls_json", null) ?: return defaults
        return try {
            val arr = JSONArray(raw)
            val loadedById = LinkedHashMap<String, PadControl>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                loadedById[id] = PadControl(
                    id = id,
                    label = obj.getString("label"),
                    isStick = obj.getBoolean("isStick"),
                    linuxCodePrimary = obj.getInt("linuxCodePrimary"),
                    linuxCodeSecondary = obj.optInt("linuxCodeSecondary", 0),
                    normX = obj.getDouble("normX").toFloat().coerceIn(0.05f, 0.95f),
                    normY = obj.getDouble("normY").toFloat().coerceIn(0.08f, 0.92f),
                    radiusPx = obj.getDouble("radiusPx").toFloat().coerceIn(36f, 360f),
                    hidden = obj.optBoolean("hidden", false)
                )
            }
            if (loadedById.isEmpty()) return defaults

            val merged = mutableListOf<PadControl>()
            for (def in defaults) {
                merged.add(loadedById[def.id] ?: def)
            }
            for ((id, extra) in loadedById) {
                if (merged.none { it.id == id }) {
                    merged.add(extra)
                }
            }
            merged
        } catch (_: Throwable) {
            defaults
        }
    }

    fun saveControls(controls: List<PadControl>) {
        val arr = JSONArray()
        for (c in controls) {
            val obj = JSONObject()
                .put("id", c.id)
                .put("label", c.label)
                .put("isStick", c.isStick)
                .put("linuxCodePrimary", c.linuxCodePrimary)
                .put("linuxCodeSecondary", c.linuxCodeSecondary)
                .put("normX", c.normX)
                .put("normY", c.normY)
                .put("radiusPx", c.radiusPx)
                .put("hidden", c.hidden)
            arr.put(obj)
        }
        prefs.edit().putString("controls_json", arr.toString()).apply()
    }

    fun getStickSensitivity(): Float {
        return prefs.getFloat("stick_sensitivity", 1.0f).coerceIn(0.25f, 2.5f)
    }

    fun setStickSensitivity(value: Float) {
        prefs.edit().putFloat("stick_sensitivity", value.coerceIn(0.25f, 2.5f)).apply()
    }

    fun getStickDeadzone(): Float {
        return prefs.getFloat("stick_deadzone", 0.10f).coerceIn(0.0f, 0.45f)
    }

    fun setStickDeadzone(value: Float) {
        prefs.edit().putFloat("stick_deadzone", value.coerceIn(0.0f, 0.45f)).apply()
    }
}
