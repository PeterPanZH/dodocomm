package com.dodocomm.app.ui.components

import com.dodocomm.app.ui.i18n.AppStrings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared 6-digit PIN keypad used by the lock screen and setup flows. */
@Composable
fun PinKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    contentColor: Color = Color.White,
    containerColor: Color = Color.White.copy(alpha = 0.16f),
) {
    val haptic = LocalHapticFeedback.current
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else {
                        Surface(
                            onClick = {
                                if (!enabled) return@Surface
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (key == "⌫") onBackspace() else onDigit(key.first())
                            },
                            shape = CircleShape,
                            color = containerColor,
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (key == "⌫") {
                                    Icon(
                                        imageVector = Icons.Rounded.Backspace,
                                        contentDescription = AppStrings.translate("删除"),
                                        tint = contentColor,
                                        modifier = Modifier.size(24.dp),
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        fontSize = 26.sp,
                                        color = contentColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinDots(
    filled: Int,
    length: Int,
    filledColor: Color = Color.White,
    emptyColor: Color = Color.White.copy(alpha = 0.28f),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(length) { index ->
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (index < filled) filledColor else emptyColor),
            )
        }
    }
}
