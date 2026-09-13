package com.example.brightnesscontrol.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

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

    fun ensureBaseBrightness(percent: Int) {
        if (preferences.getBoolean(KEY_BASE_IS_PERCENT, false)) return

        val migratedPercent = if (preferences.contains(KEY_BASE_BRIGHTNESS)) {
            // Older builds stored Android's 0..255 value. Migrate it once to 0..100.
            val oldRaw = preferences.getInt(KEY_BASE_BRIGHTNESS, 128)
            (oldRaw.coerceIn(0, 255) / 255f * 100f).roundToInt()
        } else {
            percent
        }
        preferences.edit()
            .putInt(KEY_BASE_BRIGHTNESS, migratedPercent.coerceIn(0, 100))
            .putBoolean(KEY_BASE_IS_PERCENT, true)
            .apply()
    }

    fun setCorrection(value: Int) {
        preferences.edit().putInt(KEY_CORRECTION, value.coerceIn(-100, 100)).apply()
    }

    fun setAutostart(value: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOSTART, value).apply()
    }

    fun setTheme(value: ThemeMode) {
        preferences.edit().putString(KEY_THEME, value.name).apply()
    }

    fun rebasePercent(percent: Int) {
        preferences.edit()
            .putInt(KEY_BASE_BRIGHTNESS, percent.coerceIn(0, 100))
            .putBoolean(KEY_BASE_IS_PERCENT, true)
            .putInt(KEY_CORRECTION, 0)
            .apply()
    }

    private fun readState(): PreferencesState {
        val theme = preferences.getString(KEY_THEME, ThemeMode.SYSTEM.name)
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM) }
            ?: ThemeMode.SYSTEM
        return PreferencesState(
            correction = preferences.getInt(KEY_CORRECTION, 0).coerceIn(-100, 100),
            basePercent = preferences.getInt(KEY_BASE_BRIGHTNESS, 50).coerceIn(0, 100),
            autostart = preferences.getBoolean(KEY_AUTOSTART, false),
            theme = theme
        )
    }

    companion object {
        private const val FILE_NAME = "brightness_preferences"
        private const val KEY_CORRECTION = "correction"
        private const val KEY_BASE_BRIGHTNESS = "base_brightness"
        private const val KEY_BASE_IS_PERCENT = "base_brightness_is_percent"
        private const val KEY_AUTOSTART = "autostart"
        private const val KEY_THEME = "theme"
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PreferencesState(
    val correction: Int = 0,
    val basePercent: Int = 50,
    val autostart: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM
)
