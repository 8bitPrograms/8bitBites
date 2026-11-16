// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.core.view.WindowCompat
import org.eightbit.bite.data.Preferences
import org.json.JSONArray
import org.eightbit.bite.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val cleanFont=FontFamily(listOf(FontWeight.Normal,FontWeight.Medium,FontWeight.SemiBold,FontWeight.Bold).map { weight ->
    Font(R.font.inter_variable,weight=weight,variationSettings=FontVariation.Settings(FontVariation.weight(weight.weight)))
})

internal val accentColors=linkedMapOf("blue" to Color(0xFF5DA5FF),"green" to Color(0xFF43CE8C),
    "purple" to Color(0xFFB091FF),"pink" to Color(0xFFF27DB7),"orange" to Color(0xFFF9A34B))
private val lightAccents=mapOf("blue" to Color(0xFF225AA8),"green" to Color(0xFF1B6F48),
    "purple" to Color(0xFF7040AE),"pink" to Color(0xFFA5316D),"orange" to Color(0xFF9A4B10))

data class BitePalette(val id: String, val name: String, val dark: Boolean,
    val background: Color, val foreground: Color, val accent: Color)

internal fun resolvePalette(preferences:Preferences,palettes:List<BitePalette>,systemDark:Boolean):BitePalette? {
    val dark=when(preferences.appearance) { "dark"->true;"light"->false;else->systemDark }
    val clean=preferences.theme.startsWith("clean-")
    val id=if(preferences.theme=="clean-system") (if(dark) "clean-dark" else "clean-light") else preferences.theme
    return (if(clean || preferences.themesUnlocked) palettes.find { it.id==id } else null)?.let { p ->
        if(clean) p.copy(accent=(if(p.dark) accentColors else lightAccents)[preferences.accent] ?: p.accent) else p
    }
}

internal fun paletteColorScheme(palette:BitePalette):ColorScheme {
    val base=if(palette.dark) darkColorScheme() else lightColorScheme()
    val bg=palette.background;val fg=palette.foreground
    // Blend sRGB components: perceptual interpolation crushed dark surfaces towards black.
    fun surface(color:Color,amount:Float)=color.copy(alpha=amount).compositeOver(bg)
    val fill=if(!palette.dark) .14f else if(palette.accent.luminance()>.55f) .32f else .42f
    val container=surface(palette.accent,fill)
    val onAccent=if(palette.accent.luminance()>.179f) Color.Black else Color.White
    return base.copy(primary=palette.accent,onPrimary=onAccent,primaryContainer=container,onPrimaryContainer=fg,
        secondary=palette.accent,onSecondary=onAccent,secondaryContainer=container,onSecondaryContainer=fg,
        background=bg,onBackground=fg,surface=bg,onSurface=fg,
        surfaceVariant=surface(fg,.14f),onSurfaceVariant=fg.copy(alpha=.76f).compositeOver(bg),
        surfaceContainerLowest=bg,surfaceContainerLow=surface(fg,.055f),surfaceContainer=surface(fg,.10f),
        surfaceContainerHigh=surface(fg,.16f),surfaceContainerHighest=surface(fg,.22f),
        outline=surface(fg,.55f),outlineVariant=surface(fg,.30f))
}

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
    val appearanceDark = when (preferences.appearance) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val clean=preferences.theme.startsWith("clean-")
    val palette=resolvePalette(preferences,palettes,appearanceDark)
    val dark=palette?.dark ?: appearanceDark
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
    } else paletteColorScheme(palette)
    val typography=remember(clean) {
        val base=Typography()
        if(!clean) base else base.copy(
            displayLarge=base.displayLarge.copy(fontFamily=cleanFont),displayMedium=base.displayMedium.copy(fontFamily=cleanFont),displaySmall=base.displaySmall.copy(fontFamily=cleanFont),
            headlineLarge=base.headlineLarge.copy(fontFamily=cleanFont),headlineMedium=base.headlineMedium.copy(fontFamily=cleanFont),headlineSmall=base.headlineSmall.copy(fontFamily=cleanFont),
            titleLarge=base.titleLarge.copy(fontFamily=cleanFont),titleMedium=base.titleMedium.copy(fontFamily=cleanFont),titleSmall=base.titleSmall.copy(fontFamily=cleanFont),
            bodyLarge=base.bodyLarge.copy(fontFamily=cleanFont),bodyMedium=base.bodyMedium.copy(fontFamily=cleanFont),bodySmall=base.bodySmall.copy(fontFamily=cleanFont),
            labelLarge=base.labelLarge.copy(fontFamily=cleanFont),labelMedium=base.labelMedium.copy(fontFamily=cleanFont),labelSmall=base.labelSmall.copy(fontFamily=cleanFont))
    }
    MaterialTheme(colorScheme = scheme, typography=typography, content = content)
}
