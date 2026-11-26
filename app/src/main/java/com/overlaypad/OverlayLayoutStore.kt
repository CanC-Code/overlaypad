package com.overlaypad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PadActionOption(
    val id: String,
    val label: String,
    val primaryCode: Int,
    val secondaryCode: Int = 0
)

private val PAD_ACTION_OPTIONS: List<PadActionOption> = listOf(
    PadActionOption("a", "A", 304, 0),
    PadActionOption("b", "B", 305, 0),
    PadActionOption("x", "X", 307, 0),
    PadActionOption("y", "Y", 308, 0),
    PadActionOption("lb", "LB", 310, 0),
    PadActionOption("rb", "RB", 311, 0),
    PadActionOption("lt", "LT", 312, 0x02),
    PadActionOption("rt", "RT", 313, 0x05),
    PadActionOption("l3", "L3", 317, 0),
    PadActionOption("r3", "R3", 318, 0),
    PadActionOption("dpad_u", "UP", 544, 0),
    PadActionOption("dpad_d", "DN", 545, 0),
    PadActionOption("dpad_l", "LEFT", 546, 0),
    PadActionOption("dpad_r", "RIGHT", 547, 0),
    PadActionOption("select", "BACK", 314, 0),
    PadActionOption("guide", "HOME", 316, 0),
    PadActionOption("start", "START", 315, 0)
)

private fun findActionById(id: String?): PadActionOption? {
    if (id.isNullOrEmpty()) return null
    return PAD_ACTION_OPTIONS.find { it.id == id }
}

data class PadControl(
    val id: String,
    val defaultLabel: String,
    val isStick: Boolean,
    var linuxCodePrimary: Int,
    var linuxCodeSecondary: Int = 0,
    var normX: Float,
    var normY: Float,
    var radiusPx: Float,
    var hidden: Boolean = false,
    var sticky: Boolean = false,
    var mappedActionId: String? = null
) {
    fun displayLabel(): String {
        val mapped = findActionById(mappedActionId)
        val base = if (mapped != null && mapped.id != id) {
            "$defaultLabel→${mapped.label}"
        } else {
            defaultLabel
        }
        return if (sticky && !isStick) "$base [S]" else base
    }
}

class OverlayLayoutStore(context: Context) {

    private val prefs = context.getSharedPreferences("overlay_layout", Context.MODE_PRIVATE)

    companion object {
        val ACTION_OPTIONS: List<PadActionOption>
            get() = PAD_ACTION_OPTIONS

        fun actionById(id: String?): PadActionOption? = findActionById(id)
    }

    private fun orientationSuffix(screenWidth: Int, screenHeight: Int): String {
        return if (screenWidth >= screenHeight) "land" else "port"
    }

    private fun controlsKey(screenWidth: Int, screenHeight: Int): String {
        return "controls_json_${orientationSuffix(screenWidth, screenHeight)}"
    }

    private fun pillXKey(screenWidth: Int, screenHeight: Int): String {
        return "pill_norm_x_${orientationSuffix(screenWidth, screenHeight)}"
    }

    private fun pillYKey(screenWidth: Int, screenHeight: Int): String {
        return "pill_norm_y_${orientationSuffix(screenWidth, screenHeight)}"
    }

