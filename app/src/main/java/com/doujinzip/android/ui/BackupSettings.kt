package com.doujinzip.android.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doujinzip.android.R
import com.doujinzip.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun BackupSettings(onPrepare: suspend (String) -> File, onOpenConnection: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val lock = LocalAppLock.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var authorize by rememberSaveable { mutableStateOf<String?>(null) }
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSave by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRead by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupArchive?>(null) }
    var readyPath by rememberSaveable { mutableStateOf<String?>(null) }
    var restart by remember { mutableStateOf(false) }
    var failureDialog by remember { mutableStateOf(false) }
    fun failed(reason: Int = R.string.backup_failed) { error = true; message = strings[reason]; failureDialog = true }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) {
            readyPath?.let { File(it).delete() }
            readyPath = null
        }
        pendingSave = uri?.toString()
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRead = uri?.toString() }
    LaunchedEffect(action, lock.locked) {
        val next = action ?: return@LaunchedEffect
        if (lock.locked) return@LaunchedEffect
        // Consuming the request changes the effect key; the operation needs the screen scope.
        scope.launch {
            action = null
            if (next == "restore") { open.launch(arrayOf("application/zip", "application/octet-stream")); return@launch }
            busy = true; message = null; error = false
            try {
                val file = onPrepare(AppCompatDelegate.getApplicationLocales().toLanguageTags())
                readyPath = file.absolutePath
                save.launch("doujin-backup-${java.time.LocalDate.now()}.zip")
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                readyPath?.let { File(it).delete() }
                readyPath = null
                failed(R.string.backup_prepare_failed)
            } finally { busy = false }
        }
    }
    LaunchedEffect(pendingSave, lock.locked) {
        val selected = pendingSave ?: return@LaunchedEffect
        if (lock.locked) return@LaunchedEffect
        scope.launch {
            pendingSave = null; busy = true
            val file = readyPath?.let(::File)
            readyPath = null
            try {
                requireNotNull(file)
                withContext(Dispatchers.IO) {
                    val uri = android.net.Uri.parse(selected)
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output -> file.inputStream().use { it.copyTo(output) } }
                    val expected = file.inputStream().use(BackupFormat::streamDigest)
                    val actual = requireNotNull(context.contentResolver.openInputStream(uri)).use(BackupFormat::streamDigest)
                    check(expected == actual)
                }
                error = false; message = strings[R.string.backup_saved]
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                failed(R.string.backup_write_failed)
            } finally { file?.delete(); busy = false }
        }
    }
    LaunchedEffect(pendingRead, lock.locked) {
        val selected = pendingRead ?: return@LaunchedEffect
        if (lock.locked) return@LaunchedEffect
        scope.launch {
            pendingRead = null; busy = true
            try {
                preview = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(android.net.Uri.parse(selected))).use { input ->
                        BackupFormat.read(input).also { LocalBackupStore(context).validate(it) }
                    }
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                failed()
            } finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings[R.string.backup_scope])
        Text(strings[R.string.backup_plaintext], color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(enabled = !busy && action == null && readyPath == null && pendingSave == null, onClick = { if (lock.enabled) authorize = "export" else action = "export" }) { Text(strings[R.string.backup_export]) }
        OutlinedButton(enabled = !busy && action == null && readyPath == null && pendingSave == null, onClick = { if (lock.enabled) authorize = "restore" else action = "restore" }) { Text(strings[R.string.backup_import]) }
        TextButton(onClick = onOpenConnection, enabled = !busy) { Text(strings[R.string.ui_connection_settings]) }
        message?.let { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    }
    authorize?.let { next -> AlertDialog(onDismissRequest = { authorize = null },
        title = { Text(strings[R.string.backup_verify]) }, text = { Text(strings[R.string.backup_verify_notice]) },
        confirmButton = { TextButton(onClick = { authorize = null; lock.onBackground(); action = next }) { Text(strings[R.string.ui_confirm]) } },
        dismissButton = { TextButton(onClick = { authorize = null }) { Text(strings[R.string.ui_cancel]) } }) }
    preview?.let { archive -> AlertDialog(onDismissRequest = { preview = null },
        title = { Text(strings[R.string.backup_import]) },
        text = { Text(strings[R.string.backup_restore_notice] + if (archive.data.getBoolean("serverDataExcluded")) "\n" + strings[R.string.backup_reconnect] else "") },
        confirmButton = { TextButton(onClick = {
            preview = null; busy = true
            scope.launch {
                try { withContext(Dispatchers.IO) { LocalBackupStore(context).stage(archive) }; restart = true }
                catch (failure: Exception) { if (failure is kotlinx.coroutines.CancellationException) throw failure; failed() }
                finally { busy = false }
            }
        }) { Text(strings[R.string.ui_confirm]) } },
        dismissButton = { TextButton(onClick = { preview = null }) { Text(strings[R.string.ui_cancel]) } }) }
    if (restart) AlertDialog(onDismissRequest = {}, title = { Text(strings[R.string.backup_restart]) },
        text = { Text(strings[R.string.backup_restart_notice]) },
        confirmButton = { TextButton(onClick = {
            var owner: android.content.Context = context
            while (owner is android.content.ContextWrapper && owner !is Activity) owner = owner.baseContext
            (owner as? Activity)?.finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
        }) { Text(strings[R.string.ui_confirm]) } })
    if (failureDialog) AlertDialog(onDismissRequest = { failureDialog = false },
        title = { Text(strings[R.string.backup_title]) }, text = { Text(message ?: strings[R.string.backup_failed]) },
        confirmButton = { TextButton(onClick = { failureDialog = false; onOpenConnection() }) { Text(strings[R.string.ui_connection_settings]) } },
        dismissButton = { TextButton(onClick = { failureDialog = false }) { Text(strings[R.string.ui_confirm]) } })
    if (busy) AlertDialog(onDismissRequest = {}, title = { Text(strings[R.string.backup_working]) },
        text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(strings[R.string.backup_sync_notice]) } }, confirmButton = {})
}
