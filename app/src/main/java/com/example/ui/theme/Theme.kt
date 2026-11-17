package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = FluentCobalt,
    onPrimary = Color.White,
    primaryContainer = FluentCobaltContainer,
    onPrimaryContainer = FluentOnCobaltContainer,
    secondary = FluentTeal,
    onSecondary = Color.White,
    secondaryContainer = FluentTealContainer,
    onSecondaryContainer = FluentOnTealContainer,
    tertiary = FluentAmber,
    onTertiary = Color.White,
    tertiaryContainer = FluentAmberContainer,
    onTertiaryContainer = FluentOnAmberContainer,
    background = DeskBackdropLight,
    onBackground = OnSurfaceLight,
    surface = ShellSurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = ElevatedRibbonLight,
    onSurfaceVariant = OnSurfaceMutedLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = ShellSurfaceLight,
    surfaceContainer = DeskBackdropLight,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = HairlineLight,
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCobalt,
    onPrimary = Color(0xFF06203B),
    primaryContainer = DarkCobaltContainer,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = DarkTeal,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = DarkAmber,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = DeskBackdropDark,
    onBackground = OnSurfaceDark,
    surface = ShellSurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = ElevatedRibbonDark,
    onSurfaceVariant = OnSurfaceMutedDark,
    surfaceContainerLowest = Color(0xFF090D16),
    surfaceContainerLow = ShellSurfaceDark,
    surfaceContainer = ElevatedRibbonDark,
    surfaceContainerHigh = Color(0xFF283548),
    outline = HairlineDark,
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

val DocuDeskShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun DocuDeskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        typography = DocuDeskTypography,
        shapes = DocuDeskShapes,
        content = content
    )
}
