package com.nova.converter.features.queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.converter.R
import androidx.compose.ui.res.stringResource
import com.nova.converter.core.Outputs
import com.nova.converter.core.Queue
import com.nova.converter.ui.ConfirmDialog
import kotlinx.coroutines.launch
import java.io.File

/**
 * Queue — SEQUENTIAL jobs (ek time pe ek hi job chalti hai).
 * Jobs queue mein rehti hain — pehle wali complete hone ke baad agli start hoti hai.
 * UI mein queue position dikhati hai (#1, #2, #3...).
 */
@Composable
fun QueueScreen(snackbar: SnackbarHostState, onBrowse: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by Queue.tasks.collectAsState()

    var minimized by remember { mutableStateOf(setOf<Int>()) }
    var confirmCancel by remember { mutableIntStateOf(-1) }
    
    // Separate running, queued, done for better UI
    val runningTask = tasks.find { it.state == Queue.State.RUNNING }
    val queuedTasks = tasks.filter { it.state == Queue.State.QUEUED }
    val doneTasks = tasks.filter { it.state == Queue.State.DONE }
    val failedTasks = tasks.filter { it.state == Queue.State.FAILED }
    val cancelledTasks = tasks.filter { it.state == Queue.State.CANCELLED }
    val hasMinimized = minimized.isNotEmpty()

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        // Header with queue status
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (runningTask != null || queuedTasks.isNotEmpty()) {
                    OutlinedButton(onClick = { minimized = minimized + (if (runningTask != null) listOf(runningTask.id) else emptyList()) + queuedTasks.map { it.id } }) {
                        Text(stringResource(R.string.queue_min_all))
                    }
                }
                if (hasMinimized) {
                    OutlinedButton(onClick = { minimized = emptySet() }) { Text(stringResource(R.string.queue_expand_all)) }
                }
                if (doneTasks.isNotEmpty() || failedTasks.isNotEmpty() || cancelledTasks.isNotEmpty()) {
                    OutlinedButton(onClick = { Queue.clearFinished() }) { Text(stringResource(R.string.queue_clear_done)) }
                }
            }
        }
        
        // Status line
        val statusText = when {
            runningTask != null -> stringResource(R.string.queue_status_running, runningTask.name)
            queuedTasks.isNotEmpty() -> stringResource(R.string.queue_status_waiting, queuedTasks.size)
            else -> stringResource(R.string.queue_hint)
        }
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    stringResource(R.string.queue_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.queue_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBrowse) { Text(stringResource(R.string.queue_browse)) }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Running task first (if any)
            runningTask?.let { task ->
                val isMin = task.id in minimized
                item(key = task.id) {
                    AnimatedVisibility(visible = isMin) {
                        MinimizedJobCard(task = task, onExpand = { minimized = minimized - task.id }, onCancel = { confirmCancel = task.id })
                    }
                    AnimatedVisibility(visible = !isMin) {
                        JobCard(task = task, onMinimize = { minimized = minimized + task.id }, onCancel = { confirmCancel = task.id }, onSaved = { msg -> scope.launch { snackbar.showSnackbar(msg) } }, onShowError = { msg -> scope.launch { snackbar.showSnackbar(msg.take(120)) } }, context = context)
                    }
                }
            }
            
            // Queued tasks with position numbers
            queuedTasks.forEachIndexed { index, task ->
                val pos = index + 1
                val isMin = task.id in minimized
                item(key = task.id) {
                    AnimatedVisibility(visible = isMin) {
                        MinimizedJobCard(task = task, onExpand = { minimized = minimized - task.id }, onCancel = { confirmCancel = task.id }, queuePosition = pos)
                    }
                    AnimatedVisibility(visible = !isMin) {
                        JobCard(task = task, onMinimize = { minimized = minimized + task.id }, onCancel = { confirmCancel = task.id }, onSaved = { msg -> scope.launch { snackbar.showSnackbar(msg) } }, onShowError = { msg -> scope.launch { snackbar.showSnackbar(msg.take(120)) } }, context = context, queuePosition = pos)
                    }
                }
            }
            
            // Done/failed/cancelled
            (doneTasks + failedTasks + cancelledTasks).forEach { task ->
                val isMin = task.id in minimized
                item(key = task.id) {
                    AnimatedVisibility(visible = isMin) {
                        MinimizedJobCard(task = task, onExpand = { minimized = minimized - task.id }, onCancel = { confirmCancel = task.id })
                    }
                    AnimatedVisibility(visible = !isMin) {
                        JobCard(task = task, onMinimize = { minimized = minimized + task.id }, onCancel = { confirmCancel = task.id }, onSaved = { msg -> scope.launch { snackbar.showSnackbar(msg) } }, onShowError = { msg -> scope.launch { snackbar.showSnackbar(msg.take(120)) } }, context = context)
                    }
                }
            }
        }

        if (confirmCancel in tasks.map { it.id }) {
            ConfirmDialog(
                title = stringResource(R.string.cancel_job_title),
                message = stringResource(R.string.cancel_job_msg),
                confirmLabel = stringResource(R.string.cancel_job_yes),
                onConfirm = { Queue.cancel(confirmCancel) },
                onDismiss = { confirmCancel = -1 }
            )
        }
    }
}