    fun defaultControls(screenWidth: Int, screenHeight: Int): MutableList<PadControl> {
        val base = minOf(screenWidth, screenHeight).toFloat()
        val isPortrait = screenHeight > screenWidth
        val btnR = base * 0.056f
        val smallBtnR = base * 0.044f
        val stickR = base * 0.125f

        return if (isPortrait) {
            mutableListOf(
                PadControl("ls", "LS", true, 0x00, 0x01, 0.19f, 0.81f, stickR, false, false, null),
                PadControl("rs", "RS", true, 0x03, 0x04, 0.81f, 0.81f, stickR * 0.92f, false, false, null),

                PadControl("l3", "L3", false, 317, 0, 0.34f, 0.89f, smallBtnR, false, false, "l3"),
                PadControl("r3", "R3", false, 318, 0, 0.66f, 0.89f, smallBtnR, false, false, "r3"),

                PadControl("a", "A", false, 304, 0, 0.82f, 0.63f, btnR, false, false, "a"),
                PadControl("b", "B", false, 305, 0, 0.91f, 0.55f, btnR, false, false, "b"),
                PadControl("x", "X", false, 307, 0, 0.73f, 0.55f, btnR, false, false, "x"),
                PadControl("y", "Y", false, 308, 0, 0.82f, 0.47f, btnR, false, false, "y"),

                PadControl("dpad_u", "UP", false, 544, 0, 0.19f, 0.55f, smallBtnR, false, false, "dpad_u"),
                PadControl("dpad_d", "DN", false, 545, 0, 0.19f, 0.67f, smallBtnR, false, false, "dpad_d"),
                PadControl("dpad_l", "LEFT", false, 546, 0, 0.10f, 0.61f, smallBtnR, false, false, "dpad_l"),
                PadControl("dpad_r", "RIGHT", false, 547, 0, 0.28f, 0.61f, smallBtnR, false, false, "dpad_r"),

                PadControl("lb", "LB", false, 310, 0, 0.13f, 0.42f, btnR * 0.90f, false, false, "lb"),
                PadControl("rb", "RB", false, 311, 0, 0.87f, 0.34f, btnR * 0.90f, false, false, "rb"),
                PadControl("lt", "LT", false, 312, 0x02, 0.25f, 0.42f, btnR * 0.90f, false, false, "lt"),
                PadControl("rt", "RT", false, 313, 0x05, 0.75f, 0.34f, btnR * 0.90f, false, false, "rt"),

                PadControl("select", "BACK", false, 314, 0, 0.36f, 0.94f, smallBtnR * 0.92f, false, false, "select"),
                PadControl("guide", "HOME", false, 316, 0, 0.50f, 0.94f, smallBtnR * 0.92f, false, false, "guide"),
                PadControl("start", "START", false, 315, 0, 0.64f, 0.94f, smallBtnR * 0.92f, false, false, "start")
            )
        } else {
            mutableListOf(
                PadControl("ls", "LS", true, 0x00, 0x01, 0.16f, 0.72f, stickR, false, false, null),
                PadControl("rs", "RS", true, 0x03, 0x04, 0.70f, 0.72f, stickR * 0.90f, false, false, null),

                PadControl("l3", "L3", false, 317, 0, 0.27f, 0.84f, smallBtnR, false, false, "l3"),
                PadControl("r3", "R3", false, 318, 0, 0.81f, 0.84f, smallBtnR, false, false, "r3"),

                PadControl("a", "A", false, 304, 0, 0.86f, 0.56f, btnR, false, false, "a"),
                PadControl("b", "B", false, 305, 0, 0.93f, 0.46f, btnR, false, false, "b"),
                PadControl("x", "X", false, 307, 0, 0.79f, 0.46f, btnR, false, false, "x"),
                PadControl("y", "Y", false, 308, 0, 0.86f, 0.36f, btnR, false, false, "y"),

                PadControl("dpad_u", "UP", false, 544, 0, 0.16f, 0.44f, smallBtnR * 1.05f, false, false, "dpad_u"),
                PadControl("dpad_d", "DN", false, 545, 0, 0.16f, 0.58f, smallBtnR * 1.05f, false, false, "dpad_d"),
                PadControl("dpad_l", "LEFT", false, 546, 0, 0.09f, 0.51f, smallBtnR * 1.05f, false, false, "dpad_l"),
                PadControl("dpad_r", "RIGHT", false, 547, 0, 0.23f, 0.51f, smallBtnR * 1.05f, false, false, "dpad_r"),

                PadControl("lb", "LB", false, 310, 0, 0.14f, 0.20f, btnR * 0.92f, false, false, "lb"),
                PadControl("rb", "RB", false, 311, 0, 0.86f, 0.20f, btnR * 0.92f, false, false, "rb"),
                PadControl("lt", "LT", false, 312, 0x02, 0.25f, 0.20f, btnR * 0.92f, false, false, "lt"),
                PadControl("rt", "RT", false, 313, 0x05, 0.75f, 0.20f, btnR * 0.92f, false, false, "rt"),

                PadControl("select", "BACK", false, 314, 0, 0.42f, 0.16f, smallBtnR, false, false, "select"),
                PadControl("guide", "HOME", false, 316, 0, 0.50f, 0.16f, smallBtnR, false, false, "guide"),
                PadControl("start", "START", false, 315, 0, 0.58f, 0.16f, smallBtnR, false, false, "start")
            )
        }
    }

