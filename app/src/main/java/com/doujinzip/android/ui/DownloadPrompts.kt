package com.doujinzip.android.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doujinzip.android.R
import com.doujinzip.android.model.DownloadDestination

@Composable
internal fun DownloadPrompts(viewModel: MainViewModel, onSettings: (String) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val state = viewModel.uiState
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let {
        runCatching {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.setDownloadLocation(it.toString(), DocumentsContract.getTreeDocumentId(it).substringAfter(':'))
        }.onFailure { android.widget.Toast.makeText(context, strings[R.string.ui_could_not_save_folder_access_permission], android.widget.Toast.LENGTH_LONG).show() }
    } }
    state.pendingDownloadGallery?.let { gallery ->
        AlertDialog(onDismissRequest = viewModel::cancelDownloadPrompt,
            title = { Text(strings[if (state.downloadFolderRequired) R.string.ui_choose_download_folder else R.string.ui_choose_download_destination]) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.pendingDownloadCount > 1) strings[R.string.ui_selected, state.pendingDownloadCount] else gallery.title, maxLines = 2)
                if (state.downloadFolderRequired) {
                    Text(strings[R.string.msg_choose_a_download_folder_first])
                    Button(onClick = { folder.launch(null) }, modifier = Modifier.fillMaxWidth()) { ActionIcon(ActionSymbol.FOLDER); Text(strings[R.string.ui_choose_folder], Modifier.padding(start = 8.dp)) }
                } else {
                    Button(onClick = { viewModel.chooseDownloadDestination(DownloadDestination.DEVICE) }, enabled = state.pendingDeviceDownloadAvailable, modifier = Modifier.fillMaxWidth()) { ActionIcon(ActionSymbol.DEVICE); Text(strings[R.string.ui_device], Modifier.padding(start = 8.dp)) }
                    OutlinedButton(onClick = { viewModel.chooseDownloadDestination(DownloadDestination.DESKTOP) }, enabled = state.pendingDesktopDownloadAvailable, modifier = Modifier.fillMaxWidth()) { ActionIcon(ActionSymbol.PC); Text(strings[R.string.ui_desktop], Modifier.padding(start = 8.dp)) }
                }
                TextButton(onClick = { onSettings("downloads") }) { ActionIcon(ActionSymbol.SETTINGS); Text(strings[R.string.ui_download_settings], Modifier.padding(start = 8.dp)) }
            } }, confirmButton = {}, dismissButton = { TextButton(onClick = viewModel::cancelDownloadPrompt) { Text(strings[R.string.ui_cancel]) } })
    }
    if (state.desktopDownloadConnectionPrompt) {
        var suppress by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { viewModel.dismissDesktopDownloadConnectionPrompt(suppress) }, title = { Text(strings[R.string.ui_connect_to_pc]) },
            text = { Column {
                Text(strings[R.string.ui_desktop_download_offline])
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(suppress, { suppress = it })
                    Text(strings[R.string.ui_dont_show_again], Modifier.weight(1f))
                }
            } }, confirmButton = { TextButton(onClick = { viewModel.dismissDesktopDownloadConnectionPrompt(suppress); onSettings("connection") }) { Text(strings[R.string.ui_connect_to_pc]) } },
            dismissButton = { TextButton(onClick = { viewModel.dismissDesktopDownloadConnectionPrompt(suppress) }) { Text(strings[R.string.ui_confirm]) } })
    }
}
