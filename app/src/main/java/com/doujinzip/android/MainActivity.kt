package com.doujinzip.android

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import com.doujinzip.android.ui.LocalGalleryBorders
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.doujinzip.android.ui.DoujinZipApp
import com.doujinzip.android.ui.MainViewModel
import com.doujinzip.android.ui.AppLockViewModel
import com.doujinzip.android.ui.AppLockScreen
import com.doujinzip.android.ui.LocalAppLock
import com.doujinzip.android.ui.labelResource
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.doujinzip.android.ui.theme.DoujinZipTheme

class MainActivity : AppCompatActivity() {
    private var subscriptionTarget by mutableStateOf<Pair<String,Long>?>(null)
    private fun readSubscriptionTarget(intent:Intent?) {
        // Only our immutable PendingIntent may enter through the private alias.
        if(intent?.component?.className != com.doujinzip.android.data.SubscriptionNotifications.ENTRY_ACTIVITY) return
        if(intent?.hasExtra("subscription_gallery")==true) {
            val id=intent.getLongExtra("subscription_gallery",-1)
            val scope=intent.getStringExtra("subscription_scope").orEmpty()
            if(id>=0 && scope.length<=200) subscriptionTarget=scope to id
            intent.removeExtra("subscription_gallery");intent.removeExtra("subscription_scope")
        }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);readSubscriptionTarget(intent) }
    private var biometricPrompt: androidx.biometric.BiometricPrompt? = null
    private fun attachBiometric(request: com.doujinzip.android.security.BiometricRequest): androidx.biometric.BiometricPrompt =
        androidx.biometric.BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this), object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { appLock.biometricSuccess(request) }
            override fun onAuthenticationError(code: Int, message: CharSequence) { appLock.biometricError(request, code, message.toString()) }
        }).also { biometricPrompt = it }

    private fun requestBiometric(automatic: Boolean) {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return
        val available = androidx.biometric.BiometricManager.from(this).canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        val request = appLock.beginBiometric(automatic, available) ?: return
        try {
            attachBiometric(request).authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.ui_unlock_app))
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(getString(R.string.ui_use, getString(appLock.method.labelResource))).build())
        } catch (error: RuntimeException) {
            appLock.biometricError(request, androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE, error.message.orEmpty())
        }
    }

    private val appLock: AppLockViewModel by viewModels()
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) { appLock.onBackground(); biometricPrompt?.cancelAuthentication() }
        }
    }

    override fun onResume() {
        appLock.onForeground()
        super.onResume()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            val interactive = getSystemService(android.os.PowerManager::class.java).isInteractive
            val deviceLocked = getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked
            appLock.onBackground(allowBackupSave = interactive && !deviceLocked)
            biometricPrompt?.cancelAuthentication()
        }
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val restoredState = if (com.doujinzip.android.data.CrashDiagnostics.recoverNavigation(this)) null else savedInstanceState
        super.onCreate(restoredState)
        androidx.core.content.ContextCompat.registerReceiver(this, screenOffReceiver,
            android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        val backupRuntime = getSharedPreferences("backup_runtime", MODE_PRIVATE)
        if (backupRuntime.contains("locale")) {
            val tags = backupRuntime.getString("locale", "").orEmpty()
            check(backupRuntime.edit().remove("locale").commit())
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tags))
        }
        backupRuntime.getString("result", null)?.let { result ->
            check(backupRuntime.edit().remove("result").commit())
            val message = if (result == "success") getString(R.string.backup_restored) +
                if (backupRuntime.getBoolean("serverDataExcluded", false)) "\n" + getString(R.string.backup_reconnect) else ""
                else getString(R.string.backup_restore_failed)
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        }
        subscriptionTarget=restoredState?.takeIf { it.containsKey("subscription_gallery") }?.let { it.getString("subscription_scope").orEmpty() to it.getLong("subscription_gallery") }
        readSubscriptionTarget(intent)
        appLock.activeBiometricRequest?.let(::attachBiometric)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val backupProfile = viewModel.uiState.profiles.firstOrNull { it.id == viewModel.uiState.selectedProfileId }
            LaunchedEffect(backupProfile, appLock.locked, viewModel.autoBackupEnabled, viewModel.uiState.isStarting) {
                if (backupProfile == null || appLock.locked || !viewModel.autoBackupEnabled || viewModel.uiState.isStarting) return@LaunchedEffect
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (true) {
                        try { viewModel.checkDesktopBackup(backupProfile) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { /* Retry while the app is in use. */ }
                        delay(60_000)
                    }
                }
            }
            LaunchedEffect(appLock.locked,viewModel.uiState.isStarting,subscriptionTarget) {
                if(!appLock.locked && !viewModel.uiState.isStarting) subscriptionTarget?.let { target ->
                    subscriptionTarget=null
                    viewModel.openSubscriptionNotification(target.first,target.second)
                }
            }
            // Reading the whole UI state here reran window/theme work on every search/scroll update.
            val themeMode by remember(viewModel) { derivedStateOf { viewModel.uiState.themeMode } }
            val fontSize by remember(viewModel) { derivedStateOf { viewModel.uiState.fontSizeSp } }
            val style by remember(viewModel) { derivedStateOf { viewModel.uiState.appStyle } }
            val borders by remember(viewModel) { derivedStateOf { viewModel.uiState.galleryBorders } }
            val darkTheme = themeMode.usesDarkTheme(isSystemInDarkTheme())
            DisposableEffect(appLock.enabled, darkTheme) {
                if (appLock.enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                val transparent = android.graphics.Color.TRANSPARENT
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(transparent)
                } else {
                    SystemBarStyle.light(transparent, transparent)
                }
                enableEdgeToEdge(systemBarStyle, systemBarStyle)
                onDispose { }
            }
            DoujinZipTheme(darkTheme = darkTheme, fontSizeSp = fontSize, style = style) {
                CompositionLocalProvider(LocalGalleryBorders provides borders, LocalAppLock provides appLock) {
                    val holder = rememberSaveableStateHolder()
                    if (appLock.locked) AppLockScreen(appLock, ::requestBiometric) { moveTaskToBack(true) }
                    else holder.SaveableStateProvider("app") {
                        DoujinZipApp(viewModel)
                        if (backupProfile != null) com.doujinzip.android.ui.DesktopBackupRestorePrompt(viewModel, backupProfile)
                    }
                }
            }
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        subscriptionTarget?.let { outState.putString("subscription_scope",it.first);outState.putLong("subscription_gallery",it.second) }
        super.onSaveInstanceState(outState)
    }
}
