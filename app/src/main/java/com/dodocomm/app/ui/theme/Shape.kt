package com.dodocomm.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Standard grouped settings / list card. */
val CardShape = RoundedCornerShape(20.dp)

/** Gradient hero cards. */
val HeroShape = RoundedCornerShape(28.dp)

/** Bottom sheet top corners. */
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Outlined text fields. */
val FieldShape = RoundedCornerShape(16.dp)

/** Inner tonal containers (message body, note boxes). */
val InnerShape = RoundedCornerShape(16.dp)
