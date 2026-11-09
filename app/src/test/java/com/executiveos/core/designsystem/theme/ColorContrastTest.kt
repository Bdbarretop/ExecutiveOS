package com.executiveos.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** La paleta es propia: esta prueba garantiza contraste WCAG AA para texto normal (4,5:1). */
class ColorContrastTest {
    @Test
    fun lightSchemeMeetsWcagAa() = assertReadable("claro", LightColorScheme)

    @Test
    fun darkSchemeMeetsWcagAa() = assertReadable("oscuro", DarkColorScheme)

    private fun assertReadable(name: String, scheme: ColorScheme) {
        val pairs = mapOf(
            "primary" to (scheme.primary to scheme.onPrimary),
            "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
            "secondary" to (scheme.secondary to scheme.onSecondary),
            "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
            "tertiary" to (scheme.tertiary to scheme.onTertiary),
            "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
            "error" to (scheme.error to scheme.onError),
            "errorContainer" to (scheme.errorContainer to scheme.onErrorContainer),
            "background" to (scheme.background to scheme.onBackground),
            "surface" to (scheme.surface to scheme.onSurface),
            "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant),
        )
        pairs.forEach { (role, colors) ->
            val ratio = contrastRatio(colors.first, colors.second)
            assertTrue("Tema $name, $role: contraste $ratio", ratio >= MINIMUM_AA_RATIO)
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private companion object {
        const val MINIMUM_AA_RATIO = 4.5f
    }
}
