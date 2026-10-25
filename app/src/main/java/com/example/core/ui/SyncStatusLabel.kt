package com.example.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.R
import com.example.cloud.sync.SyncWorker
import com.example.cloud.sync.SyncWorkerStatus
import kotlinx.coroutines.delay

/**
 * Stateful Composable that observes background synchronization status from [SyncWorker] and
 * [WorkManager], rendering a live `'Last synced: X minutes ago'` label and optional manual sync action.
 */
@Composable
fun SyncStatusLabel(
    modifier: Modifier = Modifier,
    showRefreshButton: Boolean = true,
    onSyncNow: (() -> Unit)? = null
) {
    val context = LocalContext.current

    LaunchedEffect(context) {
        SyncWorker.init(context)
    }

    val workerStatus by SyncWorker.syncStatus.collectAsStateWithLifecycle()
    var workInfos by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }

    DisposableEffect(context) {
        val liveData = runCatching {
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWorkLiveData(SyncWorker.UNIQUE_PERIODIC_WORK_NAME)
        }.getOrNull()

        val observer = Observer<List<WorkInfo>> { infos ->
            workInfos = infos ?: emptyList()
        }

        runCatching { liveData?.observeForever(observer) }

        onDispose {
            runCatching { liveData?.removeObserver(observer) }
        }
    }

    val isWorkManagerRunning = workInfos.any { it.state == WorkInfo.State.RUNNING }
    val latestOutputTimestamp = workInfos
        .map { it.outputData.getLong(SyncWorker.KEY_LAST_SYNC_TIMESTAMP, 0L) }
        .maxOrNull() ?: 0L

    val effectiveStatus = SyncWorkerStatus(
        isSyncing = workerStatus.isSyncing || isWorkManagerRunning,
        lastSyncedAtMillis = maxOf(workerStatus.lastSyncedAtMillis, latestOutputTimestamp),
        lastErrorMessage = workerStatus.lastErrorMessage
    )

    SyncStatusContent(
        status = effectiveStatus,
        modifier = modifier,
        showRefreshButton = showRefreshButton,
        onSyncNow = {
            if (onSyncNow != null) {
                onSyncNow()
            } else {
                SyncWorker.enqueueImmediateSync(context)
            }
        }
    )
}

/**
 * Stateless Composable displaying the current [SyncWorkerStatus] with a live-updating
 * `'Last synced: X minutes ago'` label.
 */
@Composable
fun SyncStatusContent(
    status: SyncWorkerStatus,
    modifier: Modifier = Modifier,
    showRefreshButton: Boolean = true,
    onSyncNow: () -> Unit = {}
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(status.lastSyncedAtMillis, status.isSyncing) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val labelText = if (status.isSyncing) {
        stringResource(id = R.string.sync_status_syncing)
    } else {
        SyncWorker.formatLastSyncedLabel(
            lastSyncedAtMillis = status.lastSyncedAtMillis,
            nowMillis = nowMillis
        )
    }

    val indicatorColor = when {
        status.isSyncing -> MaterialTheme.colorScheme.primary
        status.lastErrorMessage != null -> MaterialTheme.colorScheme.error
        status.lastSyncedAtMillis > 0L -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_status_component"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .testTag("sync_status_progress"),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                            .testTag("sync_status_dot")
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    AnimatedContent(
                        targetState = labelText,
                        transitionSpec = {
                            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                        },
                        label = "last_synced_label_transition"
                    ) { targetLabel ->
                        Text(
                            text = targetLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("last_synced_label")
                        )
                    }

                    if (!status.lastErrorMessage.isNullOrBlank() && !status.isSyncing) {
                        Text(
                            text = status.lastErrorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("sync_status_error_label")
                        )
                    }
                }
            }

            if (showRefreshButton) {
                IconButton(
                    onClick = onSyncNow,
                    enabled = !status.isSyncing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sync_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = stringResource(id = R.string.sync_status_refresh_description),
                        tint = if (status.isSyncing) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}
