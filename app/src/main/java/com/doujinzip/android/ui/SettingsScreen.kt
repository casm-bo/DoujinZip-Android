package com.doujinzip.android.ui

import com.doujinzip.android.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import com.doujinzip.android.model.GalleryBorderKind
import com.doujinzip.android.model.GalleryBorders
import com.doujinzip.android.model.GalleryBorderStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doujinzip.android.BuildConfig
import com.doujinzip.android.model.AppThemeMode
import com.doujinzip.android.model.DesktopProfile
import com.doujinzip.android.model.StorageLocation
import com.doujinzip.android.model.DownloadDestination
import com.doujinzip.android.model.readableStoragePath
import com.doujinzip.android.model.ViewerPreferences

private enum class SettingsSection {
    HOME,
    APPEARANCE,
    LANGUAGE,
    LOCK,
    SUBSCRIPTIONS,
    VIEWER,
    LIBRARY,
    CONNECTION,
    SUPPORT,
    CHANGELOG,
    BACKUP,
}

@Composable
fun SettingsScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onPair: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onRemoveProfile: (String) -> Unit,
    onAddLibraryLocation: (String, String) -> Unit,
    onRemoveLibraryLocation: (String) -> Unit,
    onSetDownloadLocation: (String, String) -> Unit,
    onClearDownloadLocation: () -> Unit,
    onViewerPreferencesChange: (ViewerPreferences) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onAppStyleChange: (com.doujinzip.android.model.AppStyle) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onGalleryBorderChange: (GalleryBorderKind, GalleryBorderStyle) -> Unit,
    onCheckForUpdates: () -> Unit,
    openConnectionRequested: Boolean,
    onConnectionRequestHandled: () -> Unit,
    onDownloadDestinationChange: (DownloadDestination) -> Unit = {},
    requestedSection: String? = null,
    onSectionRequestHandled: () -> Unit = {},
    onSubscriptionOptionsChange: (com.doujinzip.android.security.SubscriptionOptions) -> Unit = {},
    onManageSubscriptions: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onPrepareBackup: suspend (String) -> java.io.File = { error("Backup unavailable") },
    initialSectionName: String = "HOME",
    onSectionChanged: (String) -> Unit = {},
    onScanServers: () -> Unit = {},
    onStopServerScan: () -> Unit = {},
    onSelectServer: (String) -> Unit = {},
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var sectionName by rememberSaveable { mutableStateOf(initialSectionName) }
    val section = runCatching { SettingsSection.valueOf(sectionName) }.getOrDefault(SettingsSection.HOME)
    val openSection: (SettingsSection) -> Unit = { sectionName = it.name; onSectionChanged(it.name) }

    BackHandler(enabled = section != SettingsSection.HOME) {
        openSection(SettingsSection.HOME)
    }

    LaunchedEffect(openConnectionRequested) {
        if (openConnectionRequested) {
            openSection(SettingsSection.CONNECTION)
            onConnectionRequestHandled()
        }
    }

    LaunchedEffect(requestedSection) {
        when (requestedSection) {
            "downloads" -> { openSection(SettingsSection.LIBRARY); onSectionRequestHandled() }
            "connection" -> { openSection(SettingsSection.CONNECTION); onSectionRequestHandled() }
        }
    }

    when (section) {
        SettingsSection.BACKUP -> SettingsDetailLayout(strings[R.string.backup_title], contentPadding, { openSection(SettingsSection.HOME) }) {
            SectionCard(strings[R.string.backup_title]) { BackupSettings(onPrepareBackup) { openSection(SettingsSection.CONNECTION) } }
        }
        SettingsSection.SUBSCRIPTIONS -> SettingsDetailLayout(strings[R.string.sub_settings],contentPadding,{openSection(SettingsSection.HOME)}) {
            SectionCard(strings[R.string.sub_settings]) { SubscriptionSettings(state.subscriptionOptions,onSubscriptionOptionsChange,onManageSubscriptions) }
        }
        SettingsSection.LANGUAGE -> SettingsDetailLayout(strings[R.string.app_language], contentPadding, { openSection(SettingsSection.HOME) }) { SectionCard(strings[R.string.app_language]) { LanguageSettings() } }
        SettingsSection.LOCK -> SettingsDetailLayout(strings[R.string.ui_app_lock], contentPadding, { openSection(SettingsSection.HOME) }) { SectionCard(strings[R.string.ui_app_lock]) { AppLockSettings() } }
        SettingsSection.HOME -> SettingsHomeScreen(
            state = state,
            contentPadding = contentPadding,
            onOpenSection = openSection,
        )
        SettingsSection.APPEARANCE -> AppearanceSettingsScreen(
            themeMode = state.themeMode,
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.HOME) },
            onChange = onThemeModeChange,
            appStyle = state.appStyle,
            onAppStyleChange = onAppStyleChange,
            fontSizeSp = state.fontSizeSp,
            onFontSizeChange = onFontSizeChange,
            borders = state.galleryBorders,
            onBorderChange = onGalleryBorderChange,
        )
        SettingsSection.VIEWER -> ViewerSettingsScreen(
            preferences = state.viewerPreferences,
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.HOME) },
            onChange = onViewerPreferencesChange,
        )
        SettingsSection.LIBRARY -> LibrarySettingsScreen(
            state = state,
            onDownloadDestinationChange = onDownloadDestinationChange,
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.HOME) },
            onAddLibraryLocation = onAddLibraryLocation,
            onRemoveLibraryLocation = onRemoveLibraryLocation,
            onSetDownloadLocation = onSetDownloadLocation,
            onClearDownloadLocation = onClearDownloadLocation,
        )
        SettingsSection.CONNECTION -> ConnectionSettingsScreen(
            state = state,
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.HOME) },
            onHostChange = onHostChange,
            onPortChange = onPortChange,
            onDeviceNameChange = onDeviceNameChange,
            onCodeChange = onCodeChange,
            onTestConnection = onTestConnection,
            onPair = onPair,
            onScanServers = onScanServers,
            onStopServerScan = onStopServerScan,
            onSelectServer = onSelectServer,
            onSelectProfile = onSelectProfile,
            onRemoveProfile = onRemoveProfile,
            onSyncNow = onSyncNow,
        )
        SettingsSection.SUPPORT -> SupportSettingsScreen(
            state = state,
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.HOME) },
            onCheckForUpdates = onCheckForUpdates,
            onOpenChangelog = { openSection(SettingsSection.CHANGELOG) },
        )
        SettingsSection.CHANGELOG -> ChangelogScreen(
            contentPadding = contentPadding,
            onBack = { openSection(SettingsSection.SUPPORT) },
        )
    }
}

