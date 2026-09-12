package com.example.brightnesscontrol.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All app settings are local. Keeping them in SharedPreferences also lets BootReceiver read them synchronously. */
class AppPreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<PreferencesState> = _state.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = readState()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun current(): PreferencesState = _state.value

    fun ensureBaseBrightness(value: Int) {
        if (!preferences.contains(KEY_BASE_BRIGHTNESS)) {
            preferences.edit().putInt(KEY_BASE_BRIGHTNESS, value.coerceIn(0, 255)).apply()
        }
    }

    fun setCorrection(value: Int) {
        preferences.edit().putInt(KEY_CORRECTION, value.coerceIn(-100, 100)).apply()
    }

    fun setBackgroundEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_BACKGROUND_ENABLED, value).apply()
    }

    fun setAutostart(value: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOSTART, value).apply()
    }

    fun setTheme(value: ThemeMode) {
        preferences.edit().putString(KEY_THEME, value.name).apply()
    }

    fun setBackgroundUri(value: String?) {
        preferences.edit().apply {
            if (value == null) remove(KEY_BACKGROUND_URI) else putString(KEY_BACKGROUND_URI, value)
        }.apply()
    }

    fun rebaseBrightness(value: Int) {
        preferences.edit()
            .putInt(KEY_BASE_BRIGHTNESS, value.coerceIn(0, 255))
            .putInt(KEY_CORRECTION, 0)
            .apply()
    }

    private fun readState(): PreferencesState {
        val theme = preferences.getString(KEY_THEME, ThemeMode.SYSTEM.name)
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM) }
            ?: ThemeMode.SYSTEM
        return PreferencesState(
            correction = preferences.getInt(KEY_CORRECTION, 0).coerceIn(-100, 100),
            baseBrightness = preferences.getInt(KEY_BASE_BRIGHTNESS, 128).coerceIn(0, 255),
            backgroundEnabled = preferences.getBoolean(KEY_BACKGROUND_ENABLED, false),
            autostart = preferences.getBoolean(KEY_AUTOSTART, false),
            theme = theme,
            backgroundUri = preferences.getString(KEY_BACKGROUND_URI, null)
        )
    }

    companion object {
        private const val FILE_NAME = "brightness_preferences"
        private const val KEY_CORRECTION = "correction"
        private const val KEY_BASE_BRIGHTNESS = "base_brightness"
        private const val KEY_BACKGROUND_ENABLED = "background_enabled"
        private const val KEY_AUTOSTART = "autostart"
        private const val KEY_THEME = "theme"
        private const val KEY_BACKGROUND_URI = "background_uri"
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PreferencesState(
    val correction: Int = 0,
    val baseBrightness: Int = 128,
    val backgroundEnabled: Boolean = false,
    val autostart: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val backgroundUri: String? = null
)
