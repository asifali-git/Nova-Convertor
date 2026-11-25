package com.nova.converter.features.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.converter.R
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nova.converter.core.Settings
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

/** Audio player themes — auto = album art se color, baaki fixed presets */
private data class AudioThemeOption(val id: String, val label: Int, val color: Color?)

private val AudioThemes = listOf(
    AudioThemeOption("auto", R.string.ath_auto, null),
    AudioThemeOption("violet", R.string.ath_violet, Color(0xFF8B5CF6)),
    AudioThemeOption("ocean", R.string.ath_ocean, Color(0xFF0EA5E9)),
    AudioThemeOption("sunset", R.string.ath_sunset, Color(0xFFF97316)),
    AudioThemeOption("rose", R.string.ath_rose, Color(0xFFEC4899)),
    AudioThemeOption("neon", R.string.ath_neon, Color(0xFF39FF14)),
    AudioThemeOption("forest", R.string.ath_forest, Color(0xFF22C55E)),
    AudioThemeOption("gold", R.string.ath_gold, Color(0xFFF59E0B)),
    AudioThemeOption("mono", R.string.ath_mono, Color(0xFF94A3B8))
)

/**
 * Advanced audio player — album art se dynamic theme (background + accent), shuffle/repeat,
 * speed, sleep timer, queue drawer, seek bar with times.
 */
