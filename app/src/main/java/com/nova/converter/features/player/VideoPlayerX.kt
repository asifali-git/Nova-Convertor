package com.nova.converter.features.player

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.nova.converter.R
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

/** Gesture sink — pointerInput stable rehta hai, lambdas har composition pe refresh hote hain */
private class GestureSink {
    var onSingleTap: () -> Unit = {}
    var onDoubleTap: (Boolean) -> Unit = {}
    var onSeekStart: () -> Unit = {}
    var onSeekMove: (Float, Int, Boolean) -> Unit = { _, _, _ -> }
    var onVertStart: (Boolean) -> Unit = {}
    var onVertMove: (Boolean, Float, Int, Boolean) -> Unit = { _, _, _, _ -> }
    var onPinchStart: () -> Unit = {}
    var onPinch: (Float, Boolean) -> Unit = { _, _ -> }
}

private fun Modifier.playerGestures(sink: GestureSink): Modifier = this.then(
    Modifier.pointerInput(sink) {
        val slop = viewConfiguration.touchSlop
        var lastTapAt = 0L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            val startY = down.position.y
            var mode = 0 // 0 none, 1 seek, 2 vertical, 3 pinch
            var startDist = 0f
            var ended = false
            while (!ended) {
                val ev = awaitPointerEvent()
                val pressed = ev.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    ended = true
                    break
                }
                if (pressed.size >= 2) {
                    val dx2 = pressed[0].position.x - pressed[1].position.x
                    val dy2 = pressed[0].position.y - pressed[1].position.y
                    val d = kotlin.math.sqrt(dx2 * dx2 + dy2 * dy2)
                    if (mode != 3) {
                        mode = 3
                        startDist = d
                        sink.onPinchStart()
                    }
                    if (startDist > 1f) sink.onPinch(d / startDist, false)
                    ev.changes.forEach { it.consume() }
                    continue
                }
                val p = pressed[0].position
                val accX = p.x - startX
                val accY = p.y - startY
                when (mode) {
                    0 -> if (abs(accX) > slop || abs(accY) > slop) {
                        mode = if (abs(accX) > abs(accY)) 1 else 2
                        when (mode) {
                            1 -> sink.onSeekStart()
                            2 -> sink.onVertStart(startX < size.width / 2f)
                        }
                    }
                    1 -> {
                        sink.onSeekMove(accX, size.width, false)
                        ev.changes.forEach { it.consume() }
                    }
                    2 -> {
                        sink.onVertMove(startX < size.width / 2f, accY, size.height, false)
                        ev.changes.forEach { it.consume() }
                    }
                }
            }
            when (mode) {
                1 -> sink.onSeekMove(0f, size.width, true)
                2 -> sink.onVertMove(startX < size.width / 2f, 0f, size.height, true)
                3 -> sink.onPinch(1f, true)
                0 -> {
                    val now = System.currentTimeMillis()
                    if (now - lastTapAt < 300) {
                        sink.onDoubleTap(startX < size.width / 2f)
                        lastTapAt = 0L
                    } else {
                        lastTapAt = now
                        sink.onSingleTap()
                    }
                }
            }
        }
    }
)

