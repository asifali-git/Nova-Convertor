package com.nova.converter.features.history

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nova.converter.R
import androidx.compose.ui.res.stringResource
import com.nova.converter.core.History
import com.nova.converter.core.HistoryItem
import com.nova.converter.features.player.PlayerController
import com.nova.converter.ui.ConfirmDialog
import java.io.File
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/**
 * History — har finished/failed job ka record. Date/status filter, play/share/delete,
 * clear-all (confirm ke saath). File delete ho gayi ho to "File missing" dikhta hai.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var filter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HistoryItem?>(null) }

    fun refresh() {
        items = History.all(context)
    }
    LaunchedEffect(Unit) { refresh() }

    val filtered = remember(items, filter, query) {
        val base = when (filter) {
            "today" -> items.filter { it.timeMs >= startOfToday() }
            "week" -> items.filter { it.timeMs >= System.currentTimeMillis() - 7L * 86_400_000L }
            "done" -> items.filter { it.status == "done" }
            "failed" -> items.filter { it.status == "failed" }
            "fav" -> items.filter { it.favorite }
            else -> items
        }
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) base
        else base.filter {
            it.fileName.lowercase(Locale.ROOT).contains(q) ||
                it.verb.lowercase(Locale.ROOT).contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
            actions = {
                if (items.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.btn_clear_all), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // filters
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipX(stringResource(R.string.filter_all), filter == "all") { filter = "all" }
                FilterChipX(stringResource(R.string.filter_today), filter == "today") { filter = "today" }
                FilterChipX(stringResource(R.string.filter_7days), filter == "week") { filter = "week" }
                FilterChipX(stringResource(R.string.filter_done), filter == "done") { filter = "done" }
                FilterChipX(stringResource(R.string.filter_failed), filter == "failed") { filter = "failed" }
                FilterChipX(stringResource(R.string.filter_favorites), filter == "fav") { filter = "fav" }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                placeholder = {
                    Text(stringResource(R.string.history_search_hint))
                },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(10.dp))

            if (filtered.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (items.isEmpty()) stringResource(R.string.history_empty_none) else stringResource(R.string.history_empty_filter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.history_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        HistoryRow(
                            item = item,
                            onPlay = {
                                PlayerController.open(item.path, item.mime)
                            },
                            onOpenFolder = {
                                val res = com.nova.converter.core.FileActions.openFolder(context, item.mime)
                                // Har route fail ho to path clipboard me chala gaya —
                                // user ko batao, warna wo sochega button hi dead hai.
                                if (res == com.nova.converter.core.FileActions.FolderOpen.COPIED) {
                                    scope.launch {
                                        snackbar.showSnackbar(context.getString(R.string.folder_path_copied))
                                    }
                                }
                            },
                            onShare = { shareItem(context, item) },
                            onDelete = { pendingDelete = item },
                            onToggleFav = {
                                History.toggleFavorite(context, item.id)
                                refresh()
                            }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_title),
            message = stringResource(R.string.history_clear_msg, items.size),
            confirmLabel = stringResource(R.string.btn_clear_all),
            onConfirm = {
                History.clear(context)
                refresh()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            message = item.fileName,
            confirmLabel = stringResource(R.string.btn_delete),
            onConfirm = {
                History.remove(context, item.id)
                refresh()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun FilterChipX(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onToggleFav: () -> Unit,
    onOpenFolder: () -> Unit
) {
    val exists = remember(item.path) {
        try {
            File(item.path).exists() && File(item.path).length() > 0
        } catch (_: Exception) {
            false
        }
    }
    val isAudio = item.mime.startsWith("audio")
    val isVideo = item.mime.startsWith("video")
    val isImage = item.mime.startsWith("image")
    val failed = item.status == "failed"

    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            isAudio -> Icons.Filled.MusicNote
                            isVideo -> Icons.Filled.Movie
                            isImage -> Icons.Filled.Image
                            else -> Icons.Filled.History
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (failed) stringResource(R.string.history_task_failed) else item.verb,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (failed) {
                            Text(
                                stringResource(R.string.history_status_failed),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        item.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(formatTime(item.timeMs))
                            if (item.size > 0) append("  •  ").append(formatSizeH(item.size))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- FILE LOCATION ----------
            // "Open" button ki jagah: file KAHAN hai yeh saaf dikhao aur
            // ek tap se wahi folder khol do. Naya user yahin se file dhoondta hai.
            if (!failed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenFolder() }
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = stringResource(R.string.history_path_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        com.nova.converter.core.FileActions.locationOf(item.fileName, item.mime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when {
                failed -> Text(
                    item.message.ifBlank { stringResource(R.string.history_job_not_finished) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                !exists -> Text(
                    stringResource(R.string.history_file_missing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!failed && exists) {
                    // Video/audio → Play, image/GIF → View. Dono built-in
                    // player me hi khulte hain, app se bahar nahi jaate.
                    OutlinedButton(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            if (isAudio || isVideo) Icons.Filled.PlayArrow else Icons.Filled.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(
                                if (isAudio || isVideo) R.string.btn_play else R.string.cd_play_media
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenFolder,
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.btn_open_folder),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Share + favourite + delete — alag row, taaki upar wale do
            // buttons text truncate na ho (Hindi/Urdu/Arabic me lamba hota hai)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!failed && exists) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.btn_share),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onToggleFav, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (item.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (item.favorite) stringResource(R.string.cd_remove_fav) else stringResource(R.string.cd_add_fav),
                            tint = if (item.favorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.cd_delete_record),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(ms))

private fun formatSizeH(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun startOfToday(): Long {
    val c = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

private fun shareItem(context: Context, item: HistoryItem) {
    try {
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", File(item.path)
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mime.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_via)))
    } catch (_: Exception) {
    }
}
