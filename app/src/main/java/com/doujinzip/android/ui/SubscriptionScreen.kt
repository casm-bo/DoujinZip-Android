package com.doujinzip.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.doujinzip.android.R
import com.doujinzip.android.model.matchesSavedSearch
import com.doujinzip.android.model.SearchSubscription
import com.doujinzip.android.security.SubscriptionOptions
import com.doujinzip.android.security.SubscriptionPreferences
import android.Manifest
import android.os.Build
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Date

internal fun sortSavedSubscriptions(records: List<com.doujinzip.android.data.SubscriptionRecord>, sort: Int) = when (sort) {
    1 -> records.sortedBy { it.value.name.lowercase() }
    2 -> records
    else -> records.asReversed()
}

@Composable
internal fun SubscriptionScreen(state: MainUiState, onClose: () -> Unit, onTab: (Int) -> Unit,
    onRefresh: () -> Unit, onRead: (Long?) -> Unit, onEdit: (SearchSubscription?) -> Unit,
    onSave: (SearchSubscription) -> Unit, onResolve: (String,Boolean) -> Unit, onSearch: (SearchSubscription) -> Unit,
    onQueryChange: (String) -> Unit = {}, onLoadLanguages: () -> Unit = {},
    ) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val save:(SearchSubscription)->Unit = { value ->
        if(runCatching { value.validate() }.isSuccess && value.enabled && state.subscriptionOptions.notifications && Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        onSave(value)
    }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var savedFilter by rememberSaveable { mutableStateOf(0) }
    val preferences = remember(context) { SubscriptionPreferences(context) }
    var savedSort by remember { mutableStateOf(preferences.loadSavedSort()) }
    var filterMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val savedRecords = remember(state.subscriptions, savedQuery, savedFilter, savedSort) {
        val filtered = state.subscriptions.filter { record ->
            (!record.value.deleted || record.conflict != null) && record.value.matchesSavedSearch(savedQuery) &&
                when (savedFilter) { 1 -> record.value.enabled; 2 -> !record.value.enabled; 3 -> record.error != null || record.conflict != null; else -> true }
        }
        sortSavedSubscriptions(filtered, savedSort)
    }
    var deleting by remember { mutableStateOf<SearchSubscription?>(null) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClose) { ActionIcon(ActionSymbol.BACK) }
            Text(strings[R.string.sub_title],Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(state.subscriptionTab==0,{onTab(0)},label={Text(strings[R.string.sub_notifications]+" (${state.subscriptionNotices.count { !it.read }})")})
            FilterChip(state.subscriptionTab==1,{onTab(1)},label={Text(strings[R.string.sub_title])})
        }
        if(state.subscriptionsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.subscriptionSyncError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        state.subscriptionError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        if(state.subscriptionTab==0) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                TextButton({onRead(null)},enabled=state.subscriptionNotices.any { !it.read }) { Text(strings[R.string.sub_all_read]) }
                OutlinedButton(onRefresh,enabled=!state.subscriptionsBusy,contentPadding=PaddingValues(horizontal=10.dp,vertical=2.dp),modifier=Modifier.heightIn(min=32.dp)) {ActionIcon(ActionSymbol.REFRESH);Spacer(Modifier.width(8.dp));Text(strings[R.string.sub_check_now])}
            }
        }
        if(state.subscriptionTab==0) {
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                if(state.subscriptionNotices.isEmpty()) item { Text(strings[R.string.sub_empty]) }
                items(state.subscriptionNotices,key={it.galleryId}) { notice ->
                    WorkCard(title=notice.title,artist=notice.artist,metadata=notice.subscriptions.joinToString(", "),tags=emptyList(),
                        footer=strings[if(notice.read) R.string.sub_read else R.string.sub_unread],grid=false,onClick={onRead(notice.galleryId)},
                        border=if(!notice.read) androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null,cover={ AsyncImage(notice.thumbnail,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) })
                }
            }
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
            item {
                Card(colors=appCardColors(),modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                    Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            OutlinedButton({onEdit(SearchSubscription(name="",query=""))},contentPadding=PaddingValues(horizontal=10.dp,vertical=2.dp),modifier=Modifier.heightIn(min=32.dp)) { ActionIcon(ActionSymbol.ADD); Spacer(Modifier.width(8.dp)); Text(strings[R.string.sub_add]) }
                            OutlinedButton(onRefresh,enabled=!state.subscriptionsBusy,contentPadding=PaddingValues(horizontal=10.dp,vertical=2.dp),modifier=Modifier.heightIn(min=32.dp)) { ActionIcon(ActionSymbol.REFRESH); Spacer(Modifier.width(8.dp)); Text(strings[R.string.sub_check_now]) }
                        }
                        OutlinedTextField(savedQuery, {savedQuery=it}, modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),
                        singleLine=true, label={Text(strings[R.string.saved_subscription_search])}, leadingIcon={ActionIcon(ActionSymbol.SEARCH)},
                        trailingIcon={if(savedQuery.isNotEmpty()) IconButton({savedQuery=""}) {ActionIcon(ActionSymbol.CLEAR)}})
                        Text(strings[R.string.sub_hint],style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.padding(vertical=4.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box {
                                OutlinedButton({filterMenu=true},contentPadding=PaddingValues(horizontal=10.dp,vertical=2.dp),modifier=Modifier.heightIn(min=32.dp)) {ActionIcon(ActionSymbol.FILTER);Spacer(Modifier.width(8.dp));Text(strings[R.string.saved_filter_labels].split("|")[savedFilter])}
                                DropdownMenu(filterMenu,{filterMenu=false}) {strings[R.string.saved_filter_labels].split("|").forEachIndexed { index,label -> DropdownMenuItem(text={Text(label)},onClick={savedFilter=index;filterMenu=false})}}
                            }
                            Box {
                                OutlinedButton({sortMenu=true},contentPadding=PaddingValues(horizontal=10.dp,vertical=2.dp),modifier=Modifier.heightIn(min=32.dp)) {ActionIcon(ActionSymbol.SORT);Spacer(Modifier.width(8.dp));Text(strings[R.string.saved_sort_labels].split("|")[savedSort])}
                                DropdownMenu(sortMenu,{sortMenu=false}) {strings[R.string.saved_sort_labels].split("|").forEachIndexed { index,label -> DropdownMenuItem(text={Text(label)},onClick={savedSort=index;preferences.saveSavedSort(index);sortMenu=false})}}
                            }
                        }
                    }
                }
            }
            if(state.subscriptions.none { !it.value.deleted }) item { Text(strings[R.string.sub_no_searches]) }
            items(savedRecords,key={it.value.id}) { record ->
                val value=record.value
                Card(colors=appCardColors(),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(value.name,style=MaterialTheme.typography.titleLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                    if(value.deleted) Text(strings[R.string.sub_deleted],color=MaterialTheme.colorScheme.error)
                    OutlinedCard(Modifier.fillMaxWidth(),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(value.query,Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodyMedium)
                            if(value.languages.isNotEmpty()) {
                                HorizontalDivider()
                                Text(value.languages.sorted().joinToString(", "),Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(strings[R.string.sub_monitor],Modifier.weight(1f))
                        Switch(value.enabled,{save(value.copy(enabled=it))},enabled=record.conflict==null,modifier=Modifier.testTag("subscription-monitor"))
                    }
                    Text(if(record.checkedAt==0L) strings[R.string.sub_never] else strings[R.string.sub_checked,DateFormat.getDateTimeInstance().format(Date(record.checkedAt))],style=MaterialTheme.typography.bodySmall)
                    record.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                    if(record.mutationId != null) Text(strings[R.string.sub_pending],style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton({onSearch(value)},modifier=Modifier.weight(1f)) { Text(strings[R.string.ui_search]) }
                        TextButton({onEdit(value)},enabled=record.conflict==null,modifier=Modifier.weight(1f)) { Text(strings[R.string.sub_edit]) }
                        TextButton({deleting=value},enabled=record.conflict==null,modifier=Modifier.weight(1f)) { Text(strings[R.string.ui_delete]) }
                    }
                    record.conflict?.let { remote ->
                        Text(strings[R.string.sub_conflict],color=MaterialTheme.colorScheme.error)
                        Text("PC: ${remote.name}\n${remote.query}\n${remote.languages.joinToString(", ")}")
                        Text(strings[if(remote.deleted) R.string.sub_deleted else if(remote.enabled) R.string.sub_monitor_on else R.string.sub_monitor_off])
                        Row { TextButton({onResolve(value.id,true)}) { Text(strings[R.string.sub_keep_phone]) }; TextButton({onResolve(value.id,false)}) { Text(strings[R.string.sub_use_pc]) } }
                    }
                } }
            }
        }
    }
    state.subscriptionEditor?.let { value -> SubscriptionEditor(value,state,{onEdit(null)},save,onQueryChange,onLoadLanguages) }
    deleting?.let { value -> AlertDialog(onDismissRequest={deleting=null},title={Text(strings[R.string.ui_delete])},text={Text(value.name)},
        confirmButton={TextButton({onSave(value.copy(deleted=true));deleting=null}) { Text(strings[R.string.ui_delete]) }},
        dismissButton={TextButton({deleting=null}) { Text(strings[R.string.ui_cancel]) }}) }

}

