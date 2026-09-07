package com.postadormobile.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object FacebookGroupStore {
    data class Group(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val url: String
    )

    private const val PREFS = "facebook_groups"
    private const val KEY = "groups_v1"

    fun list(context: Context): List<Group> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]").orEmpty()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    val url = o.optString("url", "").trim()
                    if (name.isNotBlank() && url.isNotBlank()) {
                        add(Group(o.optString("id", UUID.randomUUID().toString()), name, url))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, group: Group) {
        val items = list(context).toMutableList()
        items.removeAll { it.id == group.id }
        items.add(group)
        save(context, items)
    }

    fun delete(context: Context, id: String) {
        save(context, list(context).filterNot { it.id == id })
    }

    private fun save(context: Context, items: List<Group>) {
        val array = JSONArray()
        items.take(50).forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("url", group.url)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
