package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MaterialYouAmoledDarkColorScheme = darkColorScheme(
    primary = MaterialYouBlue80,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = MaterialYouBlueContainer,
    onPrimaryContainer = MaterialYouOnBlueContainer,
    secondary = MaterialYouGreen80,
    onSecondary = Color(0xFF003915),
    secondaryContainer = MaterialYouGreenContainer,
    onSecondaryContainer = MaterialYouOnGreenContainer,
    tertiary = MaterialYouOrange80,
    onTertiary = Color(0xFF4F2500),
    tertiaryContainer = MaterialYouOrangeContainer,
    onTertiaryContainer = MaterialYouOnOrangeContainer,
    error = MaterialYouRed80,
    onError = Color(0xFF601410),
    errorContainer = MaterialYouRedContainer,
    onErrorContainer = MaterialYouOnRedContainer,
    background = AmoledBlack,
    onBackground = AmoledTextPrimary,
    surface = AmoledSurface,
    onSurface = AmoledTextPrimary,
    surfaceVariant = AmoledSurfaceContainerLow,
    onSurfaceVariant = AmoledTextSecondary,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = AmoledSurfaceContainerHighest,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant
)

private val MaterialYouLightColorScheme = lightColorScheme(
    primary = MaterialYouBlue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF146C2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4EED0),
    onSecondaryContainer = Color(0xFF07210B),
    tertiary = Color(0xFF8F4C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF191C20),
    surface = Color.White,
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F)
)

private fun ColorScheme.toAmoled(): ColorScheme {
    return this.copy(
        background = AmoledBlack,
        surface = AmoledSurface,
        surfaceVariant = AmoledSurfaceContainerLow,
        surfaceContainerLowest = AmoledBlack,
        surfaceContainerLow = AmoledSurfaceContainerLow,
        surfaceContainer = AmoledSurfaceContainer,
        surfaceContainerHigh = AmoledSurfaceContainerHigh,
        surfaceContainerHighest = AmoledSurfaceContainerHighest,
        outline = AmoledOutline,
        outlineVariant = AmoledOutlineVariant
    )
}

@Composable
fun B91CryptTheme(
    darkTheme: Boolean = true, // Default to true for AMOLED experience
    dynamicColor: Boolean = true, // Enable Material You dynamic colors from Android 12+
    useAmoled: Boolean = true, // Force true pure black for AMOLED
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && useAmoled) dynamic.toAmoled() else dynamic
        }
        darkTheme -> {
            if (useAmoled) MaterialYouAmoledDarkColorScheme else MaterialYouAmoledDarkColorScheme
        }
        else -> MaterialYouLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
