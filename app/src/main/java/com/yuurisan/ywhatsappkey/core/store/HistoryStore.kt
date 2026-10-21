package com.yuurisan.ywhatsappkey.core.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HistoryStore(context: Context) {

    data class Entry(val key: String, val at: Long)

    private val sp = context.getSharedPreferences("ywk_history", Context.MODE_PRIVATE)
    private val limit = 40

    fun all(): List<Entry> {
        val raw = sp.getString("items", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Entry(o.optString("k"), o.optLong("t")))
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun add(key: String) {
        if (key.length != 4) return
        val now = System.currentTimeMillis()
        val list = all().filter { it.key != key }.toMutableList()
        list.add(0, Entry(key, now))
        while (list.size > limit) list.removeAt(list.size - 1)
        val arr = JSONArray()
        for (e in list) {
            arr.put(JSONObject().put("k", e.key).put("t", e.at))
        }
        sp.edit().putString("items", arr.toString()).apply()
    }

    fun clear() {
        sp.edit().remove("items").apply()
    }
}
