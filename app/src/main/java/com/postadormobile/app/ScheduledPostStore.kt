package com.postadormobile.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ScheduledPostStore {
    const val STATUS_PENDING = "pending"
    const val STATUS_READY = "ready"
    const val STATUS_OPENED = "opened"
    const val STATUS_SKIPPED = "skipped"

    data class Item(
        val id: String = UUID.randomUUID().toString(),
        val text: String,
        val mediaUrl: String,
        val target: String,
        val targetLabel: String,
        val scheduledAt: Long,
        val status: String = STATUS_PENDING,
        val createdAt: Long = System.currentTimeMillis()
    )

    private const val PREFS = "scheduled_posts"
    private const val KEY = "items_v1"

    fun list(context: Context): List<Item> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]").orEmpty()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    add(fromJson(o))
                }
            }.sortedWith(compareBy<Item> { it.scheduledAt }.thenBy { it.createdAt })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun get(context: Context, id: String): Item? = list(context).firstOrNull { it.id == id }

    fun add(context: Context, item: Item) {
        val items = list(context).toMutableList()
        items.removeAll { it.id == item.id }
        items.add(item)
        save(context, items)
    }

    fun updateStatus(context: Context, id: String, status: String) {
        val items = list(context).map {
            if (it.id == id) it.copy(status = status) else it
        }
        save(context, items)
    }

    fun delete(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
    }

    private fun save(context: Context, items: List<Item>) {
        val array = JSONArray()
        items.sortedWith(compareBy<Item> { it.scheduledAt }.thenBy { it.createdAt })
            .takeLast(200)
            .forEach { array.put(toJson(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    private fun toJson(item: Item) = JSONObject().apply {
        put("id", item.id)
        put("text", item.text)
        put("mediaUrl", item.mediaUrl)
        put("target", item.target)
        put("targetLabel", item.targetLabel)
        put("scheduledAt", item.scheduledAt)
        put("status", item.status)
        put("createdAt", item.createdAt)
    }

    private fun fromJson(o: JSONObject) = Item(
        id = o.optString("id", UUID.randomUUID().toString()),
        text = o.optString("text", ""),
        mediaUrl = o.optString("mediaUrl", ""),
        target = o.optString("target", "chooser"),
        targetLabel = o.optString("targetLabel", "Escolher aplicativo"),
        scheduledAt = o.optLong("scheduledAt", System.currentTimeMillis()),
        status = o.optString("status", STATUS_PENDING),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}
