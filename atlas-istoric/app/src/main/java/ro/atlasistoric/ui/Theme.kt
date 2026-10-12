package ro.atlasistoric.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1E5957),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1ECE6),
    secondary = Color(0xFF775C44),
    secondaryContainer = Color(0xFFF2E6D4),
    tertiary = Color(0xFF764F66),
    surface = Color(0xFFFAF9F6),
    background = Color(0xFFFAF9F6),
    surfaceVariant = Color(0xFFEAE8E2)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF97D6CD),
    primaryContainer = Color(0xFF174B47),
    secondary = Color(0xFFE1C39E),
    tertiary = Color(0xFFE6B8CF),
    surface = Color(0xFF181E20),
    background = Color(0xFF14191B),
    surfaceVariant = Color(0xFF303638)
)

@Composable fun AtlasTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
