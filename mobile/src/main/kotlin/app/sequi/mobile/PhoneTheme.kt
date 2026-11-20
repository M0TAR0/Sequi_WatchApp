package app.sequi.mobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.sequi.engine.StepKind

object SequiColors {
    val Work = Color(0xFF4ADE9A)
    val Rest = Color(0xFFFF7A6B)
    val Easy = Color(0xFF62B0FF)
    val Prep = Color(0xFFB0BAC2)
    val Heart = Color(0xFFFF5C7A)
}

fun kindAccent(k: StepKind) = when (k) {
    StepKind.WORK -> SequiColors.Work
    StepKind.REST -> SequiColors.Rest
    StepKind.EASY -> SequiColors.Easy
    StepKind.PREP -> SequiColors.Prep
}

private val dark = darkColorScheme(
    primary = SequiColors.Work, onPrimary = Color(0xFF00281A),
    primaryContainer = Color(0xFF123D2C), onPrimaryContainer = Color(0xFFC4F7DE),
    secondary = SequiColors.Easy, tertiary = SequiColors.Rest,
    background = Color(0xFF0B0D0F), surface = Color(0xFF0B0D0F),
    surfaceContainer = Color(0xFF181C1F), surfaceContainerHigh = Color(0xFF22272B),
)
private val light = lightColorScheme(
    primary = Color(0xFF0E8F5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F7DE), onPrimaryContainer = Color(0xFF00281A),
    secondary = Color(0xFF2F7FCC), tertiary = Color(0xFFD9503F),
)

@Composable
fun SequiTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
