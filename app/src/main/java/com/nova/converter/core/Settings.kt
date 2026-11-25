package com.nova.converter.core

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * App settings (SharedPreferences) — theme, notification sound etc.
 * Compose-friendly: state padha ja sakta hai, write-through hota hai.
 */
object Settings {

    private var prefs: android.content.SharedPreferences? = null

    val themeId = mutableStateOf("midnight")
    val soundEnabled = mutableStateOf(true)
    val audioTheme = mutableStateOf("auto")
    /** "app" = selected theme hamesha, "system" = system dark/light follow kare */
    val themeMode = mutableStateOf("app")
    val amoled = mutableStateOf(false)
    val tipsShown = mutableStateOf(false)
    /**
     * App language: "system" = phone ki language, warna BCP-47 tag
     * (en, hi, ur, ar, bn, ta, te, es, pt, id).
     * Note: value attachBaseContext me SharedPreferences se seedha padhi jaati hai
     * (Settings.init() us waqt tak call nahi hota), isliye dono jagah same pref name.
     */
    val language = mutableStateOf("system")
    /** Subtitle display prefs (video player) */
    val subSize = mutableStateOf("m")      // s | m | l
    val subColor = mutableStateOf("white") // white | yellow
    val subOffsetMs = mutableStateOf(0L)   // sync shift: +ve = subs late dikhen
    /** Audio equalizer preset: off | flat | pop | rock | jazz | classical | dance | vocal | bass */
    val eqPreset = mutableStateOf("off")

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext
            .getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        prefs = p
        themeId.value = p.getString("theme", "midnight") ?: "midnight"
        language.value = p.getString("language", "system") ?: "system"
        soundEnabled.value = p.getBoolean("sound", true)
        audioTheme.value = p.getString("audio_theme", "auto") ?: "auto"
        themeMode.value = p.getString("theme_mode", "app") ?: "app"
        amoled.value = p.getBoolean("amoled", false)
        tipsShown.value = p.getBoolean("tips_shown", false)
        subSize.value = p.getString("sub_size", "m") ?: "m"
        subColor.value = p.getString("sub_color", "white") ?: "white"
        subOffsetMs.value = p.getLong("sub_offset", 0L)
        eqPreset.value = p.getString("eq_preset", "off") ?: "off"
    }

    fun setTheme(id: String) {
        themeId.value = id
        prefs?.edit()?.putString("theme", id)?.apply()
    }

    /**
     * 10 supported languages — "system" (phone ki language) ke saath 10.
     * Har code ke naam res/values/strings.xml me `lang_<code>` (translatable=false)
     * hain taaki har language apne native naam se dikhe.
     */
    val supportedLanguages =
        listOf("system", "en", "hi", "ur", "ar", "bn", "ta", "te", "es", "pt", "id")

    fun setLanguage(code: String) {
        language.value = code
        prefs?.edit()?.putString("language", code)?.apply()
    }

    fun setSound(on: Boolean) {
        soundEnabled.value = on
        prefs?.edit()?.putBoolean("sound", on)?.apply()
    }

    fun setAudioTheme(id: String) {
        audioTheme.value = id
        prefs?.edit()?.putString("audio_theme", id)?.apply()
    }

    fun setThemeMode(mode: String) {
        themeMode.value = mode
        prefs?.edit()?.putString("theme_mode", mode)?.apply()
    }

    fun setAmoled(on: Boolean) {
        amoled.value = on
        prefs?.edit()?.putBoolean("amoled", on)?.apply()
    }

    fun setTipsShown(shown: Boolean) {
        tipsShown.value = shown
        prefs?.edit()?.putBoolean("tips_shown", shown)?.apply()
    }

    fun setSubSize(size: String) {
        subSize.value = size
        prefs?.edit()?.putString("sub_size", size)?.apply()
    }

    fun setSubColor(color: String) {
        subColor.value = color
        prefs?.edit()?.putString("sub_color", color)?.apply()
    }

    fun setSubOffset(offsetMs: Long) {
        subOffsetMs.value = offsetMs.coerceIn(-5000L, 5000L)
        prefs?.edit()?.putLong("sub_offset", subOffsetMs.value)?.apply()
    }

    fun setEqPreset(id: String) {
        eqPreset.value = id
        prefs?.edit()?.putString("eq_preset", id)?.apply()
    }
}
