package com.example.brightnesscontrol.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brightnesscontrol.BuildConfig
import com.example.brightnesscontrol.brightness.BrightnessController
import com.example.brightnesscontrol.brightness.BrightnessService
import com.example.brightnesscontrol.data.AppPreferences
import com.example.brightnesscontrol.data.PreferencesState
import com.example.brightnesscontrol.update.InstallResult
import com.example.brightnesscontrol.update.UpdateManager
import com.example.brightnesscontrol.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

sealed interface ApplyResult {
    data object Done : ApplyResult
    data object NeedsOverlayPermission : ApplyResult
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
        preferences.ensureBaseBrightness(BrightnessController.readSystemBrightness(appContext))
    }

    fun setCorrection(value: Int) = preferences.setCorrection(value)

    fun resetToSystem() {
        // The base is captured before an extended correction is applied, so reset never
        // mistakes an already boosted value for the user's ordinary system level.
        val base = preferences.current().baseBrightness
        BrightnessService.stop(appContext, restore = true)
        preferences.rebaseBrightness(base)
        BrightnessController.writeSystemBrightness(appContext, base)
    }

    fun applyCorrection(): ApplyResult {
        val current = preferences.current()
        if (current.correction < 0 && !BrightnessController.canDrawOverlay(appContext)) {
            return ApplyResult.NeedsOverlayPermission
        }
        if (current.correction > 0 && !BrightnessController.canWriteSettings(appContext)) {
            return ApplyResult.NeedsWriteSettingsPermission
        }

        when {
            current.correction < 0 -> BrightnessService.apply(
                appContext,
                current.correction,
                current.baseBrightness
            )
            current.correction > 0 -> {
                if (current.backgroundEnabled) {
                    BrightnessService.apply(appContext, current.correction, current.baseBrightness)
                } else {
                    BrightnessController.writeSystemBrightness(
                        appContext,
                        BrightnessController.systemValueForCorrection(
                            current.baseBrightness,
                            current.correction
                        )
                    )
                    BrightnessService.stop(appContext, restore = false)
                }
            }
            else -> {
                BrightnessService.stop(appContext, restore = true)
                BrightnessController.writeSystemBrightness(appContext, current.baseBrightness)
            }
        }
        return ApplyResult.Done
    }

    fun setBackgroundEnabled(enabled: Boolean): ApplyResult {
        if (enabled && preferences.current().correction < 0 &&
            !BrightnessController.canDrawOverlay(appContext)
        ) return ApplyResult.NeedsOverlayPermission

        preferences.setBackgroundEnabled(enabled)
        if (enabled) return applyCorrection()
        BrightnessService.stop(appContext, restore = false)
        return ApplyResult.Done
    }

    fun setAutostart(enabled: Boolean) = preferences.setAutostart(enabled)
    fun setTheme(theme: com.example.brightnesscontrol.data.ThemeMode) = preferences.setTheme(theme)
    fun setBackgroundUri(uri: Uri?) = preferences.setBackgroundUri(uri?.toString())
    fun removeBackground() = preferences.setBackgroundUri(null)

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

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
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
