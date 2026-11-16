package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

@Immutable
data class DeskSemanticColors(
    val bullEmerald: Color,
    val bullContainer: Color,
    val stopCrimson: Color,
    val stopContainer: Color,
    val amberSaffron: Color,
    val amberContainer: Color,
    val dataSky: Color,
    val cardElevated: Color,
    val borderSubtle: Color
)

val LocalDeskColors = staticCompositionLocalOf {
    DeskSemanticColors(
        bullEmerald = Color(0xFF10B981),
        bullContainer = Color(0xFF0A2E23),
        stopCrimson = Color(0xFFEF4444),
        stopContainer = Color(0xFF331518),
        amberSaffron = Color(0xFFF59E0B),
        amberContainer = Color(0xFF33250B),
        dataSky = Color(0xFF38BDF8),
        cardElevated = Color(0xFF141E2B),
        borderSubtle = Color(0xFF233144)
    )
}

private val ObsidianColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF042218),
    primaryContainer = Color(0xFF0C382A),
    onPrimaryContainer = Color(0xFF9DF2D1),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF281900),
    secondaryContainer = Color(0xFF3B2809),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF002436),
    tertiaryContainer = Color(0xFF0D3044),
    onTertiaryContainer = Color(0xFFC2EBFF),
    background = Color(0xFF0B1018),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF101722),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF172130),
    onSurfaceVariant = Color(0xFF9FB0C4),
    outline = Color(0xFF29384D),
    outlineVariant = Color(0xFF1D2838),
    error = Color(0xFFEF4444),
    onError = Color(0xFF3B0909),
    errorContainer = Color(0xFF3E1518),
    onErrorContainer = Color(0xFFFFB4AB)
)

val DeskTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

val DeskShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun TradeDeskTheme(content: @Composable () -> Unit) {
    val semantic = LocalDeskColors.current
    CompositionLocalProvider(LocalDeskColors provides semantic) {
        MaterialTheme(
            colorScheme = ObsidianColorScheme,
            typography = DeskTypography,
            shapes = DeskShapes,
            content = content
        )
    }
}