@Composable
private fun SettingsHomeScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onOpenSection: (SettingsSection) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCategoryCard(symbol = ActionSymbol.LANGUAGE, title = strings[R.string.app_language], description = strings[R.string.language_choices], onClick = { onOpenSection(SettingsSection.LANGUAGE) })
        }
        item {
            SettingsCategoryCard(symbol = ActionSymbol.LOCK, title = strings[R.string.ui_app_lock], description = strings[R.string.ui_pin_password_pattern_and_biometrics], onClick = { onOpenSection(SettingsSection.LOCK) })
        }
        item {
            SettingsCategoryCard(
                symbol = ActionSymbol.APPEARANCE, title = strings[R.string.ui_appearance],
                description = when (state.themeMode) {
                    AppThemeMode.SYSTEM -> strings[R.string.ui_light_or_dark_theme_based_on_system_settings]
                    AppThemeMode.LIGHT -> strings[R.string.ui_using_light_theme]
                    AppThemeMode.DARK -> strings[R.string.ui_using_dark_theme]
                },
                onClick = { onOpenSection(SettingsSection.APPEARANCE) },
            )
        }
        item {
            SettingsCategoryCard(
                symbol = ActionSymbol.BOOK, title = strings[R.string.ui_reader_settings],
                description = strings[R.string.ui_reading_direction_page_numbers_scale_and_gestures],
                onClick = { onOpenSection(SettingsSection.VIEWER) },
            )
        }
        item { SettingsCategoryCard(symbol = ActionSymbol.BELL, title=strings[R.string.sub_settings],description=strings[R.string.sub_interval],onClick={onOpenSection(SettingsSection.SUBSCRIPTIONS)}) }
        item {
            SettingsCategoryCard(
                symbol = ActionSymbol.FOLDER, title = strings[R.string.ui_library_settings],
                description = strings[R.string.ui_library_folders_download_folder, (state.libraryLocations.size)] +
                    if (state.downloadLocation == null) strings[R.string.ui_not_set] else strings[R.string.ui_configured],
                onClick = { onOpenSection(SettingsSection.LIBRARY) },
            )
        }
        item {
            SettingsCategoryCard(
                symbol = ActionSymbol.PC, title = strings[R.string.ui_connection_settings],
                description = strings[R.string.ui_optional_pc_sync_registered_pcs, (state.profiles.size)],
                onClick = { onOpenSection(SettingsSection.CONNECTION) },
            )
        }
        item { SettingsCategoryCard(symbol = ActionSymbol.SAVE, title=strings[R.string.backup_title],description=strings[R.string.backup_summary],onClick={onOpenSection(SettingsSection.BACKUP)}) }
        item {
            SettingsCategoryCard(
                symbol = ActionSymbol.INFO, title = strings[R.string.ui_about],
                description = strings[R.string.ui_version_changelog_diagnostics_and_issues],
                onClick = { onOpenSection(SettingsSection.SUPPORT) },
            )
        }
    }
}

