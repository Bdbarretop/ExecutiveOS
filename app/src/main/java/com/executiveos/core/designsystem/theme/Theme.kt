package com.executiveos.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.executiveos.domain.model.ThemeMode

/** Sin color dinámico: la identidad visual no debe depender del fondo de pantalla del usuario. */
@Composable
fun ExecutiveOsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ExecutiveTypography,
        shapes = ExecutiveShapes,
        content = content,
    )
}

fun ThemeMode.resolvesToDark(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
