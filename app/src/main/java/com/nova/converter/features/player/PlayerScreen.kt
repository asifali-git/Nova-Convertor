package com.nova.converter.features.player

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nova.converter.R
import com.nova.converter.core.Files
import com.nova.converter.ui.FileField
import com.nova.converter.ui.NovaButton
import java.io.File
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Queue/shuffle/repeat — PlayerScreen rakhta hai, dono players use karte hain */
private class QueueState {
    var items by mutableStateOf(emptyList<String>())
    var index by mutableIntStateOf(0)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(0) // 0 off, 1 all, 2 one

    val current: String? get() = items.getOrNull(index)

    fun next(): Boolean {
        val n = items.size
        if (n == 0) return false
        if (shuffle && n > 1) {
            var i = index
            while (i == index) i = Random.nextInt(n)
            index = i
            return true
        }
        if (index < n - 1) {
            index++
            return true
        }
        if (repeat == 1 || repeat == 2) {
            index = 0
            return true
        }
        return false
    }

    fun prev(): Boolean {
        val n = items.size
        if (n == 0) return false
        if (index > 0) {
            index--
            return true
        }
        if (repeat == 1) {
            index = n - 1
            return true
        }
        return false
    }
}

/**
 * Advanced player hub (MX Player style):
 * • video mode → VideoPlayerX (gestures, zoom, subtitles, speed, lock, resume)
 * • audio mode → AudioPlayerX (dynamic album-art theme, sleep timer)
 * • image mode → pinch/double-tap zoom viewer
 * • folder queue: prev/next across sibling files, shuffle, repeat, auto-next on end
 */
