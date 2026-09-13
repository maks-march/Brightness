package com.example.brightnesscontrol.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brightnesscontrol.BuildConfig
import com.example.brightnesscontrol.brightness.BrightnessAccessibilityService
import com.example.brightnesscontrol.brightness.BrightnessController
import com.example.brightnesscontrol.brightness.BrightnessService
import com.example.brightnesscontrol.data.AppPreferences
import com.example.brightnesscontrol.data.PreferencesState
import com.example.brightnesscontrol.data.ThemeMode
import com.example.brightnesscontrol.update.InstallResult
import com.example.brightnesscontrol.update.UpdateManager
import com.example.brightnesscontrol.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

sealed interface ApplyResult {
    data object Done : ApplyResult
    data object NeedsAccessibilityPermission : ApplyResult
    data object NeedsWriteSettingsPermission : ApplyResult
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    val preferences = AppPreferences(appContext)
    val state: StateFlow<PreferencesState> = preferences.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), preferences.current())

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        preferences.ensureBrightnessLevel()
    }

    fun setBrightnessLevel(value: Int) = preferences.setBrightnessLevel(value)

    fun resetToZero() {
        preferences.setBrightnessLevel(0)
        preferences.setDimPercent(0)
        BrightnessAccessibilityService.clearOverlay()
        BrightnessService.stop(appContext)
        BrightnessController.writeSystemBrightness(appContext, 0)
    }

    fun applyBrightness(): ApplyResult {
        val current = preferences.current()
        val dimPercent = effectiveDimPercent(current)
        if (dimPercent > 0 && !BrightnessAccessibilityService.isEnabled(appContext)) {
            return ApplyResult.NeedsAccessibilityPermission
        }
        if (!BrightnessController.canWriteSettings(appContext)) {
            return ApplyResult.NeedsWriteSettingsPermission
        }

        val systemPercent = current.brightnessLevel.coerceAtLeast(0)
        BrightnessController.writeSystemBrightness(appContext, systemPercent)
        BrightnessService.apply(appContext, systemPercent, dimPercent)
        return ApplyResult.Done
    }

    private fun effectiveDimPercent(state: PreferencesState): Int = when {
        state.brightnessLevel < 0 -> max(abs(state.brightnessLevel), state.dimPercent)
        state.brightnessLevel == 0 -> 0
        else -> state.dimPercent
    }

    fun setAutostart(enabled: Boolean) = preferences.setAutostart(enabled)
    fun setTheme(theme: ThemeMode) = preferences.setTheme(theme)

    fun checkForUpdates() {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch { _updateState.value = UpdateManager.check() }
    }

    fun installUpdate(info: com.example.brightnesscontrol.update.UpdateInfo, onPermission: () -> Unit) {
        _updateState.value = UpdateState.Downloading
        viewModelScope.launch {
            when (UpdateManager.downloadAndInstall(appContext, info)) {
                InstallResult.STARTED -> _updateState.value = UpdateState.Idle
                InstallResult.NEED_INSTALL_PERMISSION -> {
                    _updateState.value = UpdateState.NeedInstallPermission(info)
                    onPermission()
                }
                InstallResult.FAILED -> _updateState.value = UpdateState.Error("Download or installation failed")
            }
        }
    }

    fun openRestrictedSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun openAccessibilitySettings() {
        appContext.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openWriteSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun appVersion(): String = BuildConfig.VERSION_NAME
}
