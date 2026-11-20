package app.sequi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import app.sequi.engine.StepKind

/** Sequi palette: pure-black background (OLED, battery) with one mint accent and a colour per step type. */
object SequiColors {
    val Work = Color(0xFF4ADE9A)
    val Rest = Color(0xFFFF7A6B)
    val Easy = Color(0xFF62B0FF)
    val Prep = Color(0xFFB0BAC2)
    val Heart = Color(0xFFFF5C7A)
    val Dim = Color(0xFF8A949C)
}

fun kindAccent(k: StepKind) = when (k) {
    StepKind.WORK -> SequiColors.Work
    StepKind.REST -> SequiColors.Rest
    StepKind.EASY -> SequiColors.Easy
    StepKind.PREP -> SequiColors.Prep
}

private val scheme = ColorScheme().copy(
    background = Color.Black,
    onBackground = Color(0xFFECEFF1),
    primary = SequiColors.Work,
    primaryDim = Color(0xFF38B07A),
    onPrimary = Color(0xFF00281A),
    primaryContainer = Color(0xFF123D2C),
    onPrimaryContainer = Color(0xFFC4F7DE),
    secondary = SequiColors.Easy,
    onSecondary = Color(0xFF00243F),
    secondaryContainer = Color(0xFF1D252C),
    onSecondaryContainer = Color(0xFFDDE7EF),
    tertiary = SequiColors.Rest,
    onTertiary = Color(0xFF3D0A04),
    surfaceContainerLow = Color(0xFF111416),
    surfaceContainer = Color(0xFF181C1F),
    surfaceContainerHigh = Color(0xFF22272B),
    onSurface = Color(0xFFECEFF1),
    onSurfaceVariant = SequiColors.Dim,
    outline = Color(0xFF3A4349),
    outlineVariant = Color(0xFF2A3136),
)

@Composable
fun SequiTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
