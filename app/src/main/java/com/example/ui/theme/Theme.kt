package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SoftAzure,
    secondary = DarkDeepBlue,
    tertiary = TextSecondary,
    background = DarkSlate,
    surface = CloudSurface,
    onPrimary = DarkDeepBlue,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = MutedBorder,
    surfaceVariant = PillSurface
  )

private val LightColorScheme = DarkColorScheme // Keep consistent dark aesthetic as requested

@Composable
fun DriveJetTheme(
  darkTheme: Boolean = true, // Default to dark for the "handcrafted" vibe
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Disable dynamic color to maintain strict aesthetic
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
