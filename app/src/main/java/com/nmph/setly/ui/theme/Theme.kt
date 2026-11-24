package com.nmph.setly.ui.theme

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

/** Палитра «Тёплый песок и Терракота». */
private val Terracotta = Color(0xFFE07A5F)
private val DarkTerracotta = Color(0xFFC8553D)
private val Sage = Color(0xFF81B29A)
private val Ink = Color(0xFF3D405B)
private val InkLight = Color(0xFFB8BAD0) // осветлённые чернила для читаемости в тёмной теме
private val Coffee = Color(0xFF2B2522)
private val Sand = Color(0xFFFDFBF7)
private val SandSurface = Color(0xFFF4F1EA)

/** Акцент фона аватаров и иконок в новой палитре. */
val IconAccent = Terracotta

/** Единые отступы: 16.dp между блоками, 12.dp внутри карточек. */
object AppSpacing {
    val Block = 16.dp
    val CardInner = 12.dp
}

/** Единое оформление карточек всех экранов. */
object AppCards {
    val Shape = RoundedCornerShape(16.dp)
    val Elevation = 2.dp
}

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4D9D0),
    onPrimaryContainer = Color(0xFF7A2E1C),
    secondary = Sage,
    onSecondary = Coffee,
    secondaryContainer = Color(0xFFDDEBE3),
    onSecondaryContainer = Color(0xFF25483B),
    tertiary = Ink,
    onTertiary = Sand,
    tertiaryContainer = Color(0xFFE8E7EF),
    onTertiaryContainer = Ink,
    error = DarkTerracotta,
    onError = Color.White,
    errorContainer = Color(0xFFF6DDD7),
    onErrorContainer = Color(0xFF7A2E1C),
    background = Sand,
    onBackground = Coffee,
    surface = SandSurface,
    onSurface = Coffee,
    surfaceVariant = Color(0xFFE9E3D8),
    onSurfaceVariant = Color(0xFF6B625A),
    outline = Color(0xFFB9AFA2),
)

private val DarkColors = darkColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7A3B2B),
    onPrimaryContainer = Sand,
    secondary = Sage,
    onSecondary = Coffee,
    secondaryContainer = Color(0xFF3F5C4E),
    onSecondaryContainer = Sand,
    tertiary = InkLight,
    onTertiary = Coffee,
    tertiaryContainer = Color(0xFF3D405B),
    onTertiaryContainer = Sand,
    error = DarkTerracotta,
    onError = Color.White,
    errorContainer = Color(0xFF7A2E1C),
    onErrorContainer = Sand,
    background = Coffee,
    onBackground = Sand,
    surface = Color(0xFF3D3835),
    onSurface = Sand,
    surfaceVariant = Color(0xFF4A4340),
    onSurfaceVariant = Color(0xFFD9D0C7),
    outline = Color(0xFF6F655E),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = AppCards.Shape,
    large = AppCards.Shape,
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().copy(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun SetlyTheme(
    // Тёмная тема — вариант по умолчанию; системная тема на выбор не влияет.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
