package com.doujinzip.android.data

import org.json.JSONObject

internal fun validateServerEditResponse(kind: String, body: JSONObject, response: JSONObject): List<String> {
    if (kind == "reset-cover") {
        require(response.getString("mutationId") == body.getString("mutationId"))
        return listOf(response.getString("status")).also { require(it.single() in setOf("applied", "conflict", "not_found")) }
    }
    if (kind == "metadata") {
        require(response.getJSONObject("value").getString("key") == body.getJSONObject("value").getString("key"))
        return listOf(response.getString("status")).also { statuses -> require(statuses.single() in setOf("applied", "already_applied", "conflict")) }
    }
    val expected = body.getJSONArray(if (kind == "state") "mutations" else "assignments")
    val results = response.getJSONArray("results")
    require(expected.length() > 0 && results.length() == expected.length())
    val ids = (0 until expected.length()).map { expected.getJSONObject(it).getString("mutationId") }.toSet()
    require((0 until results.length()).map { results.getJSONObject(it).getString("mutationId") }.toSet() == ids)
    return (0 until results.length()).map { results.getJSONObject(it).getString("status") }.also { statuses ->
        require(statuses.all { it in setOf("applied", "already_applied", "duplicate", "conflict", "not_found") })
    }
}
