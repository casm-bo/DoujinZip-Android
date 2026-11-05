package com.doujinzip.android.data

import android.content.Context
import org.json.JSONObject

/** Requests are durable before sending; confirmed UI state is stored elsewhere. */
class ServerEditStore(context: Context) {
    private val preferences = context.getSharedPreferences("server_edit_requests", Context.MODE_PRIVATE)
    @Synchronized fun put(profileId: String, id: String, kind: String, body: JSONObject) {
        val value = JSONObject().put("profile", profileId).put("kind", kind).put("body", body)
        check(preferences.edit().putString(id, value.toString()).commit())
    }
    @Synchronized fun pending(profileId: String): List<Pair<String, JSONObject>> = preferences.all.mapNotNull { (id, raw) ->
        val value = JSONObject(raw as String)
        if (value.getString("profile") == profileId) id to value else null
    }
    @Synchronized fun remove(id: String, expected: JSONObject) {
        if (preferences.getString(id, null) == expected.toString()) check(preferences.edit().remove(id).commit())
    }
    @Synchronized fun advanceProgress(id: String, sent: JSONObject, state: JSONObject) {
        val current = preferences.getString(id, null)?.let(::JSONObject) ?: return
        if (!id.startsWith("progress:") || current.toString() == sent.toString()) return
        val pending = current.getJSONObject("body").getJSONArray("mutations").getJSONObject(0)
        val previous = sent.getJSONObject("body").getJSONArray("mutations").getJSONObject(0)
        if (pending.getLong("baseVersion") == previous.getLong("baseVersion")) {
            pending.put("baseVersion", state.getLong("version"))
            pending.put("baseCurrentPage", state.getInt("currentPage"))
            pending.put("baseIsRead", state.getBoolean("isRead"))
            check(preferences.edit().putString(id, current.toString()).commit())
        }
    }
}