@Composable
@androidx.annotation.OptIn(UnstableApi::class)
fun AudioPlayerX(
    player: ExoPlayer,
    path: String,
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
    // album art / metadata IO pe — main pe retriever = open lag
    var meta by remember(path) {
        mutableStateOf(MediaUtil.Meta(File(path).nameWithoutExtension, "", "", 0L, null))
    }
    LaunchedEffect(path) {
        meta = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            MediaUtil.meta(path)
        }
    }
    val base = remember(meta.art) {
        MediaUtil.dominantColor(meta.art, Color(0xFF7A5CD6))
    }
    // user-selected audio theme (auto → album art color)
    val themeId = Settings.audioTheme.value
    val preset = remember(themeId) { AudioThemes.firstOrNull { it.id == themeId } }
    val artColor = base
    val activeBase = preset?.color ?: artColor
    val light = remember(activeBase) {
        Color((activeBase.red + 1f) / 2f, (activeBase.green + 1f) / 2f, (activeBase.blue + 1f) / 2f, 1f)
    }
    val accent = activeBase
    var themeMenu by remember { mutableStateOf(false) }
    var eqMenu by remember { mutableStateOf(false) }

    // ---- equalizer / bass boost (android.media.audiofx, session player ki) ----
    val eqPreset = Settings.eqPreset.value
    var eqFx by remember { mutableStateOf<Equalizer?>(null) }
    var bassFx by remember { mutableStateOf<BassBoost?>(null) }
    fun releaseEq() {
        try { eqFx?.release() } catch (_: Exception) {}
        try { bassFx?.release() } catch (_: Exception) {}
        eqFx = null
        bassFx = null
    }
    LaunchedEffect(eqPreset) {
        releaseEq()
        if (eqPreset == "off") return@LaunchedEffect
        // session id playback start ke baad milti hai
        var sid = player.audioSessionId
        var tries = 0
        while (sid == 0 && tries < 25) {
            delay(400)
            sid = player.audioSessionId
            tries++
        }
        if (sid == 0) return@LaunchedEffect
        try {
            if (eqPreset == "bass") {
                val bb = BassBoost(0, sid)
                bb.enabled = true
                bb.setStrength(750.toShort())
                bassFx = bb
            } else {
                val target = when (eqPreset) {
                    "pop" -> "Pop"
                    "rock" -> "Rock"
                    "jazz" -> "Jazz"
                    "classical" -> "Classical"
                    "dance" -> "Dance"
                    "vocal" -> "Vocal"
                    else -> "Flat"
                }
                val e = Equalizer(0, sid)
                e.enabled = true
                var idx = -1
                for (i in 0 until e.numberOfPresets) {
                    if (e.getPresetName(i.toShort())?.toString()?.equals(target, true) == true) {
                        idx = i
                        break
                    }
                }
                if (idx >= 0) e.usePreset(idx.toShort())
                eqFx = e
            }
        } catch (_: Exception) {
            releaseEq()
        }
    }
    DisposableEffect(player) {
        onDispose { releaseEq() }
    }

    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var sleepTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(player) {
        while (true) {
            delay(400)
            if (!scrubbing) {
                posMs = player.currentPosition
                durMs = player.duration.coerceAtLeast(0L)
            }
        }
    }

    DisposableEffect(player, path) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // sleep timer
    LaunchedEffect(sleepMin, sleepTick) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            player.pause()
            sleepMin = 0
        }
    }

    val art = meta.art
    val transition = rememberInfiniteTransition(label = "art")
    val artScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (playing) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "artScale"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        activeBase.copy(alpha = 0.92f),
                        activeBase.copy(alpha = 0.45f).copy(
                            red = activeBase.red * 0.6f,
                            green = activeBase.green * 0.6f,
                            blue = activeBase.blue * 0.6f
                        ),
                        Color(0xFF0B0B12)
                    )
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---- top bar ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.now_playing),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        meta.album.ifEmpty { stringResource(R.string.default_album) },
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onShuffle(!shuffle) }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        tint = if (shuffle) light else Color.White.copy(alpha = 0.55f)
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = onRepeat) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = stringResource(R.string.cd_repeat),
                            tint = if (repeat > 0) light else Color.White.copy(alpha = 0.55f)
                        )
                    }
                    if (repeat == 2) {
                        Text(
                            "1",
                            color = if (repeat == 2) Color.White else light,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 26.dp, top = 16.dp)
                        )
                    }
                }
                // ---- player theme picker (auto album-art + presets) ----
                Box {
                    IconButton(onClick = { themeMenu = true }) {
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = stringResource(R.string.cd_player_theme),
                            tint = if (themeId == "auto") Color.White.copy(alpha = 0.6f) else Color.White
                        )
                    }
                    DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                        AudioThemes.forEach { t ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(t.color ?: artColor)
                                        )
                                        Text(stringResource(t.label), Modifier.weight(1f))
                                        if (t.id == themeId) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    Settings.setAudioTheme(t.id)
                                    themeMenu = false
                                }
                            )
                        }
                    }
                }
                // ---- equalizer menu ----
                Box {
                    IconButton(onClick = { eqMenu = true }) {
                        Icon(
                            Icons.Filled.Equalizer,
                            contentDescription = stringResource(R.string.cd_equalizer),
                            tint = if (eqPreset == "off") Color.White.copy(alpha = 0.6f) else Color.White
                        )
                    }
                    DropdownMenu(expanded = eqMenu, onDismissRequest = { eqMenu = false }) {
                        listOf(
                            "off" to R.string.eq_off,
                            "flat" to R.string.eq_normal,
                            "pop" to R.string.eq_pop,
                            "rock" to R.string.eq_rock,
                            "jazz" to R.string.eq_jazz,
                            "classical" to R.string.eq_classical,
                            "dance" to R.string.eq_dance,
                            "vocal" to R.string.eq_vocal,
                            "bass" to R.string.eq_bass
                        ).forEach { (id, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(label), Modifier.weight(1f))
                                        if (id == eqPreset) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    Settings.setEqPreset(id)
                                    eqMenu = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onOpenList) {
                    Icon(
                        Icons.Filled.PlaylistPlay,
                        contentDescription = stringResource(R.string.queue_title),
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- art ----
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 44.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    }
                    .shadow(18.dp, RoundedCornerShape(32.dp), clip = false)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF15131E)),
                contentAlignment = Alignment.Center
            ) {
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = light.copy(alpha = 0.8f),
                            modifier = Modifier.size(74.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.audio_brand),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ---- title ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    meta.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    meta.artist,
                    color = light,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- seek ----
            Column(Modifier.padding(horizontal = 20.dp)) {
                Slider(
                    value = (if (scrubbing) scrubMs else posMs).toFloat(),
                    onValueChange = {
                        scrubbing = true
                        scrubMs = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(scrubMs)
                        posMs = scrubMs
                        scrubbing = false
                    },
                    valueRange = 0f..durMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        msTime(if (scrubbing) scrubMs else posMs),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        msTime(durMs),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- transport ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPrev) {
                    IconButton(onClick = onPrev) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.cd_previous),
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                }) {
                    Icon(
                        if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = activeBase,
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(14.dp, CircleShape, clip = false)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(16.dp)
                    )
                }
                if (hasNext) {
                    IconButton(onClick = onNext) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.cd_next),
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- speed + sleep ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Chip(
                    label = "%.2fx".format(Locale.US, speed),
                    accent = accent,
                    onClick = {
                        val next = when (speed) {
                            1f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2f
                            2f -> 0.75f
                            0.75f -> 1f
                            else -> 1f
                        }
                        speed = next
                        player.setPlaybackSpeed(next)
                    }
                )
                Chip(
                    label = if (sleepMin == 0) stringResource(R.string.sleep) else stringResource(R.string.sleep_min, sleepMin),
                    accent = accent,
                    onClick = {
                        sleepMin = when (sleepMin) {
                            0 -> 15
                            15 -> 30
                            30 -> 45
                            45 -> 60
                            else -> 0
                        }
                        sleepTick++
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun Chip(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
