package com.example.brightnesscontrol.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.brightnesscontrol.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Downloading : UpdateState
    data class NeedInstallPermission(val info: UpdateInfo) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String
)

enum class InstallResult { STARTED, NEED_INSTALL_PERMISSION, FAILED }

object UpdateManager {
    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(BuildConfig.VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            connection.useConnection { input ->
                val json = JSONObject(input.inputStream.bufferedReader().use { it.readText() })
                val info = UpdateInfo(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName", ""),
                    notes = json.optString("notes", "")
                )
                if (info.versionCode > BuildConfig.VERSION_CODE && info.versionName.isNotBlank()) {
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            }
        }.getOrElse { error ->
            UpdateState.Error(error.message ?: "Unknown error")
        }
    }

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo): InstallResult =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                return@withContext InstallResult.NEED_INSTALL_PERMISSION
            }

            runCatching {
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                val apk = File(directory, "BrightnessControl-${info.versionCode}.apk")
                val connection = (URL(BuildConfig.APK_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }
                connection.useConnection { input ->
                    input.inputStream.use { source ->
                        apk.outputStream().use { destination -> source.copyTo(destination) }
                    }
                }
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apk
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
                InstallResult.STARTED
            }.getOrElse { InstallResult.FAILED }
        }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
        try {
            if (responseCode !in 200..299) error("HTTP $responseCode")
            return block(this)
        } finally {
            disconnect()
        }
    }
}