@Composable
private fun SubscriptionEditor(value:SearchSubscription,state:MainUiState,onDismiss:()->Unit,onSave:(SearchSubscription)->Unit,
    onQueryChange:(String)->Unit,onLoadLanguages:()->Unit) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    var name by rememberSaveable(value.id) { mutableStateOf(value.name) }
    var query by rememberSaveable(value.id) { mutableStateOf(value.query) }
    var languages by rememberSaveable(value.id) { mutableStateOf(value.languages.toList()) }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    val expansion=remember { InlineSearchState() }
    var enabled by rememberSaveable(value.id) { mutableStateOf(value.enabled) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(strings[R.string.sub_title])},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it},label={Text(strings[R.string.sub_name])},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text(strings[R.string.sub_tags],style=MaterialTheme.typography.titleSmall)
            CompositionLocalProvider(LocalInlineSearch provides expansion) {
                UnifiedSearchField(query,{query=it;onQueryChange(it)},strings[R.string.sub_tags_placeholder],{}, {},
                    suggestions=state.subscriptionSuggestions,loading=state.isLoadingSubscriptionSuggestions,
                    onSuggestion={ val next=com.doujinzip.android.model.applySearchSuggestion(query,it.token);query=next;onQueryChange(next) },outlined=true)
            }
            OutlinedButton({showLanguages=!showLanguages}) { ActionIcon(ActionSymbol.LANGUAGE);Spacer(Modifier.width(8.dp));Text(strings[R.string.ui_language]+" (${languages.size})") }
            if(showLanguages) SearchLanguageList(state,languages.toSet(),
                { code -> languages=if(code in languages) languages-code else languages+code },{languages=emptyList()},onLoadLanguages)
            Row(verticalAlignment=Alignment.CenterVertically) { Text(strings[R.string.sub_monitor],Modifier.weight(1f)); Switch(enabled,{enabled=it},modifier=Modifier.testTag("subscription-editor-monitor")) }
            Text(strings[R.string.sub_hint],style=MaterialTheme.typography.bodySmall)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            state.subscriptionSyncError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        state.subscriptionError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton({
        val edited=value.copy(name=name.trim().ifBlank { query.trim() },query=com.doujinzip.android.model.normalizeSearchSeparators(query),languages=languages.toSet(),enabled=enabled)
        try { edited.validate();onSave(edited) } catch(e:IllegalArgumentException) { error=e.message }
    }) { Text(strings[R.string.ui_save]) }},dismissButton={TextButton(onDismiss) { Text(strings[R.string.ui_cancel]) }})
}

