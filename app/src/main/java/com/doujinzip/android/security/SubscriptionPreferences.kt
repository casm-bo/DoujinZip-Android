package com.doujinzip.android.security

import android.content.Context

data class SubscriptionOptions(val intervalMinutes: Int = 60, val notifications: Boolean = true, val hideWhenLocked: Boolean = true,
    val syncEnabled: Boolean = true, val syncIntervalMinutes: Int = 15)

internal fun normalizeSubscriptionCheckInterval(minutes:Int):Int = when(minutes) {
    5,10 -> 15
    180 -> 360
    720 -> 1440
    in SubscriptionPreferences.checkIntervals -> minutes
    else -> 60
}

internal fun subscriptionSyncDue(options: SubscriptionOptions, now: Long, lastAttempt: Long?, unsupported: Boolean, manual: Boolean = false): Boolean {
    if(manual) return true
    if(!options.syncEnabled) return false
    val interval = if(unsupported) maxOf(options.syncIntervalMinutes,1440) else options.syncIntervalMinutes
    return lastAttempt == null || now < lastAttempt || now-lastAttempt >= interval*60_000L
}
class SubscriptionPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("subscription_options",Context.MODE_PRIVATE)
    fun loadSavedSort(): Int = preferences.getInt("saved_sort", 0).takeIf { it in 0..2 } ?: 0
    fun saveSavedSort(value: Int) {
        require(value in 0..2)
        preferences.edit().putInt("saved_sort", value).apply()
    }
    fun load() = SubscriptionOptions(normalizeSubscriptionCheckInterval(preferences.getInt("interval",60)),
        preferences.getBoolean("notifications",true),preferences.getBoolean("hide_locked",true),preferences.getBoolean("sync_enabled",true),
        preferences.getInt("sync_interval",15).takeIf { it in intervals } ?: 15)
    fun save(value: SubscriptionOptions) {
        require(value.intervalMinutes in checkIntervals && value.syncIntervalMinutes in intervals)
        check(preferences.edit().putInt("interval",value.intervalMinutes).putBoolean("notifications",value.notifications).putBoolean("hide_locked",value.hideWhenLocked)
            .putBoolean("sync_enabled",value.syncEnabled).putInt("sync_interval",value.syncIntervalMinutes).commit())
    }
    companion object {
        val checkIntervals = listOf(15,30,60,360,1440)
        val intervals = listOf(15,30,60,180,360,720,1440)
    }
}
