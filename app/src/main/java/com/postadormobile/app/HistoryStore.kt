package com.postadormobile.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HistoryStore {
    private const val PREFS = "postador_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 50

    const val STATUS_RECEBIDA = "RECEBIDA"
    const val STATUS_ENVIADA = "ENVIADA"
    const val STATUS_FALHOU = "FALHOU"

    data class Item(
        val id: String,
        val text: String,
        val mediaUrl: String,
        val target: String,
        val status: String,
        val detail: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    @Synchronized
    fun add(
        context: Context,
        id: String,
        text: String,
        mediaUrl: String,
        target: String,
        status: String = STATUS_RECEBIDA,
        detail: String = "Recebida do painel."
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = readArray(prefs.getString(KEY_ITEMS, "[]"))
        val now = System.currentTimeMillis()

        val item = JSONObject().apply {
            put("id", id)
            put("text", text)
            put("mediaUrl", mediaUrl)
            put("target", target)
            put("status", status)
            put("detail", detail)
            put("createdAt", now)
            put("updatedAt", now)
        }
        array.put(item)

        val trimmed = JSONArray()
        val start = maxOf(0, array.length() - MAX_ITEMS)
        for (i in start until array.length()) {
            trimmed.put(array.optJSONObject(i))
        }
        prefs.edit().putString(KEY_ITEMS, trimmed.toString()).apply()
    }

    @Synchronized
    fun update(context: Context, id: String, status: String, detail: String) {
        if (id.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = readArray(prefs.getString(KEY_ITEMS, "[]"))

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) {
                obj.put("status", status)
                obj.put("detail", detail)
                obj.put("updatedAt", System.currentTimeMillis())
                prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
                return
            }
        }
    }

    @Synchronized
    fun list(context: Context): List<Item> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = readArray(prefs.getString(KEY_ITEMS, "[]"))
        val result = mutableListOf<Item>()

        for (i in array.length() - 1 downTo 0) {
            val obj = array.optJSONObject(i) ?: continue
            result += Item(
                id = obj.optString("id"),
                text = obj.optString("text"),
                mediaUrl = obj.optString("mediaUrl"),
                target = obj.optString("target"),
                status = obj.optString("status", STATUS_RECEBIDA),
                detail = obj.optString("detail"),
                createdAt = obj.optLong("createdAt"),
                updatedAt = obj.optLong("updatedAt")
            )
        }
        return result
    }

    private fun readArray(raw: String?): JSONArray {
        return try {
            JSONArray(raw ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
    }
}
