package com.nova.converter.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.nova.converter.core.Settings

/** 2026 dark UI — navy background + pink/red accent (default "Midnight") */
val NovaPrimary = Color(0xFFFF3B5C)
val NovaOnPrimary = Color.White
val NovaBg = Color(0xFF0C1130)
val NovaBgDeep = Color(0xFF080C24)
val NovaSurface = Color(0xFF1A2149)
val NovaSurface2 = Color(0xFF232B57)
val NovaText = Color(0xFFEFF1FF)
val NovaTextDim = Color(0xFF9AA2C8)
val NovaOutline = Color(0xFF333B6E)
val NovaGreen = Color(0xFF36D399)
val NovaBlue = Color(0xFF4F8CFF)
val NovaPurple = Color(0xFF8B7CFF)
val NovaAmber = Color(0xFFFFB020)
val NovaPink = Color(0xFFFF7AB6)

/** Ek theme ka spec — Settings screen isi list se picker banata hai */
data class NovaThemeSpec(
    val id: String,
    val name: String,
    val colors: ColorScheme,
    val swatch: List<Color>
)

private fun darkScheme(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
    deep: Color,
    surface: Color,
    surface2: Color,
    text: Color,
    dim: Color,
    outline: Color,
    error: Color = Color(0xFFFF5A5A)
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    secondary = secondary,
    onSecondary = Color.White,
    tertiary = tertiary,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surface2,
    onSurfaceVariant = dim,
    outline = outline,
    error = error,
    onError = Color.White
)

private fun lightScheme(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
    surface: Color,
    surface2: Color,
    text: Color,
    dim: Color,
    outline: Color,
    error: Color = Color(0xFFDC2626)
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    secondary = secondary,
    onSecondary = Color.White,
    tertiary = tertiary,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surface2,
    onSurfaceVariant = dim,
    outline = outline,
    error = error,
    onError = Color.White
)

