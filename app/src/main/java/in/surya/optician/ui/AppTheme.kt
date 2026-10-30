package `in`.surya.optician.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand and semantic colours used across the app. */
object Brand {
    val Navy = Color(0xFF123A63)
    val Blue = Color(0xFF174C70)
    val Teal = Color(0xFF2677A8)
    val Ink = Color(0xFF152D43)
    val Success = Color(0xFF16804A)
    val SuccessBg = Color(0xFFE0F4E8)
    val Warning = Color(0xFFB45309)
    val WarningBg = Color(0xFFFFEFD9)
    val Danger = Color(0xFFB3261E)
    val DangerBg = Color(0xFFFDECEA)
}

private val SuryaColors = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFA),
    onPrimaryContainer = Color(0xFF102D4A),
    secondary = Brand.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F2FC),
    onSecondaryContainer = Color(0xFF163D55),
    tertiary = Brand.Warning,
    tertiaryContainer = Brand.WarningBg,
    background = Color(0xFFF4F7FB),
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = Color(0xFF5B6B7C),
    outline = Color(0xFFD5DEE8),
    outlineVariant = Color(0xFFE4EAF1),
    error = Brand.Danger,
    errorContainer = Brand.DangerBg,
    onErrorContainer = Brand.Danger,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color.White
)

private val base = Typography()

private val SuryaTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium)
)

private val SuryaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SuryaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SuryaColors,
        typography = SuryaTypography,
        shapes = SuryaShapes,
        content = content
    )
}
