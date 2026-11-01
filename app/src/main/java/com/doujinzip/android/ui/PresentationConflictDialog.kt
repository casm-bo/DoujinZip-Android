package com.doujinzip.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.doujinzip.android.R
import com.doujinzip.android.data.PresentationRecord
import com.doujinzip.android.data.PresentationCovers

@Composable
internal fun PresentationConflictDialog(record: PresentationRecord, onChoose: (Boolean) -> Unit, onLater: () -> Unit) {
    val remote = record.conflict ?: return
    val local = record.value
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val covers = remember(context) { PresentationCovers(context) }
    fun list(values: List<String>) = values.takeIf { it.isNotEmpty() }?.joinToString("\n")
    fun mode(value: Boolean?) = strings[when (value) { true -> R.string.conflict_enabled; false -> R.string.conflict_disabled; null -> R.string.conflict_auto }]
    val a = local.metadata
    val b = remote.metadata
    val fields = listOf(
        Triple(R.string.ui_title, local.title, remote.title),
        Triple(R.string.ui_artist, list(a.artists), list(b.artists)),
        Triple(R.string.ui_group, list(a.groups), list(b.groups)),
        Triple(R.string.ui_source_series, list(a.series), list(b.series)),
        Triple(R.string.ui_character, list(a.characters), list(b.characters)),
        Triple(R.string.ui_tags, list(a.tags), list(b.tags)),
        Triple(R.string.ui_type, a.galleryType, b.galleryType),
        Triple(R.string.ui_language, a.language, b.language),
        Triple(R.string.conflict_gallery_id, a.hitomiId, b.hitomiId),
        Triple(R.string.conflict_published, a.publishedDate, b.publishedDate),
        Triple(R.string.ui_webtoon_tag, mode(local.webtoon), mode(remote.webtoon)),
    ).filter { it.second != it.third }
    AlertDialog(onDismissRequest = onLater,
        title = { Text(strings[R.string.conflict_title]) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(local.title, style = MaterialTheme.typography.titleSmall)
                Text(strings[R.string.conflict_explanation])
                Row(Modifier.fillMaxWidth()) {
                    Text(strings[R.string.conflict_phone], Modifier.weight(1f))
                    Text(strings[R.string.conflict_pc], Modifier.weight(1f))
                }
                fields.forEach { (label, phone, pc) ->
                    Text(strings[label], style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(phone ?: strings[R.string.ui_none], Modifier.weight(1f))
                        Text(pc ?: strings[R.string.ui_none], Modifier.weight(1f))
                    }
                }
                if (local.coverHash != remote.coverHash) {
                    Text(strings[R.string.conflict_cover], style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(local.coverHash to R.string.conflict_phone, remote.coverHash to R.string.conflict_pc).forEach { (hash, label) ->
                            Box(Modifier.weight(1f)) {
                                if (hash == null) Text(strings[R.string.ui_none])
                                else AsyncImage(covers.file(hash), contentDescription = strings[label], modifier = Modifier.fillMaxWidth().height(160.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(true) }) { Text(strings[R.string.conflict_use_phone]) } },
        dismissButton = {
            Column {
                TextButton(onClick = { onChoose(false) }) { Text(strings[R.string.ui_use_pc_edit]) }
                TextButton(onClick = onLater) { Text(strings[R.string.ui_later]) }
            }
        })
}
