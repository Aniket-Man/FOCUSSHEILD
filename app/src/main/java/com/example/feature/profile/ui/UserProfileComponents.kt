package com.example.feature.profile.ui

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.design.LocalFocusColors
import com.example.core.design.LocalFocusMotion
import com.example.core.ui.FocusStatusMessage
import java.io.File

data class AvatarPreset(
    val id: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

// Persisted IDs and emblem artwork are unchanged; only display labels are localized.
val AVATAR_PRESETS = listOf(
    AvatarPreset("SHIELD", R.string.profile_avatar_scholar, com.example.core.ui.FocusBrandMark,
        listOf(Color(0xFF8B5CF6), Color(0xFF6E18E6))),
    AvatarPreset("PHOENIX", R.string.profile_avatar_flame, Icons.Rounded.LocalFireDepartment,
        listOf(Color(0xFFFF8A65), Color(0xFFFF5722))),
    AvatarPreset("MEDAL", R.string.profile_avatar_champion, Icons.Rounded.EmojiEvents,
        listOf(Color(0xFFFFD54F), Color(0xFFFFA000))),
    AvatarPreset("ASTRONAUT", R.string.profile_avatar_cosmic, Icons.Rounded.RocketLaunch,
        listOf(Color(0xFF81D4FA), Color(0xFF0288D1))),
    AvatarPreset("BRAIN", R.string.profile_avatar_thinker, Icons.Rounded.Psychology,
        listOf(Color(0xFFCE93D8), Color(0xFF7B1FA2))),
    AvatarPreset("LIGHTNING", R.string.profile_avatar_velocity, Icons.Rounded.Bolt,
        listOf(Color(0xFFA5D6A7), Color(0xFF2E7D32)))
)

private fun avatarCacheKey(uri: Uri): String {
    if (uri.scheme != "file") return uri.toString()
    return "$uri#${uri.path?.let(::File)?.lastModified() ?: 0L}"
}

@Composable
fun UserProfileAvatar(
    photoUri: String?,
    avatarPresetId: String,
    size: Dp = 56.dp,
    showEditBadge: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val preset = remember(avatarPresetId) { AVATAR_PRESETS.find { it.id == avatarPresetId } ?: AVATAR_PRESETS.first() }
    val editLabel = stringResource(R.string.profile_edit)
    val motion = LocalFocusMotion.current
    val outline = if (LocalFocusColors.current.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
    Box(
        modifier = modifier.size(size).then(if (onEditClick != null) {
            Modifier.clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = editLabel, onClick = onEditClick)
                .semantics { contentDescription = editLabel }
        } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrBlank()) {
            val uri = Uri.parse(photoUri)
            val cacheKey = avatarCacheKey(uri)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(uri)
                    .memoryCacheKey(cacheKey).diskCacheKey(cacheKey).crossfade(!motion.reduceMotion).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape).border(1.dp, outline, CircleShape)
            )
        } else {
            Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(preset.gradientColors)),
                contentAlignment = Alignment.Center) {
                Icon(preset.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.52f))
            }
        }
        if (showEditBadge && onEditClick != null) {
            Box(Modifier.size(size * 0.38f).align(Alignment.BottomEnd).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary).border(1.dp, FocusColors.Surface, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhotoCamera, null, tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.22f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileBottomSheet(
    currentName: String,
    currentPhotoUri: String?,
    currentAvatarPreset: String,
    currentMotto: String,
    currentAcademicGoal: String,
    currentDailyGoalMinutes: Int,
    onDismiss: () -> Unit,
    onSaveProfile: (name: String, photoUri: String?, preset: String, motto: String, goal: String, dailyMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    saveError: String? = null,
    onDismissSaveError: () -> Unit = {}
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(currentName) }
    var photoUri by rememberSaveable { mutableStateOf(currentPhotoUri) }
    var avatarPreset by rememberSaveable { mutableStateOf(currentAvatarPreset) }
    var motto by rememberSaveable { mutableStateOf(currentMotto) }
    var academicGoal by rememberSaveable { mutableStateOf(currentAcademicGoal) }
    var dailyGoalMinutes by rememberSaveable { mutableStateOf(currentDailyGoalMinutes) }
    var pendingCropUri by rememberSaveable { mutableStateOf<String?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    val nameFocus = remember { FocusRequester() }
    val busy = saving
    val requestDismiss = { if (!busy) onDismiss() }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* The crop is copied to private storage before saving. */ }
            pendingCropUri = uri.toString()
        }
    }
    val fallbackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingCropUri = uri.toString()
    }
    fun choosePhoto() {
        try { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (exception: Exception) {
            try { fallbackPicker.launch("image/*") }
            catch (fallbackError: Exception) {
                Log.w("EditProfile", "Unable to open photo picker", fallbackError)
                feedback = context.getString(R.string.profile_photo_picker_error)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy }),
        containerColor = FocusColors.Surface,
        contentColor = FocusColors.TextPrimary,
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_edit_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = requestDismiss, enabled = !busy) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.action_close))
                }
            }
            FocusStatusMessage(saveError ?: feedback, isError = true, onDismiss = {
                if (saveError != null) onDismissSaveError()
                feedback = null
            })
            Surface(color = FocusColors.SurfaceVariant, shape = FocusShapes.card) {
                Column(Modifier.fillMaxWidth().padding(FocusSpacing.base), verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
                    UserProfileAvatar(photoUri, avatarPreset, size = 80.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(stringResource(R.string.profile_photo_hint), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                        OutlinedButton(onClick = ::choosePhoto, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("profile_upload_photo_button")) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.profile_upload_photo), Modifier.padding(start = FocusSpacing.sm))
                        }
                        if (photoUri != null) TextButton(onClick = { photoUri = null }, enabled = !busy) {
                            Text(stringResource(R.string.profile_remove_photo), color = FocusColors.BlockedText)
                        }
                    }
                    Text(stringResource(R.string.profile_avatar_options), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    // All options wrap into view. A selected chip retains a check and native state.
                    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                        AVATAR_PRESETS.forEach { preset ->
                            val selected = photoUri == null && avatarPreset == preset.id
                            FilterChip(selected = selected, onClick = { photoUri = null; avatarPreset = preset.id }, enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("profile_avatar_${preset.id}"),
                                label = { Text(stringResource(preset.labelRes)) },
                                leadingIcon = { Icon(if (selected) Icons.Rounded.Check else preset.icon, null, Modifier.size(20.dp)) })
                        }
                    }
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it; nameError = null }, enabled = !busy,
                label = { Text(stringResource(R.string.profile_name_label)) }, placeholder = { Text(stringResource(R.string.profile_name_example)) },
                isError = nameError != null, supportingText = nameError?.let { message -> ({ Text(message) }) },
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).testTag("profile_name_input"))
            OutlinedTextField(value = motto, onValueChange = { motto = it }, enabled = !busy,
                label = { Text(stringResource(R.string.profile_motto_label)) }, placeholder = { Text(stringResource(R.string.profile_motto_example)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(value = academicGoal, onValueChange = { academicGoal = it }, enabled = !busy,
                label = { Text(stringResource(R.string.profile_academic_label)) }, placeholder = { Text(stringResource(R.string.profile_academic_example)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            val duration = stringResource(R.string.profile_goal_duration, dailyGoalMinutes / 60, dailyGoalMinutes % 60)
            Text(stringResource(R.string.profile_daily_target, duration), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() })
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                (listOf(60, 120, 180, 240, 300, 360, 480) + dailyGoalMinutes).distinct().sorted().forEach { minutes ->
                    FilterChip(selected = dailyGoalMinutes == minutes, onClick = { dailyGoalMinutes = minutes }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp), label = {
                            Text(if (minutes % 60 == 0) pluralStringResource(R.plurals.profile_goal_hours, minutes / 60, minutes / 60)
                                else stringResource(R.string.profile_goal_duration, minutes / 60, minutes % 60))
                        })
                }
            }
            Button(onClick = {
                if (name.isBlank()) {
                    nameError = context.getString(R.string.profile_name_error)
                    nameFocus.requestFocus()
                } else {
                    feedback = null
                    onSaveProfile(name.trim(), photoUri, avatarPreset, motto.trim(), academicGoal.trim(), dailyGoalMinutes)
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("profile_save_button")) {
                Text(stringResource(if (busy) R.string.action_saving else R.string.profile_save))
            }
        }
    }
    pendingCropUri?.let { source ->
        ImageCropDialog(sourceUri = Uri.parse(source), onConfirm = { photoUri = it; pendingCropUri = null }, onCancel = { pendingCropUri = null })
    }
}
