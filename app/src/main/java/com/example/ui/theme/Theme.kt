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
    primary = AgencyCyan,
    secondary = AgencyEmerald,
    tertiary = AgencyViolet,
    background = DarkBg,
    surface = SurfaceDark,
    onPrimary = DarkBg,
    onSecondary = DarkBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outlineVariant = BorderDark
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Disabled to force dark theme
  content: @Composable () -> Unit,
) {
  // Always use the DarkColorScheme for this specific branding
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