/**
 * Advanced video player (MX Player style):
 * • swipe left = brightness, right = volume (live bubble)
 * • horizontal drag = seek with time bubble (live preview)
 * • double-tap left/right = ±10s
 * • pinch = zoom 1x–4x, resize cycle fit/fill/zoom
 * • speed menu, subtitles auto-load (.srt/.vtt), screen lock, resume position
 */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun VideoPlayerX(
    player: ExoPlayer,
    path: String,
    title: String,
    hasNext: Boolean,
    hasPrev: Boolean,
    shuffle: Boolean,
    repeat: Int, // 0 off, 1 all, 2 one
    onShuffle: (Boolean) -> Unit,
    onRepeat: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onOpenList: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var resizeIdx by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var subsEnabled by remember { mutableStateOf(true) }
    var subMenu by remember { mutableStateOf(false) }

    // ---- audio tracks (multi-audio videos) ----
    val audioTracks = remember(player.currentTracks) {
        player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { g ->
                (0 until g.length).map { i ->
                    val f = g.getTrackFormat(i)
                    Triple(g, i, f.label ?: f.language?.uppercase() ?: context.getString(R.string.track_n, i + 1))
                }
            }
    }
    fun selectAudio(group: androidx.media3.common.Tracks.Group, index: Int) {
        val override = TrackSelectionOverride(group.mediaTrackGroup, index)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(override)
            .build()
    }
    var subs by remember(path) { mutableStateOf(emptyList<MediaUtil.Cue>()) }
    LaunchedEffect(path) {
        subs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadSubs(path) }
    }
    var seekBubble by remember { mutableStateOf<Long?>(null) }
    var sideBubble by remember { mutableStateOf<Pair<Int, Float>?>(null) }

    // keep screen on + wake lock while playing
    DisposableEffect(player) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) interaction++
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasError = true
            }
            override fun onRenderedFirstFrame() {
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player.removeListener(listener)
        }
    }

    // auto-hide controls
    LaunchedEffect(controlsVisible, playing, interaction) {
        if (controlsVisible && playing && !locked) {
            delay(3500)
            controlsVisible = false
        }
    }

    // live position
    LaunchedEffect(player) {
        while (true) {
            delay(400)
            if (!scrubbing) {
                posMs = player.currentPosition
                durMs = player.duration.coerceAtLeast(0L)
            }
            buffering = player.playbackState == Player.STATE_BUFFERING
        }
    }

    fun showControls() {
        controlsVisible = true
        interaction++
    }

    // gesture bases (must be declared before sink lambdas capture them)
    var vertBase by remember { mutableFloatStateOf(0.5f) }
    var pinchBase by remember { mutableFloatStateOf(1f) }
    var seekStartMs by remember { mutableLongStateOf(0L) }

    fun currentBrightness(): Float {
        val act = activity ?: return 0.5f
        val cur = act.window.attributes.screenBrightness
        if (cur >= 0f) return cur
        return Settings.System.getInt(
            act.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128
        ) / 255f
    }

    val sink = remember { GestureSink() }
    sink.onSingleTap = {
        if (!locked) controlsVisible = !controlsVisible
        interaction++
    }
    sink.onDoubleTap = { left ->
        if (!locked) {
            val delta = if (left) -10_000L else 10_000L
            val target = (player.currentPosition + delta).coerceAtLeast(0L)
            player.seekTo(target)
            seekBubble = target
            showControls()
        }
    }
    sink.onSeekStart = {
        seekStartMs = player.currentPosition
        seekBubble = seekStartMs
    }
    sink.onSeekMove = { dx, width, commit ->
        if (!locked && width > 0) {
            if (commit) {
                seekBubble = null
            } else {
                val dur = player.duration.coerceAtLeast(0L)
                if (dur > 0) {
                    val t = (seekStartMs + (dx / width) * dur).toLong().coerceIn(0L, dur - 1)
                    seekBubble = t
                    if (abs(t - player.currentPosition) > 400) player.seekTo(t)
                }
            }
        }
    }
    sink.onVertStart = { isLeft ->
        vertBase = if (isLeft) currentBrightness()
        else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    sink.onVertMove = { isLeft, dy, height, commit ->
        if (!locked && height > 0) {
            if (commit) {
                sideBubble = null
            } else {
                val nv = (vertBase - dy / height).coerceIn(0f, 1f)
                if (isLeft) {
                    val b = nv.coerceAtLeast(0.02f)
                    activity?.let {
                        val attr = it.window.attributes
                        attr.screenBrightness = b
                        it.window.attributes = attr
                    }
                    sideBubble = R.string.cd_brightness to b
                } else {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC, (nv * maxVol).toInt(), 0
                    )
                    sideBubble = R.string.cd_volume to nv
                }
            }
        }
    }
    sink.onPinchStart = { pinchBase = zoom }
    sink.onPinch = { scale, commit ->
        if (!locked) {
            zoom = (pinchBase * scale).coerceIn(1f, 4f)
            if (commit && zoom < 1.08f) zoom = 1f
            if (zoom > 1f) resizeIdx = 0
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            update = { pv ->
                pv.player = player
                pv.resizeMode = when (resizeIdx) {
                    1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin.Center
                }
                .playerGestures(sink)
        )

        // subtitles
        SubtitleOverlay(player, subs, subsEnabled)

        // buffering / error
        if (buffering && !hasError) {
            CircularProgressIndicator(Modifier.size(46.dp), color = Color.White, strokeWidth = 3.5.dp)
        }
        if (hasError) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.err_cant_play),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.err_codec),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ---- bubbles ----
        seekBubble?.let { t ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    msTime(t) + " / " + msTime(durMs),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        sideBubble?.let { (labelRes, v) ->
            Box(
                Modifier
                    .align(if (labelRes == R.string.cd_brightness) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 26.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(labelRes),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        Modifier
                            .width(10.dp)
                            .height(84.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .width(10.dp)
                                .height((84 * v).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                        )
                    }
                    Text("${(v * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // auto-hide bubbles
        LaunchedEffect(sideBubble) {
            if (sideBubble != null) {
                delay(1200)
                sideBubble = null
            }
        }
        LaunchedEffect(seekBubble) {
            if (seekBubble != null) {
                delay(1500)
                seekBubble = null
            }
        }

        // ---- lock ----
        if (locked) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable {
                        locked = false
                        showControls()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.cd_unlock), tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        // ---- controls ----
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // scrims
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                            )
                        )
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(190.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )

                // top bar
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
                    }
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (subs.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { subMenu = true; interaction++ }) {
                                Icon(
                                    Icons.Filled.Subtitles,
                                    contentDescription = stringResource(R.string.cd_subtitles),
                                    tint = if (subsEnabled) Color.White else Color.White.copy(alpha = 0.45f)
                                )
                            }
                            DropdownMenu(expanded = subMenu, onDismissRequest = { subMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_show_subtitles)) },
                                    trailingIcon = {
                                        if (subsEnabled) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        subsEnabled = !subsEnabled
                                        interaction++
                                    }
                                )
                                if (audioTracks.size > 1) {
                                    audioTracks.forEach { (group, trackIdx, label) ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(stringResource(R.string.audio_track, label), Modifier.weight(1f))
                                                    if (group.isTrackSelected(trackIdx)) {
                                                        Icon(
                                                            Icons.Filled.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectAudio(group, trackIdx)
                                                interaction++
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.sub_size_label) + when (com.nova.converter.core.Settings.subSize.value) {
                                                "s" -> stringResource(R.string.sub_small)
                                                "l" -> stringResource(R.string.sub_large)
                                                else -> stringResource(R.string.sub_normal)
                                            }
                                        )
                                    },
                                    onClick = {
                                        com.nova.converter.core.Settings.setSubSize(
                                            when (com.nova.converter.core.Settings.subSize.value) {
                                                "m" -> "l"
                                                "l" -> "s"
                                                else -> "m"
                                            }
                                        )
                                        interaction++
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.sub_color_label) +
                                                if (com.nova.converter.core.Settings.subColor.value == "yellow") stringResource(R.string.color_yellow) else stringResource(R.string.color_white)
                                        )
                                    },
                                    onClick = {
                                        com.nova.converter.core.Settings.setSubColor(
                                            if (com.nova.converter.core.Settings.subColor.value == "yellow") "white" else "yellow"
                                        )
                                        interaction++
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.sub_sync_label) + if (com.nova.converter.core.Settings.subOffsetMs.value == 0L) "0.0s"
                                            else "%+.1fs".format(com.nova.converter.core.Settings.subOffsetMs.value / 1000.0)
                                        )
                                    },
                                    onClick = { interaction++ }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sub_earlier)) },
                                    onClick = {
                                        com.nova.converter.core.Settings.setSubOffset(com.nova.converter.core.Settings.subOffsetMs.value - 500)
                                        interaction++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sub_later)) },
                                    onClick = {
                                        com.nova.converter.core.Settings.setSubOffset(com.nova.converter.core.Settings.subOffsetMs.value + 500)
                                        interaction++
                                    }
                                )
                                if (com.nova.converter.core.Settings.subOffsetMs.value != 0L) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sub_reset_sync)) },
                                        onClick = {
                                            com.nova.converter.core.Settings.setSubOffset(0L)
                                            interaction++
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { speedMenu = true; interaction++ }) {
                            Icon(Icons.Filled.Speed, contentDescription = stringResource(R.string.cd_speed), tint = Color.White)
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${if (s == 1f) "1.0" else s.toString().removeSuffix("0")}x" +
                                                if (s == 1f) " (normal)" else ""
                                        )
                                    },
                                    onClick = {
                                        speed = s
                                        player.setPlaybackSpeed(s)
                                        speedMenu = false
                                        interaction++
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { onShuffle(!shuffle); interaction++ }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = stringResource(R.string.cd_shuffle),
                            tint = if (shuffle) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { onRepeat(); interaction++ }) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = stringResource(R.string.cd_repeat),
                            tint = if (repeat > 0) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { resizeIdx = (resizeIdx + 1) % 3; interaction++ }) {
                        Icon(Icons.Filled.AspectRatio, contentDescription = stringResource(R.string.cd_resize), tint = Color.White)
                    }
                    IconButton(onClick = { locked = true; interaction++ }) {
                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.cd_lock), tint = Color.White)
                    }
                    IconButton(onClick = onOpenList) {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = stringResource(R.string.queue_title), tint = Color.White)
                    }
                }

                // resize hint
                if (resizeIdx != 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            when (resizeIdx) {
                                1 -> stringResource(R.string.resize_fill)
                                else -> stringResource(R.string.resize_zoom)
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // center play/pause
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(34.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasPrev) {
                        IconButton(onClick = { onPrev(); interaction++ }) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.cd_previous), tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                    }
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        interaction++
                    }) {
                        Icon(
                            if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.cd_play_pause),
                            tint = Color.White,
                            modifier = Modifier.size(58.dp)
                        )
                    }
                    if (hasNext) {
                        IconButton(onClick = { onNext(); interaction++ }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.cd_next), tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                    }
                }

                // bottom controls
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            msTime(if (scrubbing) scrubMs else posMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = (if (scrubbing) scrubMs else posMs).toFloat(),
                            onValueChange = {
                                scrubbing = true
                                scrubMs = it.toLong()
                                interaction++
                            },
                            onValueChangeFinished = {
                                player.seekTo(scrubMs)
                                posMs = scrubMs
                                scrubbing = false
                            },
                            valueRange = 0f..durMs.toFloat().coerceAtLeast(1f),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            msTime(durMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); interaction++ }) {
                            Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.cd_back10), tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        IconButton(onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                            interaction++
                        }) {
                            Icon(
                                if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(8.dp)
                            )
                        }
                        IconButton(onClick = { player.seekTo(player.currentPosition + 10_000); interaction++ }) {
                            Icon(Icons.Filled.Forward10, contentDescription = stringResource(R.string.cd_forward10), tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Box {
                            Text(
                                "${String.format(Locale.US, "%.1f", speed)}x",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { speedMenu = true; interaction++ }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleOverlay(player: Player, cues: List<MediaUtil.Cue>, enabled: Boolean) {
    if (cues.isEmpty() || !enabled) return
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, cues) {
        while (true) {
            now = player.currentPosition
            delay(200)
        }
    }
    // user sync shift (+ve = subs late aayein)
    val offset = com.nova.converter.core.Settings.subOffsetMs.value
    val t = now - offset
    val cue = remember(t) { cues.firstOrNull { t in it.startMs until it.endMs } }
    if (cue != null) {
        val sizeSp = when (com.nova.converter.core.Settings.subSize.value) {
            "s" -> 15.sp
            "l" -> 22.sp
            else -> 18.sp
        }
        val color =
            if (com.nova.converter.core.Settings.subColor.value == "yellow") Color(0xFFFFE066)
            else Color.White
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 110.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                cue.text,
                color = color,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = sizeSp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(color = Color.Black, offset = Offset(1.5f, 1.5f), blurRadius = 6f)
                )
            )
        }
    }
}

private fun loadSubs(path: String): List<MediaUtil.Cue> {
    val subPath = MediaUtil.findSubtitle(path) ?: return emptyList()
    val text = MediaUtil.readText(subPath) ?: return emptyList()
    return MediaUtil.parseSubtitles(text)
}

internal fun msTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
