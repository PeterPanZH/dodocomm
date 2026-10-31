@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dodocomm.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dodocomm.app.R
import com.dodocomm.app.core.security.AppLock
import com.dodocomm.app.ui.components.PinDots
import com.dodocomm.app.ui.components.PinKeypad
import com.dodocomm.app.ui.components.Text
import com.dodocomm.app.ui.theme.BrandGradientLight

private enum class PinStep { CURRENT, NEW, CONFIRM }

@Composable
fun AppLockPinScreen(
    mode: String,
    onDismiss: () -> Unit,
    onSubmit: (current: String, newPin: String, confirmPin: String) -> String?,
) {
    val steps = when (mode) {
        "setup" -> listOf(PinStep.NEW, PinStep.CONFIRM)
        "change" -> listOf(PinStep.CURRENT, PinStep.NEW, PinStep.CONFIRM)
        else -> listOf(PinStep.CURRENT)
    }
    var stepIndex by remember { mutableStateOf(0) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    val title = when (mode) {
        "setup" -> "设置应用锁"
        "change" -> "修改 PIN"
        else -> "关闭应用锁"
    }
    val instruction = when (step) {
        PinStep.CURRENT -> "输入当前 6 位 PIN"
        PinStep.NEW -> "设置 6 位 PIN"
        PinStep.CONFIRM -> "再次输入确认"
    }

    fun submit(value: String) {
        error = null
        val nextIndex = stepIndex + 1
        when (step) {
            PinStep.CURRENT -> {
                currentPin = value
                stepIndex = nextIndex
                entry = ""
            }
            PinStep.NEW -> {
                newPin = value
                stepIndex = nextIndex
                entry = ""
            }
            PinStep.CONFIRM -> {
                confirmPin = value
                val result = onSubmit(currentPin, newPin, value)
                if (result == null) {
                    onDismiss()
                } else {
                    error = result
                    stepIndex = 0
                    currentPin = ""
                    newPin = ""
                    confirmPin = ""
                    entry = ""
                }
            }
        }
    }

    fun tapDigit(digit: Char) {
        if (entry.length >= AppLock.PIN_LENGTH) return
        entry += digit
        if (entry.length == AppLock.PIN_LENGTH) {
            val value = entry
            entry = ""
            submit(value)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(BrandGradientLight[0], BrandGradientLight[1], BrandGradientLight[2]),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp)
                    .padding(top = 24.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "取消",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(34.dp))
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    modifier = Modifier.size(76.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.dodoco_mascot),
                            contentDescription = null,
                            modifier = Modifier.size(58.dp),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "6 位数字，用于解锁嘟嘟通讯",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(24.dp))
                PinDots(filled = entry.length, length = AppLock.PIN_LENGTH)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error ?: " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                PinKeypad(
                    enabled = true,
                    onDigit = ::tapDigit,
                    onBackspace = { entry = entry.dropLast(1); error = null },
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "忘记 PIN 只能清除应用数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}
