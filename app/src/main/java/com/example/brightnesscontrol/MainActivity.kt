package com.example.brightnesscontrol

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.brightnesscontrol.brightness.BrightnessAccessibilityService
import com.example.brightnesscontrol.brightness.BrightnessController
import com.example.brightnesscontrol.data.PreferencesState
import com.example.brightnesscontrol.data.ThemeMode
import com.example.brightnesscontrol.ui.AppBackground
import com.example.brightnesscontrol.ui.BrightnessTheme
import com.example.brightnesscontrol.ui.MainViewModel
import com.example.brightnesscontrol.update.UpdateInfo
import com.example.brightnesscontrol.update.UpdateManager
import com.example.brightnesscontrol.update.UpdateState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var permissionFlowActive = false
    private var restrictedSettingsStage = false
    private var accessibilitySettingsStage = false
    private var writeSettingsStage = false

    override fun onResume() {
        super.onResume()

        if (restrictedSettingsStage) {
            restrictedSettingsStage = false
            accessibilitySettingsStage = true
            viewModel.openAccessibilitySettings()
            return
        }

        if (accessibilitySettingsStage) {
            accessibilitySettingsStage = false
            if (!permissionFlowActive) return

            val accessibilityGranted = BrightnessAccessibilityService.isEnabled(this)
            if (!accessibilityGranted) {
                permissionFlowActive = false
                return
            }
            if (!BrightnessController.canWriteSettings(this)) {
                writeSettingsStage = true
                viewModel.openWriteSettings()
                return
            }
            permissionFlowActive = false
            handleApplyResult(viewModel.applyBrightness(), this, viewModel)
            return
        }

        if (writeSettingsStage) {
            writeSettingsStage = false
            if (permissionFlowActive &&
                BrightnessAccessibilityService.isEnabled(this) &&
                BrightnessController.canWriteSettings(this)
            ) {
                permissionFlowActive = false
                handleApplyResult(viewModel.applyBrightness(), this, viewModel)
            } else {
                permissionFlowActive = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            BrightnessTheme(state.theme) {
                AppBackground {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = if (selectedTab == 0) {
                                            stringResource(R.string.brightness_title)
                                        } else {
                                            stringResource(R.string.settings_title)
                                        },
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                actions = {
                                    if (selectedTab == 0) {
                                        IconButton(onClick = { shareGithub(context) }) {
                                            Icon(
                                                Icons.Outlined.Share,
                                                contentDescription = stringResource(R.string.feedback),
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    } else {
                                        Icon(
                                            Icons.Outlined.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(16.dp))
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = Color.Transparent
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                modifier = Modifier.navigationBarsPadding(),
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Outlined.Brightness6, null) },
                                    label = { Text(stringResource(R.string.nav_brightness)) }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Outlined.Settings, null) },
                                    label = { Text(stringResource(R.string.nav_settings)) }
                                )
                            }
                        }
                    ) { padding ->
                        if (selectedTab == 0) {
                            BrightnessScreen(
                                modifier = Modifier.padding(padding),
                                state = state,
                                onBrightnessChange = viewModel::setBrightnessLevel,
                                onApply = {
                                    handleApplyResult(viewModel.applyBrightness(), context, viewModel)
                                },
                                onReset = {
                                    viewModel.resetToZero()
                                    handleApplyResult(viewModel.applyBrightness(), context, viewModel)
                                },
                                onAutostartChange = viewModel::setAutostart
                            )
                        } else {
                            SettingsScreen(
                                modifier = Modifier.padding(padding),
                                state = state,
                                updateState = updateState,
                                appVersion = viewModel.appVersion(),
                                onThemeChange = viewModel::setTheme,
                                onOverlayPermission = { startAccessibilityPermissionFlow(false) },
                                onWritePermission = viewModel::openWriteSettings,
                                onNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onCheckUpdates = viewModel::checkForUpdates,
                                onInstallUpdate = { info ->
                                    viewModel.installUpdate(info) {
                                        UpdateManager.openUnknownSourcesSettings(context)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startAccessibilityPermissionFlow(applyAfter: Boolean) {
        permissionFlowActive = applyAfter
        restrictedSettingsStage = true
        accessibilitySettingsStage = false
        writeSettingsStage = false
        viewModel.openRestrictedSettings()
    }

    private fun handleApplyResult(
        result: com.example.brightnesscontrol.ui.ApplyResult,
        context: Context,
        vm: MainViewModel
    ) {
        when (result) {
            com.example.brightnesscontrol.ui.ApplyResult.Done ->
                Toast.makeText(context, R.string.applied, Toast.LENGTH_SHORT).show()
            com.example.brightnesscontrol.ui.ApplyResult.NeedsAccessibilityPermission -> {
                startAccessibilityPermissionFlow(applyAfter = true)
            }
            com.example.brightnesscontrol.ui.ApplyResult.NeedsWriteSettingsPermission -> {
                permissionFlowActive = true
                writeSettingsStage = true
                vm.openWriteSettings()
            }
        }
    }
}

@Composable
private fun BrightnessScreen(
    modifier: Modifier,
    state: PreferencesState,
    onBrightnessChange: (Int) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onAutostartChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                stringResource(R.string.brightness_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.brightness_level),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "${state.brightnessLevel}%",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = state.brightnessLevel.toFloat(),
                    onValueChange = { onBrightnessChange(it.roundToInt()) },
                    valueRange = -100f..100f,
                    steps = 199
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.apply))
                    }
                    OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }
        }
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (state.autostart) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.auto_apply_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.autostart,
                        onCheckedChange = onAutostartChange
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    modifier: Modifier,
    state: PreferencesState,
    updateState: UpdateState,
    appVersion: String,
    onThemeChange: (ThemeMode) -> Unit,
    onOverlayPermission: () -> Unit,
    onWritePermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: (UpdateInfo) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val overlayGranted = BrightnessAccessibilityService.isEnabled(context)
    val writeGranted = BrightnessController.canWriteSettings(context)
    val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(2.dp))
        SectionTitle(R.string.appearance)
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.theme),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(ThemeMode.SYSTEM, state.theme, R.string.theme_system, onThemeChange)
                ThemeChip(ThemeMode.LIGHT, state.theme, R.string.theme_light, onThemeChange)
                ThemeChip(ThemeMode.DARK, state.theme, R.string.theme_dark, onThemeChange)
            }
        }

        SectionTitle(R.string.access)
        AccessRow(
            icon = Icons.Outlined.Security,
            title = stringResource(R.string.overlay_permission),
            description = stringResource(R.string.overlay_permission_description),
            granted = overlayGranted,
            onGrant = onOverlayPermission
        )
        AccessRow(
            icon = Icons.Outlined.LightMode,
            title = stringResource(R.string.system_write_permission),
            description = stringResource(R.string.system_write_permission_description),
            granted = writeGranted,
            onGrant = onWritePermission
        )
        AccessRow(
            icon = Icons.Outlined.Code,
            title = stringResource(R.string.notification_permission),
            description = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                stringResource(R.string.notifications_not_needed)
            } else stringResource(R.string.notification_permission_description),
            granted = notificationGranted,
            onGrant = onNotificationPermission
        )

        SectionTitle(R.string.updates)
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.updates),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.current_version, appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (updateState) {
                UpdateState.Idle -> TextButton(onClick = onCheckUpdates) {
                    Text(stringResource(R.string.check_updates))
                }
                UpdateState.Checking -> UpdateProgress(stringResource(R.string.checking))
                UpdateState.UpToDate -> {
                    Text(stringResource(R.string.no_updates), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCheckUpdates) { Text(stringResource(R.string.check_updates)) }
                }
                is UpdateState.Available -> {
                    Text(
                        stringResource(R.string.update_available, updateState.info.versionName),
                        fontWeight = FontWeight.SemiBold
                    )
                    if (updateState.info.notes.isNotBlank()) {
                        Text(
                            stringResource(R.string.update_notes, updateState.info.notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { onInstallUpdate(updateState.info) }) {
                        Text(stringResource(R.string.install_update))
                    }
                }
                UpdateState.Downloading -> UpdateProgress(stringResource(R.string.downloading))
                is UpdateState.NeedInstallPermission -> {
                    Text(stringResource(R.string.install_permission_needed))
                    TextButton(onClick = { onInstallUpdate(updateState.info) }) {
                        Text(stringResource(R.string.install_update))
                    }
                }
                is UpdateState.Error -> {
                    Text(
                        stringResource(R.string.update_error, updateState.message),
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onCheckUpdates) { Text(stringResource(R.string.check_updates)) }
                }
            }
        }

        SectionTitle(R.string.about)
        GlassCard {
            TextButton(onClick = { uriHandler.openUri(BuildConfig.GITHUB_URL) }) {
                Icon(Icons.Outlined.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.github))
            }
            TextButton(onClick = { uriHandler.openUri(BuildConfig.ISSUES_URL) }) {
                Icon(Icons.Outlined.Code, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.issues))
            }
            TextButton(onClick = { uriHandler.openUri("mailto:${BuildConfig.AUTHOR_EMAIL}") }) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.feedback))
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun UpdateProgress(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(label)
    }
}

@Composable
private fun ThemeChip(
    mode: ThemeMode,
    selected: ThemeMode,
    label: Int,
    onClick: (ThemeMode) -> Unit
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onClick(mode) },
        label = { Text(stringResource(label), maxLines = 1) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AccessRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text(
                    stringResource(R.string.granted),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
                OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp)
        )
    }
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun GlassCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.13f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

private fun shareGithub(context: Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, BuildConfig.GITHUB_URL)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.app_name)))
}
