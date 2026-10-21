package com.yuurisan.ywhatsappkey.core.store

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("ywk", Context.MODE_PRIVATE)

    /**
     * Target disimpan berurutan, bukan sebagai Set, supaya urutan yang diketik
     * pengguna tetap sama saat panel dibuka lagi.
     */
    var targetList: List<String>
        get() = sp.getString("tseq", "").orEmpty()
            .split(' ')
            .filter { it.length == 4 && it.all { c -> c.isDigit() } }
        set(value) = sp.edit()
            .putString("tseq", value.filter { it.length == 4 }.distinct().joinToString(" "))
            .apply()

    val targets: Set<String> get() = targetList.toSet()

    var twinMode: Boolean
        get() = sp.getBoolean("twin", false)
        set(value) = sp.edit().putBoolean("twin", value).apply()

    var hasGenerated: Boolean
        get() = sp.getBoolean("generated", false)
        set(value) = sp.edit().putBoolean("generated", value).apply()

    var bubbleX: Int
        get() = sp.getInt("bx", -1)
        set(value) = sp.edit().putInt("bx", value).apply()

    var bubbleY: Int
        get() = sp.getInt("by", -1)
        set(value) = sp.edit().putInt("by", value).apply()

    var panelX: Int
        get() = sp.getInt("px", Int.MIN_VALUE)
        set(value) = sp.edit().putInt("px", value).apply()

    var panelY: Int
        get() = sp.getInt("py", Int.MIN_VALUE)
        set(value) = sp.edit().putInt("py", value).apply()

    var panelCollapsed: Boolean
        get() = sp.getBoolean("pcol", false)
        set(value) = sp.edit().putBoolean("pcol", value).apply()

    var lastKey: String
        get() = sp.getString("last", "").orEmpty()
        set(value) = sp.edit().putString("last", value).apply()

    var autoSave: Boolean
        get() = sp.getBoolean("autosave", true)
        set(value) = sp.edit().putBoolean("autosave", value).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(value) = sp.edit().putBoolean("vibrate", value).apply()

    var genX: Int
        get() = sp.getInt("gx", -1)
        set(value) = sp.edit().putInt("gx", value).apply()

    var genY: Int
        get() = sp.getInt("gy", -1)
        set(value) = sp.edit().putInt("gy", value).apply()

    var saveX: Int
        get() = sp.getInt("sx", -1)
        set(value) = sp.edit().putInt("sx", value).apply()

    var saveY: Int
        get() = sp.getInt("sy", -1)
        set(value) = sp.edit().putInt("sy", value).apply()

    val hasGenPoint: Boolean get() = genX >= 0 && genY >= 0

    val hasSavePoint: Boolean get() = saveX >= 0 && saveY >= 0

    fun clearPoints() {
        sp.edit().putInt("gx", -1).putInt("gy", -1).putInt("sx", -1).putInt("sy", -1).apply()
    }

    fun twinSet(): Set<String> = (0..9).map { "$it$it$it$it" }.toSet()
}
