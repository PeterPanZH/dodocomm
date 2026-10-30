package com.dodocomm.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand palette: Klee / Dodoco red-pink + warm cream (see docs/UI_DESIGN_SPEC.md)
// ---------------------------------------------------------------------------

private val KleeRed = Color(0xFFD64550)
private val KleeRedSoft = Color(0xFFF08A9B)
private val KleeRose = Color(0xFFE8647A)
private val KleeGold = Color(0xFFF2B544)

/** Diagonal flow gradient used by hero cards / brand marks / switch thumbs. */
val BrandGradientLight: List<Color> = listOf(
    Color(0xFFC93A46), // deep Klee red
    Color(0xFFEF748B), // warm rose
    Color(0xFFF2B544), // clover gold
)

/** Desaturated variant for dark surfaces so white text keeps its contrast. */
val BrandGradientDark: List<Color> = listOf(
    Color(0xFF7E2530),
    Color(0xFFB34555),
    Color(0xFFC98A3C),
)

val LightColors = lightColorScheme(
    primary = KleeRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2E4),
    onPrimaryContainer = Color(0xFF4A1013),
    inversePrimary = Color(0xFFFFB3BB),
    secondary = KleeRose,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDE3),
    onSecondaryContainer = Color(0xFF4A101A),
    tertiary = KleeGold,
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = Color(0xFFFFF0D0),
    onTertiaryContainer = Color(0xFF3A2200),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF2B1B19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B1B19),
    surfaceVariant = Color(0xFFF6EAE3),
    onSurfaceVariant = Color(0xFF6F5A54),
    surfaceTint = KleeRed,
    inverseSurface = Color(0xFF3A2A28),
    inverseOnSurface = Color(0xFFFFF1EC),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0E0),
    onErrorContainer = Color(0xFF5F1212),
    outline = Color(0xFFCBB6AE),
    outlineVariant = Color(0xFFEAD8D0),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFF0E0D8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFBF8),
    surfaceContainer = Color(0xFFFBF2EC),
    surfaceContainerHigh = Color(0xFFF6EAE3),
    surfaceContainerHighest = Color(0xFFF1E2DA),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB3BB),
    onPrimary = Color(0xFF4A1015),
    primaryContainer = Color(0xFF7E2531),
    onPrimaryContainer = Color(0xFFFFE2E4),
    inversePrimary = KleeRed,
    secondary = Color(0xFFFFB3C1),
    onSecondary = Color(0xFF4A1020),
    secondaryContainer = Color(0xFF6E2938),
    onSecondaryContainer = Color(0xFFFFDDE5),
    tertiary = Color(0xFFF5C96B),
    onTertiary = Color(0xFF3A2600),
    tertiaryContainer = Color(0xFF5C4212),
    onTertiaryContainer = Color(0xFFFFF0D0),
    background = Color(0xFF1A0F10),
    onBackground = Color(0xFFF8E9E4),
    surface = Color(0xFF241517),
    onSurface = Color(0xFFF8E9E4),
    surfaceVariant = Color(0xFF332024),
    onSurfaceVariant = Color(0xFFCBB2AA),
    surfaceTint = Color(0xFFFFB3BB),
    inverseSurface = Color(0xFFF8E9E4),
    inverseOnSurface = Color(0xFF33201F),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFE0E0),
    outline = Color(0xFF7B625C),
    outlineVariant = Color(0xFF4A3430),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3A2528),
    surfaceDim = Color(0xFF120A0B),
    surfaceContainerLowest = Color(0xFF100809),
    surfaceContainerLow = Color(0xFF1D1113),
    surfaceContainer = Color(0xFF241517),
    surfaceContainerHigh = Color(0xFF2F1C1F),
    surfaceContainerHighest = Color(0xFF3A2528),
)

// ---------------------------------------------------------------------------
// Semantic status colors (chips / stat pills). Kept fixed so "已送达" stays green.
// ---------------------------------------------------------------------------

data class TonalPair(val container: Color, val content: Color)

val SentLight = TonalPair(Color(0xFFE3F8EC), Color(0xFF0B7A4B))
val SentDark = TonalPair(Color(0xFF102E22), Color(0xFF6EE7B7))

val PendingLight = TonalPair(Color(0xFFFDF1DC), Color(0xFF92400E))
val PendingDark = TonalPair(Color(0xFF332610), Color(0xFFFBBF24))

val FilteredLight = TonalPair(Color(0xFFF6E7E0), Color(0xFF7A5A52))
val FilteredDark = TonalPair(Color(0xFF332024), Color(0xFFCBB2AA))

// ---------------------------------------------------------------------------
// Pastel avatar palette for sender initials (warm variants mixed with the old set).
// ---------------------------------------------------------------------------

val AvatarPaletteLight: List<TonalPair> = listOf(
    TonalPair(Color(0xFFFFE2E4), Color(0xFFB02A36)),
    TonalPair(Color(0xFFFFDDE3), Color(0xFFA82A48)),
    TonalPair(Color(0xFFFFEFD2), Color(0xFF9A5B00)),
    TonalPair(Color(0xFFFDE2E4), Color(0xFF9D2740)),
    TonalPair(Color(0xFFD1FAE5), Color(0xFF047857)),
    TonalPair(Color(0xFFFFE8D6), Color(0xFF9C4221)),
    TonalPair(Color(0xFFE0E7FF), Color(0xFF4338CA)),
    TonalPair(Color(0xFFCFFAFE), Color(0xFF0E7490)),
)

val AvatarPaletteDark: List<TonalPair> = listOf(
    TonalPair(Color(0xFF5A1A22), Color(0xFFFFB3BB)),
    TonalPair(Color(0xFF4A1425), Color(0xFFFFB3C1)),
    TonalPair(Color(0xFF4A3410), Color(0xFFFDE68A)),
    TonalPair(Color(0xFF4C0519), Color(0xFFFDA4AF)),
    TonalPair(Color(0xFF0B2E23), Color(0xFF6EE7B7)),
    TonalPair(Color(0xFF431407), Color(0xFFFDBA74)),
    TonalPair(Color(0xFF312E81), Color(0xFFC7D2FE)),
    TonalPair(Color(0xFF164E63), Color(0xFFA5F3FC)),
)