@Composable
internal fun SubscriptionSettings(options:SubscriptionOptions,onChange:(SubscriptionOptions)->Unit,onManage:()->Unit) {
    val strings=androidx.compose.ui.platform.LocalResources.current
    val context=LocalContext.current
    var allowed by remember { mutableStateOf(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed=context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { allowed=context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(strings[R.string.sub_interval],style=MaterialTheme.typography.titleMedium)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SubscriptionPreferences.checkIntervals.forEach { minutes ->
                FilterChip(options.intervalMinutes==minutes,{onChange(options.copy(intervalMinutes=minutes))},
                    label={Text(subscriptionIntervalLabel(strings,minutes))},modifier=Modifier.testTag("subscription-check-interval-$minutes"))
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) { Text(strings[R.string.sub_system],Modifier.weight(1f)); Switch(options.notifications,{
            onChange(options.copy(notifications=it))
            if(it && Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) }
        if(options.notifications && !allowed) {
            Text(strings[R.string.sub_permission],style=MaterialTheme.typography.bodySmall)
            TextButton({context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}) { Text(strings[R.string.ui_settings]) }
        }
        Row(verticalAlignment=Alignment.CenterVertically) { Text(strings[R.string.sub_hide_locked],Modifier.weight(1f)); Switch(options.hideWhenLocked,{onChange(options.copy(hideWhenLocked=it))},modifier=Modifier.testTag("subscription-hide-locked")) }
        Text(strings[if(options.hideWhenLocked) R.string.sub_hide_hint else R.string.sub_expose_hint],style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onManage) { Text(strings[R.string.sub_title]) }
    }
}

internal fun subscriptionIntervalLabel(strings:android.content.res.Resources,minutes:Int):String = when {
    minutes==1440 -> strings[R.string.sub_day]
    minutes>=60 && minutes%60==0 -> strings[R.string.sub_hours,minutes/60]
    else -> strings[R.string.sub_minutes,minutes]
}
