package com.example.disasterlink.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = TextPrimary,
    inversePrimary = PrimaryDark,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = TextPrimary,
    tertiary = Accent,
    onTertiary = TextPrimary,
    tertiaryContainer = SecondaryLight,
    onTertiaryContainer = TextPrimary,
    background = Background,
    onBackground = OnBackground,
    surface = PrimaryLight, // App bar background
    onSurface = TextPrimary, // Text/icons on app bar
    surfaceVariant = Surface, // For cards and other surfaces
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Primary,
    inverseSurface = PrimaryDark,
    inverseOnSurface = OnPrimary,
    error = Error,
    onError = OnError,
    errorContainer = PrimaryLight, // Placeholder or define specific error container color
    onErrorContainer = TextPrimary, // Placeholder or define specific on error container color
    outline = Divider,
    outlineVariant = TextSecondary,
    scrim = Color(0x99000000)
)

private val DarkColorScheme = darkColorScheme(
    primary = Accent, // Blue for icons and highlights
    onPrimary = TextPrimary, // Contrast with Accent
    primaryContainer = OnSurfaceVariant, // Dark Gray for icon/button backgrounds
    onPrimaryContainer = OnPrimary, // White content on Dark Gray
    inversePrimary = PrimaryLight,
    secondary = TextSecondary, // Gray
    onSecondary = OnPrimary, // White text on gray
    secondaryContainer = OnSurfaceVariant, // Dark gray container
    onSecondaryContainer = Background, // Light gray text
    tertiary = Divider, // Light gray
    onTertiary = TextPrimary, // Dark text on light gray
    tertiaryContainer = OnSurfaceVariant, // Dark Gray container
    onTertiaryContainer = OnPrimary, // White text
    background = TextPrimary, // Very dark (almost black)
    onBackground = OnPrimary, // White text
    surface = PrimaryDark, // App bar background
    onSurface = OnPrimary, // App bar text/icons
    surfaceVariant = OnSurfaceVariant, // Dark gray for cards (community feed)
    onSurfaceVariant = Background, // Light gray text
    surfaceTint = Accent,
    inverseSurface = Background,
    inverseOnSurface = TextPrimary,
    error = Error,
    onError = OnError,
    errorContainer = PrimaryDark, // Placeholder or define specific
    onErrorContainer = OnPrimary, // Placeholder or define specific
    outline = TextSecondary, // Gray
    outlineVariant = Divider, // Lighter gray
    scrim = Color(0x99000000)
)

@Composable
fun DisasterLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Defaulting to false to use custom scheme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // from your typography file
        shapes = Shapes,         // from shapes.kt
        content = content
    )
}
