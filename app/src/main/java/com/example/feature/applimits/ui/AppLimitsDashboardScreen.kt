package com.example.feature.applimits.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusScreenHeader

@Composable
fun AppLimitsDashboardScreen(viewModel: AppLimitsViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saving by viewModel.isSaving.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun addLimit() { editingPackage = null; showEditor = true; viewModel.loadInstalledApps() }
    Scaffold(containerColor = FocusColors.Background,
        modifier = modifier.navigationBarsPadding().testTag("app_limits_dashboard_screen"),
        topBar = { FocusScreenHeader(stringResource(R.string.limits_title), onNavigateBack, Modifier.statusBarsPadding(),
            backButtonModifier = Modifier.testTag("app_limits_back_button")) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.xl)) {
            item { AppLimitFeedback(viewModel) }
            item { Text(stringResource(R.string.limits_local_body), color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
            item { AppLimitPermissionNotices(viewModel) }
            if (state.isLoading) item {
                CircularProgressIndicator()
                Text(stringResource(R.string.limits_loading), color = FocusColors.TextSecondary)
            }
            if (state.loadError != null) item {
                Text(state.loadError.orEmpty(), color = FocusColors.BlockedText)
                TextButton(viewModel::refresh) { Text(stringResource(R.string.action_retry)) }
            } else if (!state.isLoading && state.items.isEmpty()) item { AppLimitEmptyState(::addLimit, !saving) }
            if (state.items.isNotEmpty()) {
                item { AppLimitSummary(state) }
                item { Button(::addLimit, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp).testTag("fab_add_app_limit")) {
                    Text(stringResource(R.string.limits_add))
                } }
                items(state.items, key = { it.packageName }) { item ->
                    AppLimitCard(item, viewModel, onEdit = { editingPackage = item.packageName; showEditor = true })
                }
            }
        }
    }
    if (showEditor) AppLimitEditorHost(viewModel, editingPackage, onDismiss = { showEditor = false })
}
