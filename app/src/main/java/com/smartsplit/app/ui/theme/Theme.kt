package com.smartsplit.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = SurfaceWhite,
    primaryContainer = PrimaryTealLight,
    onPrimaryContainer = PrimaryTealDark,
    secondary = SecondaryIndigo,
    onSecondary = SurfaceWhite,
    secondaryContainer = SecondaryIndigoLight,
    onSecondaryContainer = SecondaryIndigo,
    background = BackgroundSlate,
    onBackground = TextDark,
    surface = SurfaceWhite,
    onSurface = TextDark,
    surfaceVariant = BackgroundSlate,
    onSurfaceVariant = TextMedium,
    outline = DividerBorder,
    error = ErrorRed,
    onError = SurfaceWhite
)

@Composable
fun SmartSplitAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PrimaryTealDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
