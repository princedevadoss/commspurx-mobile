package com.commspurx.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Refined navy + brass palette — professional operations dashboard. */
val BrandPrimary = Color(0xFF1E3A5F)
val BrandPrimaryDark = Color(0xFF8EB4E8)
val BrandPrimaryContainer = Color(0xFFE8EEF5)
val BrandSecondary = Color(0xFF2D6A4F)
val BrandAccent = Color(0xFFC9A962)
val ConnectedGreen = Color(0xFF2D6A4F)
val OfflineRed = Color(0xFFB42318)

val AppShellGradientLight = Brush.verticalGradient(
    colors = listOf(Color(0xFFF4F6F9), Color(0xFFFAFBFC), Color(0xFFFFFFFF)),
)
val AppShellGradientDark = Brush.verticalGradient(
    colors = listOf(Color(0xFF0F1419), Color(0xFF141A22), Color(0xFF1A212B)),
)

/** @deprecated Use {@link AppShellGradientLight}. */
val HubGradientLight = AppShellGradientLight

/** @deprecated Use {@link AppShellGradientDark}. */
val HubGradientDark = AppShellGradientDark

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = Color(0xFF0F2438),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F2EC),
    onSecondaryContainer = Color(0xFF0F3324),
    tertiary = BrandAccent,
    onTertiary = Color(0xFF2A2210),
    error = Color(0xFFB42318),
    background = Color(0xFFF4F6F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF1F5),
    onSurface = Color(0xFF141C24),
    onSurfaceVariant = Color(0xFF5C6773),
    outline = Color(0xFFD0D7DE),
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF0A1520),
    primaryContainer = Color(0xFF1A2D42),
    onPrimaryContainer = Color(0xFFD4E4F7),
    secondary = Color(0xFF6BC49A),
    onSecondary = Color(0xFF0A2018),
    secondaryContainer = Color(0xFF1A3328),
    onSecondaryContainer = Color(0xFFC8E8D8),
    tertiary = BrandAccent,
    onTertiary = Color(0xFF2A2210),
    error = Color(0xFFFF8A80),
    background = Color(0xFF0F1419),
    surface = Color(0xFF171D25),
    surfaceVariant = Color(0xFF222A34),
    onSurface = Color(0xFFE8EDF2),
    onSurfaceVariant = Color(0xFF9AA5B1),
    outline = Color(0xFF3A4550),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
    ),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun CommspurxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