/** 10 themes — dark aur light dono */
val NovaThemes: List<NovaThemeSpec> = listOf(
    NovaThemeSpec(
        "midnight", "Midnight",
        darkScheme(
            primary = Color(0xFFFF3B5C), secondary = Color(0xFF8B7CFF), tertiary = Color(0xFF36D399),
            background = NovaBg, deep = NovaBgDeep, surface = NovaSurface, surface2 = NovaSurface2,
            text = NovaText, dim = NovaTextDim, outline = NovaOutline
        ),
        listOf(Color(0xFFFF3B5C), Color(0xFF8B7CFF), Color(0xFF36D399))
    ),
    NovaThemeSpec(
        "ocean", "Ocean",
        darkScheme(
            primary = Color(0xFF22D3EE), secondary = Color(0xFF38BDF8), tertiary = Color(0xFF34D399),
            background = Color(0xFF061526), deep = Color(0xFF04101C), surface = Color(0xFF0E2237),
            surface2 = Color(0xFF143049), text = Color(0xFFEAF7FF), dim = Color(0xFF8FB3C9),
            outline = Color(0xFF1E3E58)
        ),
        listOf(Color(0xFF22D3EE), Color(0xFF38BDF8), Color(0xFF34D399))
    ),
    NovaThemeSpec(
        "forest", "Forest",
        darkScheme(
            primary = Color(0xFF84CC16), secondary = Color(0xFF22C55E), tertiary = Color(0xFFFACC15),
            background = Color(0xFF07160F), deep = Color(0xFF04100A), surface = Color(0xFF12291D),
            surface2 = Color(0xFF1A3628), text = Color(0xFFEFFFE9), dim = Color(0xFF9CBBA6),
            outline = Color(0xFF244633)
        ),
        listOf(Color(0xFF84CC16), Color(0xFF22C55E), Color(0xFFFACC15))
    ),
    NovaThemeSpec(
        "sunset", "Sunset",
        darkScheme(
            primary = Color(0xFFFF7A1A), secondary = Color(0xFFFF3D71), tertiary = Color(0xFFFFD166),
            background = Color(0xFF1A0F0A), deep = Color(0xFF120A07), surface = Color(0xFF2A1A12),
            surface2 = Color(0xFF3A2418), text = Color(0xFFFFF3E8), dim = Color(0xFFC7A698),
            outline = Color(0xFF4A2E1C)
        ),
        listOf(Color(0xFFFF7A1A), Color(0xFFFF3D71), Color(0xFFFFD166))
    ),
    NovaThemeSpec(
        "royal", "Royal",
        darkScheme(
            primary = Color(0xFFA855F7), secondary = Color(0xFF6366F1), tertiary = Color(0xFF2DD4BF),
            background = Color(0xFF120A24), deep = Color(0xFF0C071A), surface = Color(0xFF221440),
            surface2 = Color(0xFF2E1C55), text = Color(0xFFF4EEFF), dim = Color(0xFFAC9FD1),
            outline = Color(0xFF3A276B)
        ),
        listOf(Color(0xFFA855F7), Color(0xFF6366F1), Color(0xFF2DD4BF))
    ),
    NovaThemeSpec(
        "rose", "Rose",
        darkScheme(
            primary = Color(0xFFF43F5E), secondary = Color(0xFFFB7185), tertiary = Color(0xFFFDA4AF),
            background = Color(0xFF1A0F16), deep = Color(0xFF120A10), surface = Color(0xFF2C1726),
            surface2 = Color(0xFF3B1F32), text = Color(0xFFFFF0F4), dim = Color(0xFFC89CB0),
            outline = Color(0xFF572B47)
        ),
        listOf(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFFFDA4AF))
    ),
    NovaThemeSpec(
        "neon", "Neon",
        darkScheme(
            primary = Color(0xFF39FF14), secondary = Color(0xFFFF2BD6), tertiary = Color(0xFF00E5FF),
            background = Color(0xFF05070A), deep = Color(0xFF020406), surface = Color(0xFF0E1218),
            surface2 = Color(0xFF151B23), text = Color(0xFFE9FFF2), dim = Color(0xFF7E948A),
            outline = Color(0xFF232C36)
        ),
        listOf(Color(0xFF39FF14), Color(0xFFFF2BD6), Color(0xFF00E5FF))
    ),
    NovaThemeSpec(
        "graphite", "Graphite",
        darkScheme(
            primary = Color(0xFF60A5FA), secondary = Color(0xFF94A3B8), tertiary = Color(0xFF4ADE80),
            background = Color(0xFF17181C), deep = Color(0xFF101114), surface = Color(0xFF23252B),
            surface2 = Color(0xFF2C2F37), text = Color(0xFFF1F5F9), dim = Color(0xFF9AA3B2),
            outline = Color(0xFF3A3E48)
        ),
        listOf(Color(0xFF60A5FA), Color(0xFF94A3B8), Color(0xFF4ADE80))
    ),
    NovaThemeSpec(
        "daylight", "Daylight",
        lightScheme(
            primary = Color(0xFF2563EB), secondary = Color(0xFF7C3AED), tertiary = Color(0xFF059669),
            background = Color(0xFFF7F8FC), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFECF0F7),
            text = Color(0xFF111827), dim = Color(0xFF5B6474), outline = Color(0xFFD3DAE6)
        ),
        listOf(Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFF059669))
    ),
    NovaThemeSpec(
        "solar", "Solar Light",
        lightScheme(
            primary = Color(0xFF0D9488), secondary = Color(0xFFEA580C), tertiary = Color(0xFFCA8A04),
            background = Color(0xFFFBF7EF), surface = Color(0xFFFFFDF8), surface2 = Color(0xFFF3EDE0),
            text = Color(0xFF292524), dim = Color(0xFF6B655C), outline = Color(0xFFE3DCCB)
        ),
        listOf(Color(0xFF0D9488), Color(0xFFEA580C), Color(0xFFCA8A04))
    )
)

/**
 * Deep (gambhir) page background — theme ke hisaab se.
 * Light themes (Daylight/Solar) me yeh background ke barabar hai, warna
 * dark text dark bg pe invisible ho jata tha.
 */
val LocalNovaBgDeep = staticCompositionLocalOf { NovaBgDeep }

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val chosen = NovaThemes.firstOrNull { it.id == Settings.themeId.value } ?: NovaThemes.first()
    val chosenLight = chosen.colors.background.luminance() > 0.4f
    // system mode: polarity match rakho, warna dark default (midnight) / light default (daylight)
    val spec = if (Settings.themeMode.value == "system" && chosenLight == systemDark) {
        if (systemDark) NovaThemes.first { it.id == "midnight" }
        else NovaThemes.first { it.id == "daylight" }
    } else chosen

    // AMOLED: dark theme pe pure-black background (OLED power + true black look)
    val specLight = spec.colors.background.luminance() > 0.4f
    val colors = if (Settings.amoled.value && !specLight) {
        spec.colors.copy(background = Color.Black)
    } else spec.colors
    val bg = colors.background
    val deep = if (bg.luminance() > 0.4f) bg
    else Color(bg.red * 0.78f, bg.green * 0.78f, bg.blue * 0.78f)

    // window background bhi theme ke saath — status bar / splash edges match rahen
    val context = LocalContext.current
    LaunchedEffect(bg) {
        (context as? Activity)?.window?.setBackgroundDrawable(
            ColorDrawable(android.graphics.Color.rgb(bg.red, bg.green, bg.blue))
        )
    }

    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalNovaBgDeep provides deep) {
            content()
        }
    }
}
