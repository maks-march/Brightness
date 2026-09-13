package com.example.brightnesscontrol.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
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

    fun ensureBrightnessLevel() {
        if (preferences.contains(KEY_BRIGHTNESS_LEVEL)) return

        // Migrate values from both previous models. A negative old correction becomes
        // the new negative overlay range; a saved system level remains positive.
        val migratedLevel = when {
            preferences.contains(KEY_BRIGHTNESS_PERCENT) ->
                preferences.getInt(KEY_BRIGHTNESS_PERCENT, 0).coerceIn(0, 100)
            preferences.contains(KEY_OLD_BASE_BRIGHTNESS) -> {
                val oldRaw = preferences.getInt(KEY_OLD_BASE_BRIGHTNESS, 0)
                val basePercent = (oldRaw.coerceIn(0, 255) / 255f * 100f).roundToInt()
                val oldCorrection = preferences.getInt(KEY_OLD_CORRECTION, 0)
                if (oldCorrection < 0) -abs(oldCorrection).coerceIn(0, 100) else basePercent
            }
            else -> 0
        }

        val oldCorrection = preferences.getInt(KEY_OLD_CORRECTION, 0)
        val migratedDim = if (migratedLevel < 0) 0 else (-oldCorrection).coerceIn(0, 100)
        preferences.edit()
            .putInt(KEY_BRIGHTNESS_LEVEL, migratedLevel.coerceIn(-100, 100))
            .putInt(KEY_DIM_PERCENT, migratedDim)
            .apply()
    }

    fun setBrightnessLevel(value: Int) {
        preferences.edit().putInt(KEY_BRIGHTNESS_LEVEL, value.coerceIn(-100, 100)).apply()
    }

    fun setDimPercent(value: Int) {
        preferences.edit().putInt(KEY_DIM_PERCENT, value.coerceIn(0, 100)).apply()
    }

    fun setAutostart(value: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOSTART, value).apply()
    }

    fun setTheme(value: ThemeMode) {
        preferences.edit().putString(KEY_THEME, value.name).apply()
    }

    private fun readState(): PreferencesState {
        val theme = preferences.getString(KEY_THEME, ThemeMode.SYSTEM.name)
            ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM) }
            ?: ThemeMode.SYSTEM
        return PreferencesState(
            brightnessLevel = preferences.getInt(KEY_BRIGHTNESS_LEVEL, 0).coerceIn(-100, 100),
            dimPercent = preferences.getInt(KEY_DIM_PERCENT, 0).coerceIn(0, 100),
            autostart = preferences.getBoolean(KEY_AUTOSTART, false),
            theme = theme
        )
    }

    companion object {
        private const val FILE_NAME = "brightness_preferences"
        private const val KEY_BRIGHTNESS_LEVEL = "brightness_level"
        private const val KEY_DIM_PERCENT = "dim_percent"
        private const val KEY_AUTOSTART = "autostart"
        private const val KEY_THEME = "theme"

        // Previous absolute model.
        private const val KEY_BRIGHTNESS_PERCENT = "brightness_percent"
        // Original correction model, kept only for one-time migration.
        private const val KEY_OLD_BASE_BRIGHTNESS = "base_brightness"
        private const val KEY_OLD_CORRECTION = "correction"
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PreferencesState(
    val brightnessLevel: Int = 0,
    val dimPercent: Int = 0,
    val autostart: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM
)
