package com.example.feature.session.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusSearchField
import com.example.core.util.InstalledAppItem
import com.example.core.util.InstalledAppsProvider
import com.example.feature.session.SpecialAppOption

@Composable
fun SelectAppsToBlockSheet(
    youtubeOption: SpecialAppOption,
    browserOption: SpecialAppOption,
    isDistractingMasterEnabled: Boolean,
    blockedAppPackages: Set<String>,
    approvedChannelCount: Int = 0,
    onYoutubeOptionChange: (SpecialAppOption) -> Unit,
    onBrowserOptionChange: (SpecialAppOption) -> Unit,
    onToggleDistractingMaster: (Boolean, Set<String>) -> Unit,
    onToggleAppBlocked: (String, Boolean) -> Unit,
    onOpenYouTubeChannels: () -> Unit,
    onOpenWebsiteBlocker: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var installedBrowsers by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var hasScannedApps by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val close: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    LaunchedEffect(Unit) {
        val appLimitPackages = runCatching {
            com.example.FocusShieldApp.instance.appLimitRepository.getAllLimits()
                .filter { it.isEnabled }
                .mapTo(mutableSetOf()) { it.packageName }
        }.getOrElse { mutableSetOf() }
        val snapshot = InstalledAppsProvider.getInstalledAppsSnapshot(context, appLimitPackages)
        installedBrowsers = snapshot.browsers
        installedApps = snapshot.userApps
        hasScannedApps = true

        // A daily limit is a strong signal that the app is distracting. Mirror it into this
        // session draft only; the permanent blocked-app repository is never changed here.
        snapshot.userApps
            .filter { it.hasAppLimit && it.packageName !in blockedAppPackages }
            .forEach { onToggleAppBlocked(it.packageName, true) }
    }

    val visibleApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps else installedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val distractingApps = remember(visibleApps) { visibleApps.filter(::isLikelyDistracting) }
    val otherApps = remember(visibleApps, distractingApps) {
        val distractingPackages = distractingApps.mapTo(mutableSetOf()) { it.packageName }
        visibleApps.filterNot { it.packageName in distractingPackages }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.94f)
            .background(FocusColors.Background)
            .padding(horizontal = FocusSpacing.screenHorizontal)
            .padding(top = 12.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(44.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(FocusColors.ControlBorder)
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.session_apps_title),
                color = FocusColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            IconButton(onClick = close) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = FocusColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FocusSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = stringResource(R.string.session_apps_search),
            onSearch = {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
            modifier = Modifier.testTag("blocked_apps_search_field")
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (searchQuery.isBlank() || "youtube".contains(searchQuery, true)) {
                item(key = "youtube") {
                    YouTubeSettingsCard(
                        option = youtubeOption,
                        channelCount = approvedChannelCount,
                        onOptionChange = onYoutubeOptionChange,
                        onOpenChannels = onOpenYouTubeChannels
                    )
                }
            }
            if (searchQuery.isBlank() || "browser apps".contains(searchQuery, true)) {
                item(key = "browsers") {
                    BrowserSettingsCard(
                        browsers = installedBrowsers,
                        option = browserOption,
                        onOptionChange = onBrowserOptionChange,
                        onOpenWebsiteBlocker = onOpenWebsiteBlocker
                    )
                }
            }

            if (distractingApps.isNotEmpty()) {
                item(key = "distracting") {
                    AppGroupCard(
                        title = stringResource(R.string.session_apps_distracting_short),
                        apps = distractingApps,
                        blockedPackages = blockedAppPackages,
                        fallbackGroupChecked = isDistractingMasterEnabled,
                        onToggleGroup = onToggleDistractingMaster,
                        onToggleApp = onToggleAppBlocked
                    )
                }
            }
            if (otherApps.isNotEmpty()) {
                item(key = "others") {
                    AppGroupCard(
                        title = stringResource(R.string.session_apps_others),
                        apps = otherApps,
                        blockedPackages = blockedAppPackages,
                        fallbackGroupChecked = false,
                        onToggleGroup = onToggleDistractingMaster,
                        onToggleApp = onToggleAppBlocked
                    )
                }
            }
            if (hasScannedApps && visibleApps.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.session_apps_no_results),
                        color = FocusColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else if (!hasScannedApps) {
                item {
                    Text(
                        text = stringResource(R.string.session_apps_scanning),
                        color = FocusColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTubeSettingsCard(
    option: SpecialAppOption,
    channelCount: Int,
    onOptionChange: (SpecialAppOption) -> Unit,
    onOpenChannels: () -> Unit
) {
    val studyMode = option == SpecialAppOption.STUDY_MODE
    SpecialSettingsCard {
        AppHeaderSwitch(
            title = stringResource(R.string.session_apps_youtube),
            checked = option == SpecialAppOption.BLOCK_COMPLETELY,
            onCheckedChange = {
                onOptionChange(if (it) SpecialAppOption.BLOCK_COMPLETELY else SpecialAppOption.ALLOW_COMPLETELY)
            },
            icon = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF0033)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
            }
        )
        ThinDivider()
        NestedSwitchRow(
            label = stringResource(R.string.session_apps_block_shorts),
            checked = studyMode,
            onCheckedChange = {
                onOptionChange(if (it) SpecialAppOption.STUDY_MODE else SpecialAppOption.ALLOW_COMPLETELY)
            }
        )
        NestedSwitchRow(
            label = stringResource(R.string.session_apps_block_homepage),
            checked = studyMode,
            onCheckedChange = {
                onOptionChange(if (it) SpecialAppOption.STUDY_MODE else SpecialAppOption.ALLOW_COMPLETELY)
            }
        )
        NestedSwitchRow(
            label = stringResource(R.string.session_apps_study_mode),
            checked = studyMode,
            onCheckedChange = {
                onOptionChange(if (it) SpecialAppOption.STUDY_MODE else SpecialAppOption.ALLOW_COMPLETELY)
            },
            supportingContent = {
                CountLink(
                    text = pluralStringResource(
                        R.plurals.session_channels_added,
                        channelCount,
                        channelCount
                    ),
                    onClick = onOpenChannels
                )
            }
        )
    }
}

@Composable
private fun BrowserSettingsCard(
    browsers: List<InstalledAppItem>,
    option: SpecialAppOption,
    onOptionChange: (SpecialAppOption) -> Unit,
    onOpenWebsiteBlocker: () -> Unit
) {
    val studyMode = option == SpecialAppOption.STUDY_MODE
    SpecialSettingsCard {
        AppHeaderSwitch(
            title = stringResource(R.string.session_apps_browser),
            checked = option == SpecialAppOption.BLOCK_COMPLETELY,
            onCheckedChange = {
                onOptionChange(if (it) SpecialAppOption.BLOCK_COMPLETELY else SpecialAppOption.ALLOW_COMPLETELY)
            },
            supportingText = when {
                option == SpecialAppOption.BLOCK_COMPLETELY -> stringResource(R.string.session_apps_block_all)
                option == SpecialAppOption.STUDY_MODE -> stringResource(R.string.session_apps_study_mode)
                browsers.any { it.hasAppLimit } -> stringResource(R.string.session_apps_limit_set)
                else -> null
            },
            icon = {
                val icon = browsers.firstOrNull()?.iconBitmap
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Rounded.Language,
                        contentDescription = null,
                        tint = FocusColors.CyanBlue,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        )
        ThinDivider()
        NestedSwitchRow(
            label = stringResource(R.string.session_apps_block_adult),
            checked = studyMode,
            onCheckedChange = {
                onOptionChange(if (it) SpecialAppOption.STUDY_MODE else SpecialAppOption.ALLOW_COMPLETELY)
            }
        )
        NestedSwitchRow(
            label = stringResource(R.string.session_apps_study_mode),
            checked = studyMode,
            onCheckedChange = {
                onOptionChange(if (it) SpecialAppOption.STUDY_MODE else SpecialAppOption.ALLOW_COMPLETELY)
            },
            supportingContent = {
                CountLink(
                    text = stringResource(R.string.session_apps_websites_allowed),
                    onClick = onOpenWebsiteBlocker
                )
            }
        )
    }
}

@Composable
private fun SpecialSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = FocusColors.SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), content = content)
    }
}

@Composable
private fun AppHeaderSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
    supportingText: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = FocusColors.TextPrimary, fontWeight = FontWeight.Bold)
            if (supportingText != null) {
                Text(supportingText, color = FocusColors.TextSecondary, fontSize = 11.sp)
            }
        }
        SessionSwitch(checked)
    }
}

