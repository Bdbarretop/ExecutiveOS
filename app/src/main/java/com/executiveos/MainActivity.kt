package com.executiveos

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.core.designsystem.theme.ExecutiveOsTheme
import com.executiveos.core.designsystem.theme.resolvesToDark
import com.executiveos.core.navigation.ExecutiveOsShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val mode = themeMode ?: return@setContent
            val darkTheme = mode.resolvesToDark(isSystemInDarkTheme())

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = transparentBarStyle(darkTheme),
                    navigationBarStyle = transparentBarStyle(darkTheme),
                )
                onDispose {}
            }

            ExecutiveOsTheme(darkTheme = darkTheme) {
                ExecutiveOsShell()
            }
        }
    }
}

private fun transparentBarStyle(darkTheme: Boolean): SystemBarStyle =
    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