@Composable
private fun AppearanceSettingsScreen(
    themeMode: AppThemeMode,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onChange: (AppThemeMode) -> Unit,
    appStyle: com.doujinzip.android.model.AppStyle,
    onAppStyleChange: (com.doujinzip.android.model.AppStyle) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    borders: GalleryBorders,
    onBorderChange: (GalleryBorderKind, GalleryBorderStyle) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    SettingsDetailLayout(strings[R.string.ui_appearance], contentPadding, onBack) {
        SectionCard(strings[R.string.ui_theme]) {
            ThemeModeOption(strings[R.string.ui_system_settings], strings[R.string.ui_follow_device_appearance], AppThemeMode.SYSTEM, themeMode, onChange)
            HorizontalDivider()
            ThemeModeOption(strings[R.string.ui_light], strings[R.string.ui_always_use_light_theme], AppThemeMode.LIGHT, themeMode, onChange)
            HorizontalDivider()
            ThemeModeOption(strings[R.string.ui_dark], strings[R.string.ui_always_use_dark_theme], AppThemeMode.DARK, themeMode, onChange)
        }
        StyleSettings(appStyle, onAppStyleChange)
        FontSizeSettings(fontSizeSp, onFontSizeChange)
        BorderSettingsCard(strings[R.string.ui_gallery_borders], borders.normal) { onBorderChange(GalleryBorderKind.NORMAL, it) }
        BorderSettingsCard(strings[R.string.ui_series_borders], borders.series) { onBorderChange(GalleryBorderKind.SERIES, it) }
        BorderSettingsCard(strings[R.string.ui_favorite_borders], borders.favorite) { onBorderChange(GalleryBorderKind.FAVORITE, it) }
    }
}

