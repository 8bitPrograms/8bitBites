// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import org.eightbit.bite.data.Preferences
import org.json.JSONArray

data class BitePalette(val id: String, val name: String, val dark: Boolean,
    val background: Color, val foreground: Color, val accent: Color)

fun loadPalettes(context: android.content.Context): List<BitePalette> {
    val array = JSONArray(context.assets.open("themes/catalog.json").bufferedReader().use { it.readText() })
    fun color(text: String) = Color(android.graphics.Color.parseColor(text))
    return (0 until array.length()).map { index -> array.getJSONObject(index).let {
        BitePalette(it.getString("id"), it.getString("name"), it.getBoolean("dark"),
            color(it.getString("background")), color(it.getString("foreground")), color(it.getString("accent")))
    } }
}

@Composable
fun BiteTheme(preferences: Preferences, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val palettes = remember { loadPalettes(context) }
    val palette = if(preferences.themesUnlocked) palettes.find { it.id == preferences.theme } else null
    val dark = palette?.dark ?: when (preferences.appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    SideEffect {
        (context as? android.app.Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = if (palette == null) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme(primary = Color(0xFF9ED4AA))
        else lightColorScheme(primary = Color(0xFF386A4A))
    } else {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val bg = palette.background
        val fg = palette.foreground
        val container = lerp(bg, palette.accent, if (dark) .18f else .12f)
        val onAccent = if (palette.accent.luminance() > .179f) Color.Black else Color.White
        base.copy(primary = palette.accent, onPrimary = onAccent,
            primaryContainer = container, onPrimaryContainer = fg,
            secondary = palette.accent, onSecondary = onAccent,
            secondaryContainer = container, onSecondaryContainer = fg,
            background = bg, onBackground = fg, surface = bg, onSurface = fg,
            surfaceVariant = lerp(bg, fg, .08f), onSurfaceVariant = lerp(fg, bg, .2f),
            surfaceContainerLowest = bg, surfaceContainerLow = lerp(bg, fg, .035f),
            surfaceContainer = lerp(bg, fg, .06f), surfaceContainerHigh = lerp(bg, fg, .09f),
            surfaceContainerHighest = lerp(bg, fg, .12f), outline = lerp(bg, fg, .5f),
            outlineVariant = lerp(bg, fg, .2f))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
