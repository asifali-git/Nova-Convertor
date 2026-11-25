package com.nova.converter.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nova.converter.R
import com.nova.converter.core.FileActions
import com.nova.converter.core.History
import com.nova.converter.core.Settings
import com.nova.converter.ui.ConfirmDialog
import com.nova.converter.ui.SectionCard
import com.nova.converter.ui.theme.NovaThemes
import kotlinx.coroutines.launch

/**
 * Settings — 10 themes (instant apply + persistent), app language (10),
 * notification sound toggle, storage cleanup, about + legal
 * (privacy / terms / OSS licenses).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(snackbar: SnackbarHostState, onOpenLegal: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmClearHistory by remember { mutableStateOf(false) }
    var langPicker by remember { mutableStateOf(false) }
    var themePicker by remember { mutableStateOf(false) }
    val currentTheme = NovaThemes.firstOrNull { it.id == Settings.themeId.value } ?: NovaThemes.first()
    val langNames = Settings.supportedLanguages.map { code ->
        stringResourceForLang(code)
    }
    val currentLangName = langNames.getOrElse(
        Settings.supportedLanguages.indexOf(Settings.language.value)
    ) { langNames.first() }
    // JSON parse sirf ek baar (har recompose pe nahi)
    var historyCount by remember { mutableStateOf(History.all(context).size) }
    val versionLabel = remember(context) {
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            "${pi.versionName} ($code)"
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Output folder kholne ka ek hi entry-point. [FileActions.openFolder]
     * 5-step fallback chain chalata hai aur batata hai ki kya hua — hum sirf
     * tab message dikhate hain jab koi bhi tarika fail hokar path clipboard
     * me chala gaya. Baaki cases me file manager khud khulta hai, user ko
     * koi message ki zaroorat nahi.
     */
    val openOutputFolder: (String) -> Unit = { mime ->
        when (FileActions.openFolder(context, mime)) {
            FileActions.FolderOpen.COPIED -> scope.launch {
                snackbar.showSnackbar(context.getString(R.string.folder_path_copied))
            }
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---------- THEME (dropdown — sab kuch ek layout me) ----------
            SectionCard(stringResource(R.string.section_appearance)) {
                Text(
                    stringResource(R.string.appearance_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                com.nova.converter.ui.DropdownPickerField(
                    expanded = themePicker,
                    onDismiss = { themePicker = false },
                    options = NovaThemes.map { it.id },
                    selected = Settings.themeId.value,
                    label = { id -> NovaThemes.firstOrNull { it.id == id }?.name ?: id },
                    leading = { id ->
                        val spec = NovaThemes.firstOrNull { it.id == id }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            spec?.swatch?.take(3)?.forEach { c ->
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                )
                            }
                        }
                    },
                    onSelect = { Settings.setTheme(it) }
                ) {
                    OutlinedButton(
                        onClick = { themePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            currentTheme.swatch.take(3).forEach { c ->
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                )
                            }
                        }
                        Text(
                            currentTheme.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.follow_system_dark),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.follow_system_dark_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = Settings.themeMode.value == "system",
                        onCheckedChange = { Settings.setThemeMode(if (it) "system" else "app") }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.amoled),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.amoled_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = Settings.amoled.value,
                        onCheckedChange = { Settings.setAmoled(it) }
                    )
                }
            }

            // ---------- LANGUAGE (10 + system) ----------
            SectionCard(stringResource(R.string.language)) {
                Text(
                    stringResource(R.string.language_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                com.nova.converter.ui.DropdownPickerField(
                    expanded = langPicker,
                    onDismiss = { langPicker = false },
                    options = Settings.supportedLanguages,
                    selected = Settings.language.value,
                    label = { code -> stringResourceForLang(code) },
                    onSelect = { code ->
                        if (code != Settings.language.value) {
                            Settings.setLanguage(code)
                            (context as? android.app.Activity)?.recreate()
                        }
                    }
                ) {
                    OutlinedButton(
                        onClick = { langPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            currentLangName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------- NOTIFICATIONS ----------
            SectionCard(stringResource(R.string.section_notifications)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.completion_sound),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(R.string.completion_sound_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = Settings.soundEnabled.value,
                        onCheckedChange = { Settings.setSound(it) }
                    )
                }
            }

            // ---------- STORAGE ----------
            SectionCard(stringResource(R.string.section_storage)) {
                Text(
                    stringResource(R.string.storage_desc, historyCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { confirmClearHistory = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.clear_history),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ---------- OUTPUT FOLDERS ----------
            // Ye section isliye maanga gaya ki user ko pata chale ki file
            // kahan-jaayegi. Pehle sirf About me ek chhoti si line thi
            // ("Outputs: Movies / Music / Pictures → NovaConverter") jo itni
            // chhoti thi ki dikhti hi nahi. Ab har folder apni row me hai, uska
            // full path likha hai, aur uske saath ek "Open" button hai jo usi
            // folder ko file manager me khol deta hai (wahi fallback chain jo
            // History / Queue screens par lagi hai).
            SectionCard(stringResource(R.string.section_output_folders)) {
                Text(
                    stringResource(R.string.output_folders_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutputFolderRow(
                    label = stringResource(R.string.folder_movies),
                    mime = "video/",
                    icon = Icons.Filled.Movie,
                    tint = Color(0xFF4F8CFF),
                    onOpen = { openOutputFolder("video/") }
                )
                OutputFolderRow(
                    label = stringResource(R.string.folder_music),
                    mime = "audio/",
                    icon = Icons.Filled.MusicNote,
                    tint = Color(0xFF36D399),
                    onOpen = { openOutputFolder("audio/") }
                )
                OutputFolderRow(
                    label = stringResource(R.string.folder_pictures),
                    mime = "image/",
                    icon = Icons.Filled.Image,
                    tint = Color(0xFFFFB020),
                    onOpen = { openOutputFolder("image/") }
                )
                OutputFolderRow(
                    label = stringResource(R.string.folder_downloads),
                    mime = "application/",
                    icon = Icons.Filled.Folder,
                    tint = Color(0xFF9B8CFF),
                    onOpen = { openOutputFolder("application/") }
                )
            }

            // ---------- ABOUT ----------
            // Sirf version. Baaki descriptive lines hata di gayi hain —
            // engine/outputs/no-internet ki baat ab "Where files are saved"
            // section aur Privacy Policy me hai, to yahan repeat karna
            // sirf screen ko bhaari dikhata tha.
            //
            // Privacy Policy + Terms ke buttons jaan-boojh kar rakhe gaye
            // hain: inke bina user in screens tak pahunch hi nahi sakta,
            // aur Google Play listing ke liye in dono ka link zaroori hai.
            SectionCard(stringResource(R.string.about_title)) {
                if (versionLabel.isNotEmpty()) {
                    Text(
                        stringResource(R.string.about_version, versionLabel),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onOpenLegal("privacy") },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.btn_privacy)) }
                    OutlinedButton(
                        onClick = { onOpenLegal("terms") },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.btn_terms)) }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_title),
            message = stringResource(R.string.clear_history_msg),
            confirmLabel = stringResource(R.string.clear),
            onConfirm = {
                History.clear(context)
                historyCount = 0
                confirmClearHistory = false
                scope.launch { snackbar.showSnackbar(context.getString(R.string.history_cleared)) }
            },
            onDismiss = { confirmClearHistory = false }
        )
    }
}

/**
 * Ek output-folder ki highlighted row.
 *
 * "Highlight" ka matlab: plain text list nahi — har row ko ek tinted,
 * rounded container me rakha gaya hai jisme us folder ka apna colour-coded
 * icon hai, naam upar aur poora relative path ("Movies/NovaConverter")
 * neeche. Isse saare folders ek nazar me alag-alag dikhte hain.
 *
 * [mime] sirf folder ka type decide karne ke liye hai — actual path
 * [FileActions.savedFolder] se aata hai taaki yahan wahi text dikhe jo
 * files save karte waqt hota hai (drift impossible).
 */
@Composable
private fun OutputFolderRow(
    label: String,
    mime: String,
    icon: ImageVector,
    tint: Color,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                FileActions.savedFolder(mime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onOpen,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                stringResource(R.string.btn_open),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** Language code → display naam (native script me, resource se). */
@Composable
private fun stringResourceForLang(code: String): String =
    stringResource(
        when (code) {
            "system" -> R.string.lang_system
            "en" -> R.string.lang_en
            "hi" -> R.string.lang_hi
            "ur" -> R.string.lang_ur
            "ar" -> R.string.lang_ar
            "bn" -> R.string.lang_bn
            "ta" -> R.string.lang_ta
            "te" -> R.string.lang_te
            "es" -> R.string.lang_es
            "pt" -> R.string.lang_pt
            "id" -> R.string.lang_id
            else -> R.string.lang_system
        }
    )