    fun loadControls(screenWidth: Int, screenHeight: Int): MutableList<PadControl> {
        val defaults = defaultControls(screenWidth, screenHeight)
        val raw = prefs.getString(controlsKey(screenWidth, screenHeight), null)
            ?: prefs.getString("controls_json", null)
            ?: return defaults
        return try {
            val arr = JSONArray(raw)
            val loadedById = LinkedHashMap<String, PadControl>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val defaultLabel = obj.optString("defaultLabel", obj.optString("label", id.uppercase()))
                val mappedId = obj.optString("mappedActionId", "").ifEmpty { id }
                val mappedOpt = findActionById(mappedId)
                loadedById[id] = PadControl(
                    id = id,
                    defaultLabel = defaultLabel,
                    isStick = obj.getBoolean("isStick"),
                    linuxCodePrimary = mappedOpt?.primaryCode ?: obj.getInt("linuxCodePrimary"),
                    linuxCodeSecondary = mappedOpt?.secondaryCode ?: obj.optInt("linuxCodeSecondary", 0),
                    normX = obj.getDouble("normX").toFloat().coerceIn(0.05f, 0.95f),
                    normY = obj.getDouble("normY").toFloat().coerceIn(0.08f, 0.92f),
                    radiusPx = obj.getDouble("radiusPx").toFloat().coerceIn(36f, 360f),
                    hidden = obj.optBoolean("hidden", false),
                    sticky = obj.optBoolean("sticky", false),
                    mappedActionId = if (obj.getBoolean("isStick")) null else mappedId
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

    fun saveControls(controls: List<PadControl>, screenWidth: Int = 0, screenHeight: Int = 0) {
        val arr = JSONArray()
        for (c in controls) {
            val obj = JSONObject()
                .put("id", c.id)
                .put("defaultLabel", c.defaultLabel)
                .put("label", c.displayLabel())
                .put("isStick", c.isStick)
                .put("linuxCodePrimary", c.linuxCodePrimary)
                .put("linuxCodeSecondary", c.linuxCodeSecondary)
                .put("normX", c.normX)
                .put("normY", c.normY)
                .put("radiusPx", c.radiusPx)
                .put("hidden", c.hidden)
                .put("sticky", c.sticky)
                .put("mappedActionId", c.mappedActionId ?: "")
            arr.put(obj)
        }
        val editor = prefs.edit().putString("controls_json", arr.toString())
        if (screenWidth > 0 && screenHeight > 0) {
            editor.putString(controlsKey(screenWidth, screenHeight), arr.toString())
        }
        editor.apply()
    }

    fun getPillNormX(screenWidth: Int = 0, screenHeight: Int = 0): Float {
        val key = if (screenWidth > 0 && screenHeight > 0) pillXKey(screenWidth, screenHeight) else "pill_norm_x"
        return prefs.getFloat(key, prefs.getFloat("pill_norm_x", 0.80f)).coerceIn(0.05f, 0.92f)
    }

    fun getPillNormY(screenWidth: Int = 0, screenHeight: Int = 0): Float {
        val key = if (screenWidth > 0 && screenHeight > 0) pillYKey(screenWidth, screenHeight) else "pill_norm_y"
        return prefs.getFloat(key, prefs.getFloat("pill_norm_y", 0.14f)).coerceIn(0.08f, 0.82f)
    }

    fun savePillPosition(normX: Float, normY: Float, screenWidth: Int = 0, screenHeight: Int = 0) {
        val clampedX = normX.coerceIn(0.05f, 0.92f)
        val clampedY = normY.coerceIn(0.08f, 0.82f)
        val editor = prefs.edit()
            .putFloat("pill_norm_x", clampedX)
            .putFloat("pill_norm_y", clampedY)
        if (screenWidth > 0 && screenHeight > 0) {
            editor.putFloat(pillXKey(screenWidth, screenHeight), clampedX)
            editor.putFloat(pillYKey(screenWidth, screenHeight), clampedY)
        }
        editor.apply()
    }

    fun getOverlayOpacity(): Float {
        return prefs.getFloat("overlay_opacity", 0.85f).coerceIn(0.25f, 1.0f)
    }

    fun setOverlayOpacity(value: Float) {
        prefs.edit().putFloat("overlay_opacity", value.coerceIn(0.25f, 1.0f)).apply()
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

    // --- Named Profiles & Per-Game Auto-Load ---

    private fun readProfilesRoot(): JSONObject {
        val raw = prefs.getString("saved_profiles_json", null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Throwable) {
            JSONObject()
        }
    }

    private fun writeProfilesRoot(root: JSONObject) {
        prefs.edit().putString("saved_profiles_json", root.toString()).apply()
    }

    fun listProfileNames(): List<String> {
        val root = readProfilesRoot()
        val list = mutableListOf<String>()
        val keys = root.keys()
        while (keys.hasNext()) {
            list.add(keys.next())
        }
        return list.sorted()
    }

    fun saveNamedProfile(rawName: String, linkedPackage: String? = null): Boolean {
        val name = rawName.trim()
        if (name.isEmpty()) return false
        val root = readProfilesRoot()
        val existingPkg = root.optJSONObject(name)?.optString("linkedPackage", "") ?: ""
        val pkgToStore = linkedPackage?.trim() ?: existingPkg

        val snapshot = JSONObject()
            .put("controls_json", prefs.getString("controls_json", ""))
            .put("controls_json_land", prefs.getString("controls_json_land", ""))
            .put("controls_json_port", prefs.getString("controls_json_port", ""))
            .put("pill_norm_x", prefs.getFloat("pill_norm_x", 0.80f).toDouble())
            .put("pill_norm_y", prefs.getFloat("pill_norm_y", 0.14f).toDouble())
            .put("pill_norm_x_land", prefs.getFloat("pill_norm_x_land", 0.80f).toDouble())
            .put("pill_norm_y_land", prefs.getFloat("pill_norm_y_land", 0.14f).toDouble())
            .put("pill_norm_x_port", prefs.getFloat("pill_norm_x_port", 0.80f).toDouble())
            .put("pill_norm_y_port", prefs.getFloat("pill_norm_y_port", 0.14f).toDouble())
            .put("overlay_opacity", getOverlayOpacity().toDouble())
            .put("stick_sensitivity", getStickSensitivity().toDouble())
            .put("stick_deadzone", getStickDeadzone().toDouble())
            .put("linkedPackage", pkgToStore)

        root.put(name, snapshot)
        writeProfilesRoot(root)
        setActiveProfileName(name)
        return true
    }

    fun loadNamedProfile(name: String): Boolean {
        val root = readProfilesRoot()
        val snap = root.optJSONObject(name) ?: return false
        val editor = prefs.edit()

        val cGeneric = snap.optString("controls_json", "")
        if (cGeneric.isNotEmpty()) editor.putString("controls_json", cGeneric)

        val cLand = snap.optString("controls_json_land", "")
        if (cLand.isNotEmpty()) editor.putString("controls_json_land", cLand) else editor.remove("controls_json_land")

        val cPort = snap.optString("controls_json_port", "")
        if (cPort.isNotEmpty()) editor.putString("controls_json_port", cPort) else editor.remove("controls_json_port")

        editor.putFloat("pill_norm_x", snap.optDouble("pill_norm_x", 0.80).toFloat())
        editor.putFloat("pill_norm_y", snap.optDouble("pill_norm_y", 0.14).toFloat())
        editor.putFloat("pill_norm_x_land", snap.optDouble("pill_norm_x_land", 0.80).toFloat())
        editor.putFloat("pill_norm_y_land", snap.optDouble("pill_norm_y_land", 0.14).toFloat())
        editor.putFloat("pill_norm_x_port", snap.optDouble("pill_norm_x_port", 0.80).toFloat())
        editor.putFloat("pill_norm_y_port", snap.optDouble("pill_norm_y_port", 0.14).toFloat())

        editor.putFloat("overlay_opacity", snap.optDouble("overlay_opacity", 0.85).toFloat().coerceIn(0.25f, 1.0f))
        editor.putFloat("stick_sensitivity", snap.optDouble("stick_sensitivity", 1.0).toFloat().coerceIn(0.25f, 2.5f))
        editor.putFloat("stick_deadzone", snap.optDouble("stick_deadzone", 0.10).toFloat().coerceIn(0.0f, 0.45f))
        editor.putString("active_profile_name", name)
        editor.apply()
        return true
    }

    fun deleteNamedProfile(name: String) {
        val root = readProfilesRoot()
        root.remove(name)
        writeProfilesRoot(root)
        if (getActiveProfileName() == name) {
            setActiveProfileName("")
        }
    }

    fun linkPackageToProfile(profileName: String, packageName: String) {
        val root = readProfilesRoot()
        val targetPkg = packageName.trim()
        if (targetPkg.isNotEmpty()) {
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = root.optJSONObject(k)
                if (obj != null && obj.optString("linkedPackage", "") == targetPkg && k != profileName) {
                    obj.put("linkedPackage", "")
                }
            }
        }
        val targetObj = root.optJSONObject(profileName) ?: return
        targetObj.put("linkedPackage", targetPkg)
        writeProfilesRoot(root)
    }

    fun getLinkedPackageForProfile(profileName: String): String {
        return readProfilesRoot().optJSONObject(profileName)?.optString("linkedPackage", "") ?: ""
    }

    fun findProfileForPackage(packageName: String): String? {
        if (packageName.isBlank()) return null
        val root = readProfilesRoot()
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val pkg = root.optJSONObject(k)?.optString("linkedPackage", "") ?: ""
            if (pkg == packageName) return k
        }
        return null
    }

    fun getActiveProfileName(): String = prefs.getString("active_profile_name", "") ?: ""

    fun setActiveProfileName(name: String) {
        prefs.edit().putString("active_profile_name", name).apply()
    }

    fun isAutoLoadPerGameEnabled(): Boolean = prefs.getBoolean("auto_load_per_game", false)

    fun setAutoLoadPerGameEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_load_per_game", enabled).apply()
    }

    fun getLastDetectedGamePackage(): String = prefs.getString("last_detected_game_pkg", "") ?: ""

    fun setLastDetectedGamePackage(pkg: String) {
        if (pkg.isNotBlank()) {
            prefs.edit().putString("last_detected_game_pkg", pkg.trim()).apply()
        }
    }
}