/** Minimized (compact) job — naam + live % + expand. Job chalti rehti hai. */
@Composable
private fun MinimizedJobCard(
    task: Queue.Task,
    onExpand: () -> Unit,
    onCancel: () -> Unit,
    queuePosition: Int? = null
) {
    val active = task.state == Queue.State.RUNNING || task.state == Queue.State.QUEUED
    val positionLabel = queuePosition?.let { "#$it" } ?: ""
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StateIcon(task.state)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (positionLabel.isNotBlank()) {
                        Text(
                            positionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        task.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (active) {
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Text(
                        stateLabel(task.state),
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor(task.state)
                    )
                }
            }
            Text(
                if (task.state == Queue.State.QUEUED) "Waiting" else "${task.progress.toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (task.state == Queue.State.QUEUED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
            if (active) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // Retry button for failed/cancelled in minimized view
            if (task.state == Queue.State.FAILED || task.state == Queue.State.CANCELLED) {
                IconButton(onClick = { Queue.retry(task.id)?.let { /* retry started */ } }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.btn_retry),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.OpenInFull,
                    contentDescription = stringResource(R.string.cd_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Full job card — saari detail + Minimize button */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun JobCard(
    task: Queue.Task,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
    onSaved: (String) -> Unit,
    onShowError: (String) -> Unit,
    context: android.content.Context,
    queuePosition: Int? = null
) {
    val positionLabel = queuePosition?.let { "#$it" } ?: ""
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (positionLabel.isNotBlank()) {
                    Text(
                        positionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    task.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val activeJob = task.state == Queue.State.RUNNING || task.state == Queue.State.QUEUED
                if (activeJob) {
                    Text(
                        if (task.state == Queue.State.QUEUED) "Waiting" else "${task.progress.toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (task.state == Queue.State.QUEUED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    stateLabel(task.state),
                    style = MaterialTheme.typography.labelSmall,
                    color = stateColor(task.state)
                )
                if (activeJob) {
                    IconButton(onClick = onMinimize, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Minimize,
                            contentDescription = stringResource(R.string.cd_minimize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (task.state == Queue.State.RUNNING || task.state == Queue.State.QUEUED) {
                LinearProgressIndicator(
                    progress = { if (task.state == Queue.State.QUEUED) 0f else task.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // ---- Stats display for running/completed jobs ----
            if (task.state == Queue.State.RUNNING || task.state == Queue.State.DONE) {
                val stats = task.procStats
                val result = task.resultStats
                if (stats != null || result != null) {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // FlowRow: Row me ek-ek character karke vertical wrap hota tha
                        // (bahut lambi khali card space). Ab natural width me lines wrap.
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            stats?.let { s ->
                                Text(
                                    stringResource(R.string.queue_stats_cpu, s.cpuPercent),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(R.string.queue_stats_ram, s.ramUsedMb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(R.string.queue_stats_speed, s.speedStr),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(R.string.queue_stats_eta, s.etaStr),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            result?.let { r ->
                                Text(
                                    stringResource(R.string.queue_stats_input, r.inputStats.sizeStr),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(R.string.queue_stats_output, r.outputStats?.sizeStr ?: "—"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(R.string.queue_stats_ratio, r.compressionRatioStr),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (task.message.isNotBlank() && task.state != Queue.State.QUEUED) {
                Text(task.message, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.state == Queue.State.QUEUED || task.state == Queue.State.RUNNING) {
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.btn_cancel)) }
                }
                if (task.state == Queue.State.DONE) {
                    OutlinedButton(onClick = {
                        val saved = Outputs.save(
                            context,
                            File(task.output),
                            task.mimeType,
                            File(task.output).name
                        )
                        onSaved(if (saved != null) context.getString(R.string.saved_ok) else context.getString(R.string.saved_fail))
                    }) { Text(stringResource(R.string.btn_save)) }
                    OutlinedButton(onClick = {
                        com.nova.converter.features.player.PlayerController.open(
                            task.output, task.mimeType
                        )
                    }) { Text(stringResource(R.string.btn_open)) }
                    OutlinedButton(onClick = {
                        val res = com.nova.converter.core.FileActions.openFolder(context, task.mimeType)
                        if (res == com.nova.converter.core.FileActions.FolderOpen.COPIED) {
                            onSaved(context.getString(R.string.folder_path_copied))
                        }
                    }) { Text(stringResource(R.string.btn_open_folder)) }
                }
                if (task.state == Queue.State.FAILED || task.state == Queue.State.CANCELLED) {
                    OutlinedButton(onClick = { onShowError(task.message) }) { Text(stringResource(R.string.btn_why)) }
                    OutlinedButton(onClick = { Queue.retry(task.id)?.let { onSaved(context.getString(R.string.queue_retry_started)) } }) {
                        Text(stringResource(R.string.btn_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun StateIcon(state: Queue.State) {
    val (icon, tint) = when (state) {
        Queue.State.DONE -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        Queue.State.FAILED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        Queue.State.CANCELLED -> Icons.Filled.Close to MaterialTheme.colorScheme.onSurfaceVariant
        Queue.State.RUNNING -> Icons.Filled.Sync to MaterialTheme.colorScheme.tertiary
        Queue.State.QUEUED -> Icons.Filled.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, contentDescription = stateLabel(state), tint = tint, modifier = Modifier.size(20.dp))
}

@Composable
private fun stateColor(state: Queue.State) = when (state) {
    Queue.State.DONE -> MaterialTheme.colorScheme.primary
    Queue.State.FAILED -> MaterialTheme.colorScheme.error
    Queue.State.RUNNING -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Queue state → localized label (enum name raw mat dikhao) */
@Composable
private fun stateLabel(state: Queue.State): String = stringResource(
    when (state) {
        Queue.State.DONE -> R.string.state_done
        Queue.State.FAILED -> R.string.state_failed
        Queue.State.CANCELLED -> R.string.state_cancelled
        Queue.State.RUNNING -> R.string.state_running
        Queue.State.QUEUED -> R.string.state_queued
    }
)