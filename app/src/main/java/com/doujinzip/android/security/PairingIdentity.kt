package com.doujinzip.android.security

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

internal fun hashPairingIdentity(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest("DoujinZip pairing:$value".toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

fun pairingIdentity(context: Context): String {
    val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    val value = androidId?.takeIf { it.isNotBlank() } ?: run {
        // Keep the fallback independent of profiles, which are cleared on disconnect.
        val preferences = context.getSharedPreferences("pairing-identity", Context.MODE_PRIVATE)
        preferences.getString("id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("id", it).apply()
        }
    }
    return hashPairingIdentity(value)
}
