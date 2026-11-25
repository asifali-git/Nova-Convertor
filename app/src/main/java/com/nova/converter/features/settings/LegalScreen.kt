package com.nova.converter.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nova.converter.R

/**
 * Legal / about screens: Privacy Policy, Terms, Open-source licenses.
 *
 * Ye teeno Play Store ke liye mandatory hain — app GPL components (FFmpeg x264, x265,
 * lame …) bundle karta hai, isliye har component ka license notice APK me bhi hona
 * chahiye. License text res/raw ke txt resources me hai (ffmpeg-kit AAR se aaya) aur
 * res/raw keep.xml me resource-shrinker se protect hai.
 */
private data class OssComponent(val title: String, val rawRes: Int)

private val OSS_COMPONENTS = listOf(
    OssComponent("ffmpeg (FFmpeg full-gpl build)", R.raw.license),
    OssComponent("cpu_features", R.raw.license_cpu_features),
    OssComponent("dav1d", R.raw.license_dav1d),
    OssComponent("expat", R.raw.license_expat),
    OssComponent("fontconfig", R.raw.license_fontconfig),
    OssComponent("freetype", R.raw.license_freetype),
    OssComponent("fribidi", R.raw.license_fribidi),
    OssComponent("harfbuzz", R.raw.license_harfbuzz),
    OssComponent("kvazaar", R.raw.license_kvazaar),
    OssComponent("lame (libmp3lame)", R.raw.license_lame),
    OssComponent("libaom", R.raw.license_libaom),
    OssComponent("libass", R.raw.license_libass),
    OssComponent("libiconv", R.raw.license_libiconv),
    OssComponent("libilbc", R.raw.license_libilbc),
    OssComponent("libogg", R.raw.license_libogg),
    OssComponent("libpng", R.raw.license_libpng),
    OssComponent("libsamplerate", R.raw.license_libsamplerate),
    OssComponent("libsndfile", R.raw.license_libsndfile),
    OssComponent("libtheora", R.raw.license_libtheora),
    OssComponent("libuuid", R.raw.license_libuuid),
    OssComponent("libvidstab", R.raw.license_libvidstab),
    OssComponent("libvorbis", R.raw.license_libvorbis),
    OssComponent("libvpx", R.raw.license_libvpx),
    OssComponent("libxml2", R.raw.license_libxml2),
    OssComponent("opencore-amr", R.raw.license_opencore_amr),
    OssComponent("openh264", R.raw.license_openh264),
    OssComponent("opus", R.raw.license_opus),
    OssComponent("rubberband", R.raw.license_rubberband),
    OssComponent("SDL", R.raw.license_sdl),
    OssComponent("shine", R.raw.license_shine),
    OssComponent("snappy", R.raw.license_snappy),
    OssComponent("soxr", R.raw.license_soxr),
    OssComponent("speex", R.raw.license_speex),
    OssComponent("twolame", R.raw.license_twolame),
    OssComponent("vo-amrwbenc", R.raw.license_vo_amrwbenc),
    OssComponent("x264 (H.264 encoder, GPL)", R.raw.license_x264),
    OssComponent("x265 (H.265/HEVC encoder, GPL)", R.raw.license_x265),
    OssComponent("Xvid core", R.raw.license_xvidcore),
    OssComponent("zimg", R.raw.license_zimg),
    OssComponent("ffmpeg-kit source (CDDL/GPL)", R.raw.source)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(kind: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val title = when (kind) {
        "privacy" -> stringResource(R.string.privacy_title)
        "terms" -> stringResource(R.string.terms_title)
        else -> stringResource(R.string.licenses_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        when (kind) {
            "privacy" -> TextScreen(Modifier.padding(pad), stringResource(R.string.privacy_body))
            "terms" -> TextScreen(Modifier.padding(pad), stringResource(R.string.terms_body))
            else -> LicenseList(Modifier.padding(pad), context)
        }
    }
}

@Composable
private fun TextScreen(modifier: Modifier, body: String) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LicenseList(modifier: Modifier, context: android.content.Context) {
    var expanded by remember { mutableStateOf(-1) }
    val cache = remember { HashMap<Int, String>() }

    LazyColumn(modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.licenses_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(OSS_COMPONENTS.withIndex().toList()) { (idx, item) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = if (expanded == idx) -1 else idx }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (expanded == idx) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded == idx) {
                    Spacer(Modifier.height(8.dp))
                    val text = cache.getOrPut(item.rawRes) {
                        context.resources.openRawResource(item.rawRes)
                            .bufferedReader().use { it.readText() }
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}
