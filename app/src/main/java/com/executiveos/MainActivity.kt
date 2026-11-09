package com.executiveos

import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.executiveos.core.designsystem.theme.ExecutiveOsTheme
import com.executiveos.core.designsystem.theme.resolvesToDark
import com.executiveos.core.navigation.ExecutiveOsShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val pendingTaskId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeTaskExtra(intent)
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
                ExecutiveOsShell(
                    pendingTaskId = pendingTaskId.value,
                    onPendingTaskHandled = { pendingTaskId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeTaskExtra(intent)
    }

    /** Se retira del intent para que al girar la pantalla no se vuelva a abrir la tarea. */
    private fun consumeTaskExtra(intent: Intent) {
        intent.getStringExtra(EXTRA_TASK_ID)?.let { pendingTaskId.value = it }
        intent.removeExtra(EXTRA_TASK_ID)
    }

    companion object {
        const val EXTRA_TASK_ID = "com.executiveos.extra.TASK_ID"
    }
}

private fun transparentBarStyle(darkTheme: Boolean): SystemBarStyle =
    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
