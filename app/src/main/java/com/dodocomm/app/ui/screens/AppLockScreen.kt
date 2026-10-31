package com.dodocomm.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.dodocomm.app.ui.components.PinDots
import com.dodocomm.app.ui.components.PinKeypad
import com.dodocomm.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dodocomm.app.R
import com.dodocomm.app.core.security.AppLock
import com.dodocomm.app.ui.theme.BrandGradientDark
import com.dodocomm.app.ui.theme.BrandGradientLight
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    pinSalt: String,
    pinHash: String,
    biometricEnabled: Boolean,
    onUnlock: () -> Unit,
    onBiometric: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var remaining by remember { mutableStateOf(0) }
    var autoBiometricRequested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled && !autoBiometricRequested) {
            autoBiometricRequested = true
            onBiometric()
        }
    }

    LaunchedEffect(lockedUntil) {
        if (lockedUntil <= 0L) {
            remaining = 0
            return@LaunchedEffect
        }
        while (true) {
            val left = ((lockedUntil - System.currentTimeMillis()) / 1000L).toInt()
            remaining = left.coerceAtLeast(0)
            if (left <= 0) {
                lockedUntil = 0L
                error = null
                break
            }
            delay(500)
        }
    }

    fun submit(value: String) {
        if (lockedUntil > System.currentTimeMillis()) return
        when {
            AppLock.verifyPin(value, pinSalt, pinHash) -> {
                pin = ""
                error = null
                onUnlock()
            }
            else -> {
                failedAttempts += 1
                pin = ""
                error = if (failedAttempts >= 5) {
                    lockedUntil = System.currentTimeMillis() + 30_000L
                    "尝试次数过多，请 30 秒后再试"
                } else {
                    "PIN 不正确，请重试"
                }
            }
        }
    }

    fun tapDigit(digit: Char) {
        if (lockedUntil > System.currentTimeMillis()) return
        if (pin.length >= AppLock.PIN_LENGTH) return
        pin += digit
        error = null
        if (pin.length == AppLock.PIN_LENGTH) submit(pin)
    }

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
                .padding(top = 90.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier.size(86.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.dodoco_mascot),
                        contentDescription = null,
                        modifier = Modifier.size(66.dp),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("嘟嘟通讯", style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "输入 PIN 解锁",
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(26.dp))
            PinDots(filled = pin.length, length = AppLock.PIN_LENGTH)
            Spacer(Modifier.height(18.dp))
            val message = error ?: if (remaining > 0) "请等待 $remaining 秒" else null
            Text(
                text = message ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) androidx.compose.ui.graphics.Color.White
                else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            PinKeypad(
                enabled = remaining == 0,
                onDigit = ::tapDigit,
                onBackspace = { pin = pin.dropLast(1); error = null },
            )
            if (biometricEnabled) {
                Spacer(Modifier.height(16.dp))
                IconButton(onClick = onBiometric) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = "指纹或面容解锁",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Text(
                    text = "指纹 / 面容解锁",
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "忘记 PIN 需要清除应用数据",
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
