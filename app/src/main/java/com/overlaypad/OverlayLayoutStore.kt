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
        val btnR = base * 0.068f
        val stickR = base * 0.145f
        return mutableListOf(
            PadControl("ls", "LS", true, 0x00, 0x01, 0.18f, 0.68f, stickR, false),
            PadControl("rs", "RS", true, 0x03, 0x04, 0.72f, 0.42f, stickR * 0.85f, false),
            PadControl("a", "A", false, 304, 0, 0.84f, 0.76f, btnR, false),
            PadControl("b", "B", false, 305, 0, 0.92f, 0.64f, btnR, false),
            PadControl("x", "X", false, 307, 0, 0.76f, 0.64f, btnR, false),
            PadControl("y", "Y", false, 308, 0, 0.84f, 0.52f, btnR, false),
            PadControl("lb", "LB", false, 310, 0, 0.16f, 0.22f, btnR * 0.9f, false),
            PadControl("rb", "RB", false, 311, 0, 0.84f, 0.22f, btnR * 0.9f, false),
            PadControl("start", "START", false, 315, 0, 0.56f, 0.16f, btnR * 0.85f, false),
            PadControl("select", "BACK", false, 314, 0, 0.44f, 0.16f, btnR * 0.85f, false)
        )
    }

    fun loadControls(screenWidth: Int, screenHeight: Int): MutableList<PadControl> {
        val raw = prefs.getString("controls_json", null) ?: return defaultControls(screenWidth, screenHeight)
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PadControl>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PadControl(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        isStick = obj.getBoolean("isStick"),
                        linuxCodePrimary = obj.getInt("linuxCodePrimary"),
                        linuxCodeSecondary = obj.optInt("linuxCodeSecondary", 0),
                        normX = obj.getDouble("normX").toFloat().coerceIn(0.05f, 0.95f),
                        normY = obj.getDouble("normY").toFloat().coerceIn(0.08f, 0.92f),
                        radiusPx = obj.getDouble("radiusPx").toFloat().coerceIn(48f, 360f),
                        hidden = obj.optBoolean("hidden", false)
                    )
                )
            }
            if (list.isEmpty()) defaultControls(screenWidth, screenHeight) else list
        } catch (_: Throwable) {
            defaultControls(screenWidth, screenHeight)
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
