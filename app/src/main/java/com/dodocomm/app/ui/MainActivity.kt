package com.dodocomm.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dodocomm.app.appContainer
import com.dodocomm.app.core.settings.ThemeMode
import com.dodocomm.app.ui.theme.Sms2BarkTheme

class MainActivity : ComponentActivity() {
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
        setContent {
            val viewModel: AppViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

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

            Sms2BarkTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                Sms2BarkRoot(viewModel = viewModel)
            }
        }
    }
}
