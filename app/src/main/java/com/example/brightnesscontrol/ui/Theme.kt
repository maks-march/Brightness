package com.example.brightnesscontrol.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.brightnesscontrol.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF6155D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E0FF),
    onPrimaryContainer = Color(0xFF1D145C),
    secondary = Color(0xFF55647A),
    secondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFF8C4A72),
    background = Color(0xFFF8F7FC),
    surface = Color(0xFFF8F7FC),
    surfaceVariant = Color(0xFFE5E2EC),
    onSurfaceVariant = Color(0xFF464550)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCAC2FF),
    onPrimary = Color(0xFF30256D),
    primaryContainer = Color(0xFF483E85),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Color(0xFFBBC7DE),
    secondaryContainer = Color(0xFF3D475A),
    tertiary = Color(0xFFF2B8D7),
    background = Color(0xFF10111B),
    onBackground = Color(0xFFB9B8C1),
    surface = Color(0xFF171824),
    onSurface = Color(0xFFC4C3CC),
    surfaceVariant = Color(0xFF454550),
    onSurfaceVariant = Color(0xFFC7C4D0)
)

@Composable
fun BrightnessTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}
