package dev.anvil.ide.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anvil.ide.R

@Immutable
class AnvilColors(
    val dark: Boolean,
    /** Editor surface. */
    val bg: Color,
    /** Chrome: sidebar, top bar, panels. */
    val surface: Color,
    /** Raised elements: dialogs, menus, inputs. */
    val raised: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val selected: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
)

val DarkColors = AnvilColors(
    dark = true,
    bg = Color(0xFF1B1E24),
    surface = Color(0xFF15181D),
    raised = Color(0xFF21252C),
    border = Color(0xFF2B3039),
    text = Color(0xFFC9CFDA),
    textDim = Color(0xFF8A93A2),
    textFaint = Color(0xFF5B6371),
    accent = Color(0xFF5A9CF8),
    onAccent = Color(0xFF0D1522),
    selected = Color(0xFF263449),
    success = Color(0xFF6DBE82),
    warning = Color(0xFFE2B15C),
    danger = Color(0xFFE5707A),
)

val LightColors = AnvilColors(
    dark = false,
    bg = Color(0xFFFCFCFD),
    surface = Color(0xFFF1F3F6),
    raised = Color(0xFFFFFFFF),
    border = Color(0xFFDCDFE5),
    text = Color(0xFF1E222A),
    textDim = Color(0xFF5E6675),
    textFaint = Color(0xFF99A0AD),
    accent = Color(0xFF2D6BD8),
    onAccent = Color(0xFFFFFFFF),
    selected = Color(0xFFDDE8FA),
    success = Color(0xFF2F8F4E),
    warning = Color(0xFFB7791F),
    danger = Color(0xFFC73E49),
)

val LocalAnvil = staticCompositionLocalOf { DarkColors }

object A {
    val c: AnvilColors
        @Composable @ReadOnlyComposable get() = LocalAnvil.current
}

val UiFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val CodeFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private fun ui(size: Float, weight: FontWeight = FontWeight.Normal, line: Float = size * 1.4f) =
    TextStyle(fontFamily = UiFont, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)

val AnvilTypography = Typography(
    titleLarge = ui(20f, FontWeight.SemiBold),
    titleMedium = ui(15f, FontWeight.SemiBold),
    titleSmall = ui(13.5f, FontWeight.Medium),
    bodyLarge = ui(14f),
    bodyMedium = ui(13f),
    bodySmall = ui(12f),
    labelLarge = ui(13f, FontWeight.Medium),
    labelMedium = ui(12f, FontWeight.Medium),
    labelSmall = ui(11f, FontWeight.Medium),
)

@Composable
fun AnvilTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.bg, onBackground = c.text,
            surface = c.raised, onSurface = c.text,
            surfaceVariant = c.surface, onSurfaceVariant = c.textDim,
            outline = c.border, outlineVariant = c.border,
            surfaceContainer = c.raised, surfaceContainerHigh = c.raised,
            surfaceContainerHighest = c.raised, surfaceContainerLow = c.surface,
            surfaceContainerLowest = c.surface,
            error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            background = c.bg, onBackground = c.text,
            surface = c.raised, onSurface = c.text,
            surfaceVariant = c.surface, onSurfaceVariant = c.textDim,
            outline = c.border, outlineVariant = c.border,
            surfaceContainer = c.raised, surfaceContainerHigh = c.raised,
            surfaceContainerHighest = c.raised, surfaceContainerLow = c.surface,
            surfaceContainerLowest = c.surface,
            error = c.danger,
        )
    }
    CompositionLocalProvider(LocalAnvil provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AnvilTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(10.dp),
                extraLarge = RoundedCornerShape(12.dp),
            ),
            content = content,
        )
    }
}
