package com.tashik54.cardholder.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tashik54.cardholder.R
import com.tashik54.cardholder.data.AppSettings
import kotlin.random.Random

// Inter — ближайший свободный аналог SF Pro (шрифт «hello» при загрузке macOS).
// InterDisplay — «дисплейное» начертание для заголовков, Inter — для текста.
val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

val InterDisplay = FontFamily(
    Font(R.font.inter_display_light, FontWeight.Light),
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
)

private val InterTypography = Typography().withInter()

private fun Typography.withInter(): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Light),
    displayMedium = displayMedium.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Light),
    displaySmall = displaySmall.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Light),
    headlineLarge = headlineLarge.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Light),
    headlineMedium = headlineMedium.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Light),
    headlineSmall = headlineSmall.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Normal),
    titleLarge = titleLarge.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Medium),
    titleMedium = titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    titleSmall = titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodyLarge = bodyLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    bodyMedium = bodyMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    bodySmall = bodySmall.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
    labelLarge = labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelMedium = labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)

// Пастельный градиент в духе фонов Telegram
private val LightGradientColors = listOf(
    Color(0xFFDDE9FF), // нежно-голубой
    Color(0xFFE8DDFF), // лавандовый
    Color(0xFFFFE3EE), // розовый
)

private val DarkGradientColors = listOf(
    Color(0xFF181C2C), // тёмно-синий
    Color(0xFF221A30), // тёмно-лиловый
    Color(0xFF2A1D26), // тёмно-розовый
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5C6BC0),
    onPrimary = Color.White,
    secondary = Color(0xFF7E57C2),
    surface = Color.White,
    onSurface = Color(0xFF1C1B22),
    onSurfaceVariant = Color(0xFF666670),
    background = Color.Transparent,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FA8DA),
    onPrimary = Color(0xFF191A2C),
    secondary = Color(0xFFB39DDB),
    surface = Color(0xFF24253A),
    onSurface = Color(0xFFE7E7F2),
    onSurfaceVariant = Color(0xFFABABC0),
    background = Color.Transparent,
)

/** Тёмная ли сейчас тема: с учётом выбора пользователя в настройках. */
@Composable
fun isDarkTheme(): Boolean = when (AppSettings.themeMode.value) {
    AppSettings.THEME_LIGHT -> false
    AppSettings.THEME_DARK -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun AppGradientBrush(): Brush = Brush.verticalGradient(
    if (isDarkTheme()) DarkGradientColors else LightGradientColors,
)

@Composable
fun CardHolderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDarkTheme()) DarkScheme else LightScheme,
        typography = InterTypography,
        content = content,
    )
}

/** Фон приложения: градиент на весь экран + полупрозрачный узор (стиль Telegram). */
@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppGradientBrush()),
    ) {
        PatternOverlay(Modifier.fillMaxSize())
        content()
    }
}

/**
 * Повторяющийся полупрозрачный узор из «карт» и кружков, как на фоне
 * чатов Telegram. Раскладка детерминированная (фиксированное зерно),
 * чтобы фон не «прыгал» между экранами.
 */
@Composable
private fun PatternOverlay(modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    // В светлой теме узор темнее фона (индиго-тон градиента), в тёмной — светлее
    val shapeColor = if (dark) {
        Color.White.copy(alpha = 0.045f)
    } else {
        Color(0xFF5C6BC0).copy(alpha = 0.10f)
    }
    val density = LocalDensity.current

    Canvas(modifier) {
        val cell = with(density) { 110.dp.toPx() }
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        val rnd = Random(7)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = (col * cell + rnd.nextFloat() * cell * 0.6f) - cell * 0.3f
                val y = (row * cell + rnd.nextFloat() * cell * 0.6f) - cell * 0.3f
                val rotation = rnd.nextFloat() * 50f - 25f
                val shape = rnd.nextInt(3)
                rotate(rotation, pivot = Offset(x, y)) {
                    when (shape) {
                        0 -> drawCardShape(x, y, cell, shapeColor)
                        1 -> drawCircle(shapeColor, radius = cell * 0.16f, center = Offset(x, y))
                        else -> drawCardShape(x, y, cell, shapeColor)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCardShape(x: Float, y: Float, cell: Float, color: Color) {
    val w = cell * 0.5f
    val h = w / 1.586f // пропорция банковской карты
    drawRoundRect(
        color = color,
        topLeft = Offset(x - w / 2, y - h / 2),
        size = Size(w, h),
        cornerRadius = CornerRadius(h * 0.18f),
    )
}