@Composable
private fun FontSizeSettings(size: Float, onChange: (Float) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    var input by rememberSaveable { mutableStateOf(size.toInt().toString()) }
    SectionCard(strings[R.string.ui_font_size]) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(16f to R.string.ui_font_small, 18f to R.string.ui_font_normal, 20f to R.string.ui_font_large).forEach { (value, label) ->
                FilterChip(selected = size == value, onClick = { input = value.toInt().toString(); onChange(value) }, label = { Text(strings[label]) })
            }
        }
        HorizontalDivider()
        OutlinedTextField(value = input, onValueChange = { value ->
            input = value.take(5)
            com.doujinzip.android.model.parseAppFontSize(input)?.let(onChange)
        }, label = { Text(strings[R.string.ui_custom_font_sp]) },
            supportingText = { Text("12–28 sp") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = com.doujinzip.android.model.parseAppFontSize(input) == null)
        Text(strings[R.string.ui_preview], style = MaterialTheme.typography.titleMedium)
        Text(strings[R.string.ui_font_preview], style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BorderSettingsCard(title: String, style: GalleryBorderStyle, onChange: (GalleryBorderStyle) -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    SectionCard(title) {
        Text(strings[R.string.ui_width_dp_0_disables_border, (style.widthDp.toInt())])
        Slider(value = style.widthDp, onValueChange = { onChange(style.copy(widthDp = it)) }, valueRange = 0f..6f, steps = 5)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0xFF6750A4, 0xFFB0A6BC, 0xFF1565C0, 0xFF008577, 0xFFC62828, 0xFFE6A000, 0xFF202020).forEach { color ->
                FilterChip(selected = style.colorArgb == color, onClick = { onChange(style.copy(colorArgb = color)) },
                    label = { Text("●", color = Color(color)) })
            }
        }
        Card(colors = appCardColors(), border = if (style.widthDp > 0) BorderStroke(style.widthDp.dp, Color(style.colorArgb)) else null) {
            Text(strings[R.string.ui_preview], Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun ThemeModeOption(
    title: String,
    description: String,
    mode: AppThemeMode,
    selectedMode: AppThemeMode,
    onChange: (AppThemeMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = mode == selectedMode,
            onClick = { onChange(mode) },
        )
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SupportSettingsScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenChangelog: () -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    SettingsDetailLayout(strings[R.string.ui_about], contentPadding, onBack) {
        SectionCard(strings[R.string.ui_app_information]) {
            Text(strings[R.string.ui_version, (BuildConfig.VERSION_NAME), (BuildConfig.VERSION_CODE)])
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCheckForUpdates,
                enabled = !state.isCheckingForUpdates,
            ) {
                Text(if (state.isCheckingForUpdates) strings[R.string.ui_checking] else strings[R.string.ui_check_for_updates])
            }
        }
        SectionCard(strings[R.string.ui_history]) {
            Text(
                strings[R.string.ui_view_features_and_changes_for_each_version],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenChangelog,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(strings[R.string.ui_changelog]) }
        }
        SectionCard(strings[R.string.ui_troubleshooting]) {
            Text(
                strings[R.string.ui_diagnostics_include_only_app_device_versions_and_status_counts] +
                    strings[R.string.ui_no_connection_addresses_credentials_titles_or_file_paths_are_i],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { openGitHubIssue(context, state) },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(strings[R.string.ui_send_diagnostics])
            }
        }
    }
}

@Composable
private fun ChangelogScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    SettingsDetailLayout(strings[R.string.ui_changelog], contentPadding, onBack) {
        appChangelog.forEach { changelog ->
            SectionCard("v${changelog.version}") {
                changelog.changes.forEach { change ->
                    Text("• $change", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    symbol: ActionSymbol,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(colors = appCardColors(), onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(symbol, Modifier.padding(end = 16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ViewerSettingsScreen(
    preferences: ViewerPreferences,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onChange: (ViewerPreferences) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    SettingsDetailLayout(strings[R.string.ui_reader_settings], contentPadding, onBack) {
        listOf(R.string.ui_reading_mode, R.string.ui_page_turning, R.string.ui_view_58d697).forEachIndexed { index, title ->
            SectionCard(strings[title]) {
                ViewerSettingsContent(preferences = preferences, onChange = onChange, section = index)
            }
        }
    }
}

@Composable
private fun LibrarySettingsScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAddLibraryLocation: (String, String) -> Unit,
    onRemoveLibraryLocation: (String) -> Unit,
    onSetDownloadLocation: (String, String) -> Unit,
    onClearDownloadLocation: () -> Unit,
    onDownloadDestinationChange: (DownloadDestination) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val libraryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            persistTreePermission(context, it)
            onAddLibraryLocation(it.toString(), treeDisplayName(context, it))
        }
    }
    val downloadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            persistTreePermission(context, it)
            onSetDownloadLocation(it.toString(), treeDisplayName(context, it))
        }
    }

    SettingsDetailLayout(strings[R.string.ui_library_settings], contentPadding, onBack) {
        SectionCard(strings[R.string.ui_library_folders]) {
            Text(
                strings[R.string.ui_register_multiple_folders_to_scan_for_books],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            state.libraryLocations.forEachIndexed { index, location ->
                if (index > 0) HorizontalDivider()
                StorageLocationRow(location, strings[R.string.ui_remove]) {
                    onRemoveLibraryLocation(location.uri)
                }
            }
            Button(
                onClick = { libraryPicker.launch(null) },
                modifier = Modifier.padding(top = 10.dp),
            ) { Text(strings[R.string.ui_add_library_folder]) }
        }

        SectionCard(strings[R.string.ui_download_destination]) {
            listOf(DownloadDestination.DEVICE to R.string.ui_device_d79c45, DownloadDestination.DESKTOP to R.string.ui_desktop, DownloadDestination.ASK to R.string.ui_always_ask).forEach { (value, label) ->
                ChoiceMenuItem(strings[label], state.downloadDestination == value, onClick = { onDownloadDestinationChange(value) })
            }
        }
        SectionCard(strings[R.string.ui_download_folder]) {
            ReadOnlyPathRow(title = strings[R.string.ui_desktop_download_folder],
                path = state.desktopDownloadPath?.let { readableStoragePath(it) } ?: strings[R.string.ui_not_configured_on_desktop])
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            val location = state.downloadLocation
            if (location != null) StorageLocationRow(location, strings[R.string.ui_delete], strings[R.string.ui_local_download_folder], onClearDownloadLocation)
            else {
                Text(strings[R.string.ui_local_download_folder], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(strings[R.string.download_folder_unset], Modifier.padding(vertical = 8.dp))
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { downloadPicker.launch(null) }) {
                    Text(if (state.downloadLocation == null) strings[R.string.ui_choose_download_folder] else strings[R.string.ui_change_folder])
                }
            }
        }
    }
}

@Composable
private fun StorageLocationRow(
    location: StorageLocation,
    actionLabel: String,
    title: String? = null,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            title?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(location.displayName, fontWeight = FontWeight.Medium)
            Text(
                readableStoragePath(location.uri, location.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun ReadOnlyPathRow(title: String, path: String) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(path, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            strings[R.string.ui_can_only_be_changed_in_the_desktop_app],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionSettingsScreen(
    state: MainUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onPair: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onRemoveProfile: (String) -> Unit,
    onSyncNow: () -> Unit,
    onScanServers: () -> Unit,
    onStopServerScan: () -> Unit,
    onSelectServer: (String) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var advanced by rememberSaveable { mutableStateOf(false) }
    var permissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permissionAction?.invoke() else permissionDenied = true
        permissionAction = null
    }
    fun withLanPermission(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionAction = action
            permission.launch("android.permission.ACCESS_LOCAL_NETWORK")
        } else action()
    }
    DisposableEffect(Unit) { onDispose { onStopServerScan() } }
    val enabled = !state.isBusy && !state.isLoadingPage && !state.isLibrarySyncing
    var disconnect by rememberSaveable { mutableStateOf(false) }
    var keepProfile by rememberSaveable { mutableStateOf<String?>(null) }
    if (disconnect) androidx.compose.material3.AlertDialog(onDismissRequest = { disconnect = false },
        title = { Text(strings[R.string.connection_disconnect]) },
        text = { Text(strings[R.string.connection_disconnect_notice]) },
        confirmButton = { TextButton(onClick = { state.profiles.singleOrNull()?.let { onRemoveProfile(it.id) }; disconnect = false }) { Text(strings[R.string.connection_disconnect]) } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text(strings[R.string.ui_cancel]) } })
    keepProfile?.let { id -> androidx.compose.material3.AlertDialog(onDismissRequest = { keepProfile = null },
        title = { Text(strings[R.string.connection_choose_one]) },
        text = { Text(strings[R.string.connection_legacy_notice]) },
        confirmButton = { TextButton(onClick = { onSelectProfile(id); keepProfile = null }) { Text(strings[R.string.ui_confirm]) } },
        dismissButton = { TextButton(onClick = { keepProfile = null }) { Text(strings[R.string.ui_cancel]) } }) }
    SettingsDetailLayout(strings[R.string.ui_connection_settings], contentPadding, onBack) {
        if (state.profiles.isEmpty()) SectionCard(strings[R.string.ui_server_scan]) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { withLanPermission(onScanServers) }, enabled = enabled && !state.isScanningServers) {
                    Text(strings[if (state.isScanningServers) R.string.ui_server_scanning else R.string.ui_search])
                }
                OutlinedButton(onClick = { advanced = !advanced }) { Text(strings[R.string.ui_connection_advanced]) }
            }
            Spacer(Modifier.height(12.dp))
            state.discoveredServers.forEach { server ->
                androidx.compose.material3.OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(server.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Button(onClick = { onSelectServer(server.id) }, enabled = enabled) { Text(strings[R.string.ui_connect_server]) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (permissionDenied) Text(strings[R.string.ui_lan_permission_required], color = MaterialTheme.colorScheme.error)
            if (!state.isScanningServers && state.discoveredServers.isEmpty()) Text(strings[R.string.ui_server_scan_hint], style = MaterialTheme.typography.bodySmall)
        }
        if (state.profiles.isEmpty() && (advanced || state.selectedServerName != null)) SectionCard(strings[R.string.ui_connection_and_sync]) {
            Text(
                strings[R.string.ui_pc_connection_is_optional_and_does_not_affect_standalone_readi],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            state.selectedServerName?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            if (advanced) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.host,
                    onValueChange = onHostChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(strings[R.string.ui_ip_or_host]) },
                    placeholder = { Text("192.168.1.10") },
                    singleLine = true,
                    enabled = enabled,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.port,
                    onValueChange = onPortChange,
                    modifier = Modifier.width(112.dp),
                    label = { Text(strings[R.string.ui_port]) },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.deviceName,
                onValueChange = onDeviceNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings[R.string.ui_connection_display_name]) },
                placeholder = { Text(strings[R.string.ui_example_my_desktop]) },
                singleLine = true,
                enabled = enabled,
            )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.pairingCode,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(strings[R.string.ui_6_digit_pairing_code]) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (advanced) OutlinedButton(onClick = { withLanPermission(onTestConnection) }, enabled = enabled) { Text(strings[R.string.ui_check_status]) }
                Button(
                    onClick = { withLanPermission(onPair) },
                    enabled = enabled && state.pairingCode.length == 6 && state.deviceName.isNotBlank(),
                ) {
                    Text(strings[R.string.ui_pair])
                }
            }
        }

        if (state.profiles.size > 1) {
            SectionCard(strings[R.string.connection_choose_one]) {
                Text(strings[R.string.connection_legacy_notice])
                state.profiles.forEach { profile ->
                    OutlinedButton(onClick = { keepProfile = profile.id }, enabled = enabled) { Text(profile.name) }
                }
            }
        } else if (state.profiles.isNotEmpty()) {
            SectionCard(strings[R.string.ui_registered_pcs]) {
                val profile = state.profiles.single()
                Text(strings[R.string.server_name_label, profile.name], fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (advanced) Text(strings[R.string.server_address_label, profile.baseUrl], style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(strings[R.string.connection_status_label] + " " + strings[when (state.desktopConnectionState) {
                    DesktopConnectionState.CONNECTED -> R.string.ui_connected
                    DesktopConnectionState.CONNECTING -> R.string.ui_connecting
                    else -> R.string.ui_disconnected
                }], color = when (state.desktopConnectionState) {
                    DesktopConnectionState.CONNECTED -> androidx.compose.ui.graphics.Color(0xFF218739)
                    DesktopConnectionState.CONNECTING -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                })
                Spacer(Modifier.height(16.dp))
                Text(strings[R.string.connection_last_sync, if (state.lastDesktopSyncAt == 0L) "—" else
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(state.lastDesktopSyncAt))], style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                state.subscriptionSyncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.librarySyncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { withLanPermission(onSyncNow) }, enabled = enabled) { Text(strings[R.string.sub_sync_now]) }
                    OutlinedButton(onClick = { disconnect = true }, enabled = enabled) { Text(strings[R.string.connection_disconnect]) }
                }
            }
        }
        if (state.profiles.isNotEmpty()) SectionCard(null) {
            Text(strings[R.string.connection_single_notice], style = MaterialTheme.typography.bodyMedium)
        }
        if (state.isBusy) LoadingRow(strings[R.string.ui_connecting])
    }
}

@Composable
private fun SettingsDetailLayout(
    title: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("<") }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SavedDesktopRows(
    profiles: List<DesktopProfile>,
    selectedProfileId: String?,
    connectionState: DesktopConnectionState,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val strings = androidx.compose.ui.platform.LocalResources.current
    profiles.forEachIndexed { index, profile ->
        if (index > 0) HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = profile.id == selectedProfileId,
                onClick = { onSelect(profile.id) },
                enabled = enabled,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Medium)
                Text(
                    profile.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.id == selectedProfileId) {
                    val (label, color) = when (connectionState) {
                        DesktopConnectionState.CONNECTING -> strings[R.string.ui_connecting] to MaterialTheme.colorScheme.primary
                        DesktopConnectionState.CONNECTED -> strings[R.string.ui_connected] to MaterialTheme.colorScheme.primary
                        DesktopConnectionState.DISCONNECTED -> strings[R.string.ui_disconnected] to MaterialTheme.colorScheme.error
                        DesktopConnectionState.IDLE -> strings[R.string.ui_registered] to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
            TextButton(onClick = { onRemove(profile.id) }, enabled = enabled) { Text(strings[R.string.ui_delete]) }
        }
    }
}

@Composable
private fun SectionCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = appCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

private fun persistTreePermission(context: Context, uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
}

private fun treeDisplayName(context: Context, uri: Uri): String {
    val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    val fromProvider = runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return fromProvider?.takeIf { it.isNotBlank() }
        ?: runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':') }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.ui_selected_folder)
}
