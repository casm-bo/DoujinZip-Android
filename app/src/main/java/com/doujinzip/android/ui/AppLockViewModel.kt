package com.doujinzip.android.ui

import com.doujinzip.android.R

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doujinzip.android.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppLockViewModel @JvmOverloads constructor(application: Application, private val store: AppLockStore = AppLockStore(application)) : AndroidViewModel(application) {
    private val strings get() = androidx.core.content.ContextCompat.getContextForLanguage(getApplication()).resources
    private var credential = runCatching { store.read() }.getOrNull()
    var enabled by mutableStateOf(store.enabled); private set
    var locked by mutableStateOf(enabled); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(if (enabled && credential == null) strings[R.string.msg_cannot_read_lock_data_app_data_must_be_reset_to_recove] else null); private set
    var method by mutableStateOf(credential?.method ?: LockMethod.PIN); private set
    var biometric by mutableStateOf(credential?.biometric == true); private set
    private val biometricSession = BiometricSession()
    val activeBiometricRequest get() = biometricSession.active
    var biometricInProgress by mutableStateOf(false); private set
    var generation by mutableStateOf(0L); private set
    private var authorizedUntil = 0L
    private var backupSavePending = false
    private var backupSaveAway = false
    init { com.doujinzip.android.data.SubscriptionNotifications.session(application,!locked) }

    fun beginBackupSave() { backupSavePending = enabled && !locked; backupSaveAway = false }
    fun finishBackupSave(saved: Boolean) {
        val pending = backupSavePending || backupSaveAway
        backupSavePending = false; backupSaveAway = false
        if (pending && !saved) onBackground()
        if (!locked) com.doujinzip.android.data.SubscriptionNotifications.session(getApplication(),true)
    }
    // The picker result clears the exemption before onResume; any other return must lock.
    fun onForeground() { if (backupSavePending || backupSaveAway) onBackground() }
    fun onBackground(allowBackupSave: Boolean = false) {
        val keepUnlocked = allowBackupSave && backupSavePending && !locked
        backupSavePending = false; backupSaveAway = keepUnlocked
        com.doujinzip.android.data.SubscriptionNotifications.session(getApplication(),false)
        biometricSession.background(); generation = biometricSession.generation; biometricInProgress = false; authorizedUntil = 0
        if (enabled && !keepUnlocked) locked = true
    }
    fun clearError() { error = null }
    fun verify(secret: String, settings: Boolean = false, completed: (Boolean) -> Unit = {}) {
        if (busy || secret.length > 128) return
        val epoch = generation
        busy = true
        viewModelScope.launch {
            val success = runCatching { withContext(Dispatchers.IO) {
                check(store.remainingDelay(System.currentTimeMillis()) == 0L) { strings[R.string.msg_wait_a_moment_before_trying_again] }
                val valid = credential?.matches(secret) == true
                if (valid) store.resetAttempts() else store.failed(System.currentTimeMillis())
                valid
            } }
            busy = false
            if (epoch != generation) return@launch
            val accepted = success.getOrDefault(false)
            error = if (accepted) null else success.exceptionOrNull()?.message ?: strings[R.string.msg_incorrect_credential]
            if (accepted) { if (settings) authorizedUntil = SystemClock.elapsedRealtime() + 60_000 else { biometricSession.cancel(); biometricInProgress = false; locked = false; com.doujinzip.android.data.SubscriptionNotifications.session(getApplication(),true) } }
            completed(accepted)
        }
    }
    fun beginBiometric(automatic: Boolean, available: Boolean): BiometricRequest? {
        if (!enabled || !locked || !biometric || credential == null || busy) return null
        val request = biometricSession.begin(automatic, available)
        if (!available && !biometricInProgress) error = strings[R.string.ui_biometric_unavailable]
        if (request != null) { biometricInProgress = true; error = null }
        return request
    }
    fun biometricError(request: BiometricRequest, code: Int, message: String) {
        if (!biometricSession.complete(request)) return
        biometricInProgress = false
        error = when (biometricFailure(code)) {
            BiometricFailure.CANCELLED -> null
            BiometricFailure.TEMPORARY_LOCKOUT -> strings[R.string.ui_biometric_temporary_lockout]
            BiometricFailure.PERMANENT_LOCKOUT -> strings[R.string.ui_biometric_permanent_lockout]
            BiometricFailure.UNAVAILABLE -> strings[R.string.ui_biometric_unavailable]
            BiometricFailure.OTHER -> message
        }
    }
    fun biometricSuccess(request: BiometricRequest) {
        if (!enabled || !biometric || !locked || credential == null || !biometricSession.complete(request)) return
        biometricInProgress = false; busy = true
        viewModelScope.launch {
            val reset = runCatching { withContext(Dispatchers.IO) { store.resetAttempts() } }
            busy = false
            if (request.generation != generation) return@launch
            if (reset.isSuccess) { locked = false; error = null; com.doujinzip.android.data.SubscriptionNotifications.session(getApplication(),true) }
            else error = strings[R.string.msg_could_not_save_lock_settings]
        }
    }
    fun configure(method: LockMethod, secret: String, completed: () -> Unit) = change(completed) {
        LockCredential.create(method, secret).copy(biometric = biometric)
    }
    fun setBiometric(value: Boolean, completed: () -> Unit) = change(completed) { requireNotNull(credential).copy(biometric = value) }
    fun disable(completed: () -> Unit) = change(completed) { null }
    private fun change(completed: () -> Unit, create: () -> LockCredential?) {
        if (busy) return
        if (!canChangeLock(enabled, locked, authorizedUntil, SystemClock.elapsedRealtime())) { error = strings[R.string.msg_verify_your_current_credential_first]; return }
        val epoch = generation
        busy = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { create().also { if (it == null) store.clear() else store.save(it) } } }
            busy = false
            result.onSuccess { value ->
                credential = value; enabled = value != null; method = value?.method ?: LockMethod.PIN
                biometric = value?.biometric == true; locked = enabled && epoch != generation
                com.doujinzip.android.data.SubscriptionNotifications.session(getApplication(),!locked)
                authorizedUntil = 0; error = null
                completed()
            }.onFailure { error = it.message ?: strings[R.string.msg_could_not_save_lock_settings] }
        }
    }
}
