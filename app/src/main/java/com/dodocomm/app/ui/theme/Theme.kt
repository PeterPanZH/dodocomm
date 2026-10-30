package com.dodocomm.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** True when the app is currently rendering its dark theme. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** Brand gradient color stops for the current theme. */
val LocalBrandGradient = staticCompositionLocalOf { BrandGradientLight }

@Composable
fun Sms2BarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    val gradient = if (darkTheme) BrandGradientDark else BrandGradientLight

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalBrandGradient provides gradient,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Diagonal brand gradient, resolved once per theme. */
@Composable
fun brandBrush(): Brush {
    val colors = LocalBrandGradient.current
    return remember(colors) { Brush.linearGradient(colors) }
}

/** Vertical brand gradient (hero cards). */
@Composable
fun brandVerticalBrush(): Brush {
    val colors = LocalBrandGradient.current
    return remember(colors) { Brush.linearGradient(colors) }
}

/** Soft translucent glow painted on top of brand gradients. */
val BrandGlow: Color = Color.White.copy(alpha = 0.16f)