@Composable
fun PlayerScreen(
    snackbar: SnackbarHostState,
    initialPath: String? = null,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("") } // video | audio | image | "" (picker)
    var currentPath by remember { mutableStateOf<String?>(null) }
    var pickerUri by remember { mutableStateOf<Uri?>(null) }
    var pickerName by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    val qs = remember { QueueState() }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            shuffleModeEnabled = false
        }
    }

    fun savePos() {
        val p = currentPath ?: return
        if (mode != "video" && mode != "audio") return
        if (player.playbackState == Player.STATE_ENDED) return
        MediaUtil.savePos(context, p, player.currentPosition)
    }

    fun load(p: String, autoplay: Boolean = true) {
        savePos()
        currentPath = p
        if (mode == "video" || mode == "audio") {
            val start = MediaUtil.loadPos(context, p)
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(p))))
            player.prepare()
            if (start > 0) player.seekTo(start)
            player.playWhenReady = autoplay
        }
    }

    fun detectMode(path: String): String = when {
        MediaUtil.isImageFile(path) -> "image"
        MediaUtil.isVideoFile(path) -> "video"
        MediaUtil.isAudioFile(path) -> "audio"
        MediaUtil.hasVideoTrack(path) -> "video"
        else -> "audio"
    }

    suspend fun buildQueue(path: String) {
        // metadata probe IO pe — main thread pe retriever chalane se open hang hota tha
        val (m, items) = withContext(Dispatchers.IO) {
            detectMode(path) to (listOf(path) + MediaUtil.siblingMedia(path)).distinct()
        }
        mode = m
        qs.items = items
        qs.index = 0
    }

    fun goNext() {
        if (qs.next()) {
            load(qs.current!!)
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.end_of_queue)) }
        }
    }

    fun goPrev() {
        if (mode == "video" && player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }
        if (qs.prev()) load(qs.current!!)
    }

    fun selectIndex(i: Int) {
        qs.index = i
        load(qs.items[i])
        drawerOpen = false
    }

    // auto-next / repeat-one when playback ends + save resume position
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val finished = currentPath
                    if (finished != null) MediaUtil.clearPos(context, finished)
                    if (qs.repeat == 2) {
                        player.seekTo(0)
                        player.play()
                    } else if (!qs.next()) {
                        if (qs.repeat == 1 && qs.items.isNotEmpty()) {
                            qs.index = 0
                            load(qs.current!!)
                        }
                    } else {
                        load(qs.current!!)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePos()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            savePos()
            player.release()
        }
    }

    // direct open (result card / history se)
    LaunchedEffect(initialPath) {
        if (initialPath != null && currentPath == null) {
            buildQueue(initialPath)
            load(initialPath)
        }
    }

    BackHandler(enabled = drawerOpen || onClose != null) {
        if (drawerOpen) drawerOpen = false else onClose?.invoke()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            mode == "video" && currentPath != null -> VideoPlayerX(
                player = player,
                path = currentPath!!,
                title = File(currentPath!!).name,
                hasNext = qs.items.size > 1,
                hasPrev = qs.items.size > 1,
                shuffle = qs.shuffle,
                repeat = qs.repeat,
                onShuffle = { qs.shuffle = it },
                onRepeat = { qs.repeat = (qs.repeat + 1) % 3 },
                onPrev = ::goPrev,
                onNext = ::goNext,
                onBack = { onClose?.invoke() },
                onOpenList = { drawerOpen = true }
            )

            mode == "audio" && currentPath != null -> AudioPlayerX(
                player = player,
                path = currentPath!!,
                hasNext = qs.items.size > 1,
                hasPrev = qs.items.size > 1,
                shuffle = qs.shuffle,
                repeat = qs.repeat,
                onShuffle = { qs.shuffle = it },
                onRepeat = { qs.repeat = (qs.repeat + 1) % 3 },
                onPrev = ::goPrev,
                onNext = ::goNext,
                onBack = { onClose?.invoke() },
                onOpenList = { drawerOpen = true }
            )

            mode == "image" && currentPath != null -> ImageViewer(
                path = currentPath!!,
                hasNext = qs.items.size > 1,
                hasPrev = qs.items.size > 1,
                onPrev = ::goPrev,
                onNext = ::goNext,
                onBack = { onClose?.invoke() },
                onOpenList = { drawerOpen = true }
            )

            initialPath != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            else -> PickerUi(
                pickerName = pickerName,
                onPick = { u, n ->
                    pickerUri = u
                    pickerName = n
                },
                onOpen = {
                    val u = pickerUri
                    if (u != null) {
                        scope.launch {
                            val p = withContext(Dispatchers.IO) { Files.copyToCache(context, u) }
                            if (p != null) {
                                buildQueue(p.absolutePath)
                                load(p.absolutePath)
                            } else {
                                snackbar.showSnackbar(context.getString(R.string.load_failed))
                            }
                        }
                    }
                }
            )
        }

        // ---------- queue drawer ----------
        if (drawerOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { drawerOpen = false }
            )
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(tween(220)) { it },
                exit = slideOutHorizontally(tween(200)) { it },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            ) {
                QueuePanel(
                    items = qs.items,
                    currentIndex = qs.index,
                    onSelect = ::selectIndex,
                    onClose = { drawerOpen = false }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- queue panel

@Composable
private fun QueuePanel(
    items: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Queue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.files_count, items.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.btn_close),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(items) { i, path ->
                val selected = i == currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            File(path).name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            MediaUtil.fileSizeLabel(File(path).length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- image viewer

@Composable
private fun ImageViewer(
    path: String,
    hasNext: Boolean,
    hasPrev: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenList: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var controls by remember { mutableStateOf(true) }
    // GIF ko BitmapFactory se padhne se sirf pehla frame milta hai — user ko
    // "static photo" dikhta hai. GIF alag animated path se chalta hai.
    val isGif = MediaUtil.ext(path) == "gif"
    val bmp = remember(path) { if (isGif) null else loadSampledBitmap(path, 2200) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                    scale = newScale
                    if (newScale <= 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            }
            .clickable { controls = !controls }
    ) {
        if (isGif) {
            GifView(scale = scale, offsetX = offsetX, offsetY = offsetY, path = path)
        } else if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.err_cant_open_image), color = Color.White.copy(alpha = 0.7f))
            }
        }

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.cd_back),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        File(path).name,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                    if (hasNext) {
                        Text(
                            stringResource(R.string.btn_next),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onNext)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    } else {
                        Text("  ", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (hasPrev || hasNext) {
                    Row(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (hasPrev) {
                            Text(
                                stringResource(R.string.btn_prev),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .clickable(onClick = onPrev)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        if (hasNext) {
                            Text(
                                stringResource(R.string.btn_next),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .clickable(onClick = onNext)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.gesture_hint),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 26.dp)
                )
                Text(
                    stringResource(R.string.queue_title),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onOpenList)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/**
 * Animated GIF — `android.graphics.Movie` se frame-by-frame.
 *
 * `BitmapFactory` GIF ka sirf pehla frame decode karta hai, isliye pehle
 * Video→GIF ka output app me "static photo" dikhta tha. Movie API 26+ pe
 * available hai aur loop/frame-timing khud handle karta hai.
 *
 * Frame counter ko sirf draw phase me padhte hain — isliye har frame pe
 * sirf redraw hota hai, poora composable recompose nahi hota.
 */
@Composable
private fun GifView(path: String, scale: Float, offsetX: Float, offsetY: Float) {
    val movie = remember(path) { runCatching { android.graphics.Movie.decodeFile(path) }.getOrNull() }
    val frame = remember(path) { androidx.compose.runtime.mutableIntStateOf(0) }
    val duration = remember(movie) { movie?.duration() ?: 0 }

    LaunchedEffect(movie, duration) {
        if (movie == null || duration <= 0) return@LaunchedEffect
        while (true) {
            androidx.compose.runtime.withFrameMillis { frame.intValue = frame.intValue + 1 }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
    ) {
        val m = movie ?: return@Canvas
        val mw = m.width()
        val mh = m.height()
        if (mw <= 0 || mh <= 0) return@Canvas
        if (duration > 0) m.setTime((frame.intValue.toLong() * 40L % duration).toInt())
        val fit = minOf(size.width / mw, size.height / mh)
        val w = mw * fit
        val h = mh * fit
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        drawIntoCanvas { canvas ->
            // Movie.draw sirf (canvas, x, y) leta hai — scale/offset canvas
            // transform se karte hain (Compose ka DrawScope transform nahi chahiye).
            val native = canvas.nativeCanvas
            native.save()
            native.translate(left, top)
            native.scale(fit, fit)
            m.draw(native, 0f, 0f)
            native.restore()
        }
    }
}

// ---------------------------------------------------------------- picker (route "player")

@Composable
private fun PickerUi(
    pickerName: String,
    onPick: (Uri, String) -> Unit,
    onOpen: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.player_brand), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.player_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        FileField(
            stringResource(R.string.btn_choose_file),
            arrayOf("video/*", "audio/*", "image/*"),
            pickerName
        ) { u, n -> onPick(u, n) }
        if (pickerName.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            NovaButton(stringResource(R.string.btn_open)) { onOpen() }
        }
    }
}

private fun loadSampledBitmap(path: String, maxPx: Int): android.graphics.Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxPx) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (_: Exception) {
    null
}
