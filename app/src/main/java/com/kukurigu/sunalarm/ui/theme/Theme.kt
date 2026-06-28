package com.kukurigu.sunalarm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SunriseOrange,
    onPrimary = Color.White,
    primaryContainer = SunriseOrangeContainer,
    onPrimaryContainer = Color(0xFF381300),
    secondary = SunriseGold,
    onSecondary = Color(0xFF3A2600),
    secondaryContainer = SunriseGoldContainer,
    onSecondaryContainer = Color(0xFF2A1B00),
    tertiary = SunriseAmber,
    onTertiary = Color.White,
    background = DawnPeach,
    onBackground = DawnOnSurface,
    surface = DawnSurface,
    onSurface = DawnOnSurface,
    surfaceVariant = SunriseOrangeContainer,
    onSurfaceVariant = DawnOnSurfaceVariant,
    outline = DawnOutline,
)

private val DarkColors = darkColorScheme(
    primary = NightIndigo,
    onPrimary = Color(0xFF1F1147),
    primaryContainer = NightIndigoContainer,
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = NightViolet,
    onSecondary = Color(0xFF2A1A4D),
    secondaryContainer = NightVioletContainer,
    onSecondaryContainer = Color(0xFFEDDCFF),
    tertiary = NightAmber,
    onTertiary = Color(0xFF4A2800),
    background = NightBackground,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = Color(0xFF2A2438),
    onSurfaceVariant = NightOnSurfaceVariant,
    outline = NightOutline,
)

@Composable
fun KukuriguTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KukuriguTypography,
        content = content,
    )
}
