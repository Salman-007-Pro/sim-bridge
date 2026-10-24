package com.simbridge.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2350E6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF0B8A7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F1EA),
    onSecondaryContainer = Color(0xFF00201C),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF151A26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A26),
    surfaceVariant = Color(0xFFEDEFF6),
    onSurfaceVariant = Color(0xFF4A5163),
    surfaceContainer = Color(0xFFF1F3F9),
    surfaceContainerHigh = Color(0xFFEAEDF5),
    outline = Color(0xFFC3C8D6),
    outlineVariant = Color(0xFFDDE1EB),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E1),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C5FF),
    onPrimary = Color(0xFF00218B),
    primaryContainer = Color(0xFF1A3CC2),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFF7FD8CA),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFC9F1EA),
    background = Color(0xFF0E121B),
    onBackground = Color(0xFFE3E6EF),
    surface = Color(0xFF151A24),
    onSurface = Color(0xFFE3E6EF),
    surfaceVariant = Color(0xFF242A37),
    onSurfaceVariant = Color(0xFFC1C6D4),
    surfaceContainer = Color(0xFF1A1F2A),
    surfaceContainerHigh = Color(0xFF222835),
    outline = Color(0xFF5B6273),
    outlineVariant = Color(0xFF353B4A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val CodeTextStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)

@Composable
fun SimBridgeTheme(
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