@Composable
private fun NestedSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingContent: (@Composable () -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(start = 42.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = FocusColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            SessionSwitch(checked)
        }
        if (supportingContent != null) {
            Box(modifier = Modifier.padding(start = 42.dp, bottom = 5.dp)) {
                supportingContent()
            }
        }
    }
}

@Composable
private fun CountLink(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = FocusColors.TextSecondary, fontSize = 11.sp)
        Icon(Icons.Rounded.ChevronRight, null, tint = FocusColors.TextMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun AppGroupCard(
    title: String,
    apps: List<InstalledAppItem>,
    blockedPackages: Set<String>,
    fallbackGroupChecked: Boolean,
    onToggleGroup: (Boolean, Set<String>) -> Unit,
    onToggleApp: (String, Boolean) -> Unit
) {
    val packages = remember(apps) { apps.mapTo(mutableSetOf()) { it.packageName } }
    val allSelected = if (packages.isEmpty()) fallbackGroupChecked else packages.all { it in blockedPackages }
    Surface(
        color = FocusColors.SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(allSelected, role = Role.Switch) { onToggleGroup(it, packages) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                SessionSwitch(allSelected)
            }
            apps.forEach { app ->
                val checked = app.packageName in blockedPackages
                AppToggleRow(app, checked) { onToggleApp(app.packageName, it) }
            }
        }
    }
}

@Composable
private fun AppToggleRow(app: InstalledAppItem, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val label = if (app.hasAppLimit) {
        stringResource(R.string.session_apps_limit_set)
    } else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = app.iconBitmap
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
        } else {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                Text(app.appName.take(1).uppercase(), color = FocusColors.TextPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.appName,
                color = FocusColors.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (label != null) Text(label, color = FocusColors.TextSecondary, fontSize = 10.sp)
        }
        SessionSwitch(checked)
    }
}

@Composable
private fun SessionSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        colors = FocusColors.SwitchColors,
        modifier = Modifier.clearAndSetSemantics { }
    )
}

@Composable
private fun ThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(FocusColors.CardBorderSubtle)
    )
}

private fun isLikelyDistracting(app: InstalledAppItem): Boolean {
    if (app.hasAppLimit) return true
    val value = "${app.appName} ${app.packageName}".lowercase()
    return DISTRACTING_TERMS.any(value::contains)
}

private val DISTRACTING_TERMS = listOf(
    "instagram", "facebook", "snapchat", "tiktok", "twitter", "reddit", "netflix",
    "hotstar", "prime video", "shopping", "flipkart", "amazon", "meesho", "game",
    "free fire", "pubg", "spotify", "music", "telegram", "discord"
)
