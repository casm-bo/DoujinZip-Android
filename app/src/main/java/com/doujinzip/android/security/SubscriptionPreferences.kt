package com.doujinzip.android.security

import android.content.Context

data class SubscriptionOptions(val intervalMinutes: Int = 60, val notifications: Boolean = true, val hideWhenLocked: Boolean = true)

internal fun normalizeSubscriptionCheckInterval(minutes:Int):Int = when(minutes) {
    5,10 -> 15
    180 -> 360
    720 -> 1440
    in SubscriptionPreferences.checkIntervals -> minutes
    else -> 60
}

class SubscriptionPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("subscription_options",Context.MODE_PRIVATE)
    fun loadSavedSort(): Int = preferences.getInt("saved_sort", 0).takeIf { it in 0..2 } ?: 0
    fun saveSavedSort(value: Int) {
        require(value in 0..2)
        preferences.edit().putInt("saved_sort", value).apply()
    }
    fun load() = SubscriptionOptions(normalizeSubscriptionCheckInterval(preferences.getInt("interval",60)),
        preferences.getBoolean("notifications",true),preferences.getBoolean("hide_locked",true))
    fun save(value: SubscriptionOptions) {
        require(value.intervalMinutes in checkIntervals)
        check(preferences.edit().putInt("interval",value.intervalMinutes).putBoolean("notifications",value.notifications).putBoolean("hide_locked",value.hideWhenLocked)
            .remove("sync_enabled").remove("sync_interval").commit())
    }
    companion object {
        val checkIntervals = listOf(15,30,60,360,1440)
    }
}
