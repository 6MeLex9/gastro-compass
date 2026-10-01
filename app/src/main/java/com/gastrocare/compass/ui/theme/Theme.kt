package com.gastrocare.compass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра: спокойные медицинские зелёные тона плюс тёплые акценты для предупреждений. */
object GastroColors {
    val Primary = Color(0xFF2E7D63)
    val PrimaryDark = Color(0xFF1B5E54)
    val PrimaryLight = Color(0xFF9FE0B0)
    val Secondary = Color(0xFF4A7C8C)
    val Surface = Color(0xFFF7FAF8)
    val SurfaceVariant = Color(0xFFE3EDE7)
    val OnSurface = Color(0xFF17251F)

    val Safe = Color(0xFF2E7D32)
    val Caution = Color(0xFFF9A825)
    val Risky = Color(0xFFEF6C00)
    val Avoid = Color(0xFFC62828)
    val Info = Color(0xFF1565C0)

    val Protein = Color(0xFF1E88E5)
    val Fat = Color(0xFFF9A825)
    val Carbs = Color(0xFF43A047)
    val Calories = Color(0xFF2E7D63)

    val DarkSurface = Color(0xFF121A17)
    val DarkSurfaceVariant = Color(0xFF24322C)
}

private val LightScheme = lightColorScheme(
    primary = GastroColors.Primary,
    onPrimary = Color.White,
    primaryContainer = GastroColors.PrimaryLight,
    onPrimaryContainer = GastroColors.PrimaryDark,
    secondary = GastroColors.Secondary,
    onSecondary = Color.White,
    background = GastroColors.Surface,
    onBackground = GastroColors.OnSurface,
    surface = Color.White,
    onSurface = GastroColors.OnSurface,
    surfaceVariant = GastroColors.SurfaceVariant,
    onSurfaceVariant = Color(0xFF44544C),
    error = GastroColors.Avoid,
    onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = GastroColors.PrimaryLight,
    onPrimary = GastroColors.PrimaryDark,
    primaryContainer = GastroColors.PrimaryDark,
    onPrimaryContainer = GastroColors.PrimaryLight,
    secondary = Color(0xFF8FB8C4),
    background = GastroColors.DarkSurface,
    onBackground = Color(0xFFE3EDE7),
    surface = Color(0xFF1A241F),
    onSurface = Color(0xFFE3EDE7),
    surfaceVariant = GastroColors.DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBFCEC6),
    error = Color(0xFFFF8A80)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

@Composable
fun GastroCompassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
