// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.eightbit.bite.data.Preferences

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ThemePicker(prefs:Preferences,save:(Preferences)->Unit) {
    val context=LocalContext.current
    val palettes=remember { loadPalettes(context) }
    var menu by remember { mutableStateOf(false) }
    val choices=linkedMapOf(
        "clean-dark" to ai("Dark · Charcoal","Koyu · Kömür grisi"),
        "clean-amoled" to ai("AMOLED · Black","AMOLED · Siyah"),
        "clean-midnight" to ai("Midnight","Gece mavisi"),
        "clean-forest" to ai("Forest","Orman"),
        "clean-dusk" to ai("Dusk","Alacakaranlık"),
        "clean-light" to ai("Light","Açık"),
        "clean-system" to ai("System","Sistem"),
        "system" to ai("Material You · wallpaper colors","Material You · duvar kâğıdı renkleri"))
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(ai("Background","Arka plan"),style=MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick={menu=true},modifier=Modifier.testTag("theme-picker")) {
                Text(choices[prefs.theme] ?: palettes.find { it.id==prefs.theme }?.name ?: choices.getValue("system"))
            }
            DropdownMenu(menu,onDismissRequest={menu=false},modifier=Modifier.heightIn(max=420.dp)) {
                choices.forEach { (id,name) ->
                    DropdownMenuItem(text={Text(name)},trailingIcon={if(prefs.theme==id) Icon(Icons.Default.Check,null)},
                        onClick={save(prefs.copy(theme=id));menu=false})
                }
                if(prefs.themesUnlocked) {
                    HorizontalDivider()
                    palettes.filterNot { it.id.startsWith("clean-") }.forEach { palette ->
                        DropdownMenuItem(text={Text(palette.name)},leadingIcon={Box(Modifier.size(18.dp).background(palette.accent,CircleShape))},
                            onClick={save(prefs.copy(theme=palette.id));menu=false})
                    }
                }
            }
        }
        if(prefs.theme.startsWith("clean-")) {
            Text(ai("Accent color","Vurgu rengi"),style=MaterialTheme.typography.titleSmall)
            val accents=linkedMapOf("neutral" to ai("Default","Varsayılan"),"blue" to ai("Blue","Mavi"),"green" to ai("Green","Yeşil"),
                "purple" to ai("Purple","Mor"),"pink" to ai("Pink","Pembe"),"orange" to ai("Orange","Turuncu"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                accents.forEach { (id,label) ->
                    FilterChip(selected=prefs.accent==id,onClick={save(prefs.copy(accent=id))},label={Text(label)},
                        leadingIcon={Box(Modifier.size(14.dp).background(accentColors[id] ?: MaterialTheme.colorScheme.onSurface,CircleShape))})
                }
            }
        }
    }
}
