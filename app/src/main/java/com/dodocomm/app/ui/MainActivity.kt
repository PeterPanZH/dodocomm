package com.dodocomm.app.ui

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dodocomm.app.appContainer
import com.dodocomm.app.core.settings.ThemeMode
import com.dodocomm.app.ui.screens.AppLockScreen
import com.dodocomm.app.ui.theme.Sms2BarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : FragmentActivity() {
    private val _locked = MutableStateFlow(false)
    private val locked: StateFlow<Boolean> = _locked
    private var backgroundAt: Long = 0L

    /**
     * "后台隐藏" option: when the user leaves the app (Home/Recents), remove
     * this activity's task so it does not appear in the recent-tasks list. The
     * foreground service, notification and SMS receiver are unaffected.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (appContainer.settings.current().hideFromRecents) {
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val current = appContainer.settings.current()
        val wasUnlocked = savedInstanceState?.getBoolean(KEY_UNLOCKED, false) ?: false
        _locked.value = current.appLockEnabled && !wasUnlocked && current.appLockPinHash.isNotBlank()
        backgroundAt = savedInstanceState?.getLong(KEY_BACKGROUND_AT, 0L) ?: 0L
        applyLockWindowFlags(current.appLockEnabled)
        setContent {
            val viewModel: AppViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val isLocked by locked.collectAsStateWithLifecycle()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Keep system bar icon contrast in sync with the in-app theme.
            LaunchedEffect(darkTheme) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(transparent)
                    } else {
                        SystemBarStyle.light(transparent, transparent)
                    },
                )
            }

            LaunchedEffect(settings.appLockEnabled) {
                applyLockWindowFlags(settings.appLockEnabled)
                if (!settings.appLockEnabled) _locked.value = false
            }

            Sms2BarkTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                if (isLocked) {
                    AppLockScreen(
                        pinSalt = settings.appLockPinSalt,
                        pinHash = settings.appLockPinHash,
                        biometricEnabled = settings.biometricUnlock && biometricAvailable(),
                        onUnlock = { unlockApp() },
                        onBiometric = { requestBiometricUnlock() },
                    )
                } else {
                    Sms2BarkRoot(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val settings = appContainer.settings.current()
        if (settings.appLockEnabled && !_locked.value && settings.appLockPinHash.isNotBlank() && backgroundAt > 0L) {
            val timeoutMs = settings.autoLockMinutes.coerceAtLeast(0) * 60_000L
            val elapsed = SystemClock.elapsedRealtime() - backgroundAt
            if (timeoutMs == 0L || elapsed >= timeoutMs) _locked.value = true
        }
    }

    override fun onStop() {
        if (appContainer.settings.current().appLockEnabled) {
            backgroundAt = SystemClock.elapsedRealtime()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UNLOCKED, !_locked.value)
        outState.putLong(KEY_BACKGROUND_AT, backgroundAt)
        super.onSaveInstanceState(outState)
    }

    private fun unlockApp() {
        _locked.value = false
        backgroundAt = 0L
    }

    private fun applyLockWindowFlags(enabled: Boolean) {
        if (enabled) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!enabled)
        }
    }

    private fun biometricAvailable(): Boolean =
        runCatching {
            BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)

    private fun requestBiometricUnlock() {
        if (!biometricAvailable()) return
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlockApp()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("解锁嘟嘟通讯")
            .setSubtitle("验证指纹或面容")
            .setNegativeButtonText("使用 PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        runCatching { prompt.authenticate(info) }
    }

    private companion object {
        const val KEY_UNLOCKED = "app_lock_unlocked"
        const val KEY_BACKGROUND_AT = "app_lock_background_at"
    }
}
