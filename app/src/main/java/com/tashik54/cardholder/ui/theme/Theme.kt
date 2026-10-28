package com.tashik54.cardholder.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tashik54.cardholder.R

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
private val GradientColors = listOf(
    Color(0xFFDDE9FF), // нежно-голубой
    Color(0xFFE8DDFF), // лавандовый
    Color(0xFFFFE3EE), // розовый
)

@Composable
fun AppGradientBrush(): Brush = Brush.verticalGradient(GradientColors)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5C6BC0),
    onPrimary = Color.White,
    secondary = Color(0xFF7E57C2),
    surface = Color.White,
    onSurface = Color(0xFF1C1B22),
    onSurfaceVariant = Color(0xFF666670),
    background = Color.Transparent,
)

@Composable
fun CardHolderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = InterTypography,
        content = content,
    )
}

/** Фон приложения: градиент на весь экран под всем контентом. */
@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppGradientBrush()),
    ) {
        content()
    }
}
