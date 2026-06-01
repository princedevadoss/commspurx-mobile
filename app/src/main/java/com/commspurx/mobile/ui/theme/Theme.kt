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

/** Matches web app primary (#21c45d). */
val BrandPrimary = Color(0xFF21C45D)
val BrandPrimaryDark = Color(0xFF3DDC84)
val BrandPrimaryContainer = Color(0xFFD8F8E4)
val BrandSecondary = Color(0xFF1E4D8C)
val BrandAccent = Color(0xFFFFB020)
val ConnectedGreen = Color(0xFF22C55E)
val OfflineRed = Color(0xFFEF4444)

val HubGradientLight = Brush.verticalGradient(
    colors = listOf(Color(0xFFF4F7FB), Color(0xFFF8FAFC), Color(0xFFFFFFFF)),
)
val HubGradientDark = Brush.verticalGradient(
    colors = listOf(Color(0xFF101418), Color(0xFF141A20), Color(0xFF181D24)),
)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = Color(0xFF0B3D22),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EDFA),
    onSecondaryContainer = Color(0xFF0F2A4D),
    tertiary = BrandAccent,
    error = Color(0xFFB42318),
    background = Color(0xFFF4F7F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF4F0),
    onSurface = Color(0xFF141A17),
    onSurfaceVariant = Color(0xFF5A6760),
    outline = Color(0xFFC5D4CB),
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF062A16),
    primaryContainer = Color(0xFF1A3D2B),
    onPrimaryContainer = Color(0xFFB8F0D0),
    secondary = Color(0xFF7EB4FF),
    onSecondary = Color(0xFF0B1B33),
    secondaryContainer = Color(0xFF1A3358),
    onSecondaryContainer = Color(0xFFD6E6FF),
    tertiary = BrandAccent,
    error = Color(0xFFFF8A80),
    background = Color(0xFF0E1110),
    surface = Color(0xFF171B19),
    surfaceVariant = Color(0xFF232A27),
    onSurface = Color(0xFFE8F0EB),
    onSurfaceVariant = Color(0xFFA3B0A8),
    outline = Color(0xFF3A4540),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
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
