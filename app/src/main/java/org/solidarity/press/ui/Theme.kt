package org.solidarity.press.ui

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
import org.solidarity.press.model.Country

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF2B4C9B),
    onTertiary = Color.White,
    background = Color(0xFFFCF8F6),
    onBackground = Color(0xFF1E1210),
    surface = Color(0xFFFCF8F6),
    onSurface = Color(0xFF1E1210),
    surfaceVariant = Color(0xFFF4DED9),
    onSurfaceVariant = Color(0xFF52443F),
    surfaceContainer = Color(0xFFF7ECE8),
    surfaceContainerHigh = Color(0xFFF1E5E1),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD7C2BC),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFB3C5FF),
    onTertiary = Color(0xFF1B2F6B),
    background = Color(0xFF17100F),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF17100F),
    onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF52443F),
    onSurfaceVariant = Color(0xFFD7C2BC),
    surfaceContainer = Color(0xFF221917),
    surfaceContainerHigh = Color(0xFF2D2320),
    outline = Color(0xFF9F8D87),
    outlineVariant = Color(0xFF52443F),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)

@Composable
fun SolidarityTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

fun countryColor(country: Country?): Color = when (country) {
    Country.IT -> Color(0xFF2E7D46)
    Country.FR -> Color(0xFF2B4C9B)
    Country.UK -> Color(0xFF9A1B34)
    null -> Color(0xFF6B625E)
}
