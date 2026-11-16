// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.eightbit.bite.core.profileWaterEstimateMl
import org.eightbit.bite.data.Preferences
import java.time.*
import java.time.format.DateTimeFormatter

internal fun Double.inputNumber() = java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

@Composable
private fun SettingSwitch(title: String, selected: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BiteText(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(selected, change)
    }
}

@Composable
private fun NumberField(value: String, change: (String) -> Unit, label: String, valid: Boolean = true) {
    OutlinedTextField(value, change, label = { BiteText(label) }, singleLine = true, isError = !valid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun GoalSetting(label: String, enabled: Boolean, toggle: (Boolean) -> Unit, value: String, change: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingSwitch(label, enabled, toggle)
        if (enabled) NumberField(value, change, "Daily target · g", value.numeric()?.let { it > 0 && it <= 100000 } == true)
    }
}

@Composable
private fun LanguagePicker() {
    val languages = linkedMapOf("en" to "English", "tr" to "Türkçe", "de" to "Deutsch", "fr" to "Français",
        "es" to "Español", "it" to "Italiano", "pt" to "Português", "ja" to "日本語")
    val current = LocalConfiguration.current.locales[0].language
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }) { Text("${t("Language")} · ${languages[current] ?: "English"}") }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            languages.forEach { (tag, name) -> DropdownMenuItem(text = { Text(name) }, onClick = {
                menu = false
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }) }
        }
    }
}

@Composable
internal fun TrackerSettingsScreen(modifier: Modifier, prefs: Preferences, pending: Int, save: (Preferences) -> Unit,
    connect: () -> Unit, sync: () -> Unit, updates: @Composable () -> Unit, profile: @Composable () -> Unit, about: () -> Unit) {
    var name by rememberSaveable(prefs.name) { mutableStateOf(prefs.name) }
    var calories by rememberSaveable(prefs.calorieGoal) { mutableStateOf(prefs.calorieGoal.toString()) }
    var age by rememberSaveable(prefs.age) { mutableStateOf(prefs.age?.toString().orEmpty()) }
    var weight by rememberSaveable(prefs.weightKg) { mutableStateOf(prefs.weightKg?.inputNumber().orEmpty()) }
    var height by rememberSaveable(prefs.heightCm) { mutableStateOf(prefs.heightCm?.inputNumber().orEmpty()) }
    var sex by rememberSaveable(prefs.sex) { mutableStateOf(prefs.sex) }
    var sexMenu by remember { mutableStateOf(false) }
    var pEnabled by rememberSaveable(prefs.proteinGoalEnabled) { mutableStateOf(prefs.proteinGoalEnabled) }
    var cEnabled by rememberSaveable(prefs.carbGoalEnabled) { mutableStateOf(prefs.carbGoalEnabled) }
    var fEnabled by rememberSaveable(prefs.fatGoalEnabled) { mutableStateOf(prefs.fatGoalEnabled) }
    var protein by rememberSaveable(prefs.proteinGoal) { mutableStateOf(prefs.proteinGoal?.inputNumber().orEmpty()) }
    var carbs by rememberSaveable(prefs.carbGoal) { mutableStateOf(prefs.carbGoal?.inputNumber().orEmpty()) }
    var fat by rememberSaveable(prefs.fatGoal) { mutableStateOf(prefs.fatGoal?.inputNumber().orEmpty()) }
    var powder by rememberSaveable(prefs.powderGoal) { mutableStateOf(prefs.powderGoal?.inputNumber().orEmpty()) }
    var creatine by rememberSaveable(prefs.creatineGoal) { mutableStateOf(prefs.creatineGoal?.inputNumber().orEmpty()) }
    var waterGoal by rememberSaveable(prefs.waterGoalMl) { mutableStateOf(prefs.waterGoalMl.inputNumber()) }
    var autoBase by rememberSaveable(prefs.waterAutoBase) { mutableStateOf(prefs.waterAutoBase) }
    var powderWater by rememberSaveable(prefs.powderWaterMl) { mutableStateOf(prefs.powderWaterMl.inputNumber()) }
    var creatineWater by rememberSaveable(prefs.creatineWaterMl) { mutableStateOf(prefs.creatineWaterMl.inputNumber()) }
    var waterUnitDialog by remember { mutableStateOf(false) }
    var waterDetails by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val palettes = remember { loadPalettes(context) }
    var themeMenu by remember { mutableStateOf(false) }
    fun optional(value: String, min: Double, max: Double) = value.isBlank() || value.numeric()?.let { it in min..max } == true
    val validProfile = calories.toIntOrNull()?.let { it in 1..100000 } == true &&
        (age.isBlank() || age.toIntOrNull()?.let { it in 1..120 } == true) &&
        optional(weight, 10.0, 500.0) && optional(height, 50.0, 250.0)
    val validGoals = listOf(pEnabled to protein, cEnabled to carbs, fEnabled to fat).all { (enabled, number) ->
        if (enabled) number.numeric()?.let { it in 0.001..100000.0 } == true else true
    } && optional(powder, 0.001, 100000.0) && optional(creatine, 0.001, 100000.0)
    val reference = profileWaterEstimateMl(age.toIntOrNull(), sex, weight.numeric(), height.numeric())
    val validExtraWater = listOf(powderWater, creatineWater).all { it.numeric()?.let { n -> n in 0.0..5000.0 } == true }
    LazyColumn(modifier.imePadding(), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BiteText("Personal details", style = MaterialTheme.typography.titleLarge) }
        item { profile() }
        item { OutlinedTextField(name, { name = it.take(80) }, label = { BiteText("Display name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { NumberField(age, { age = it }, "Age", age.isBlank() || age.toIntOrNull()?.let { it in 1..120 } == true) }
        item { NumberField(weight, { weight = it }, "Weight · kg", optional(weight, 10.0, 500.0)) }
        item { NumberField(height, { height = it }, "Height · cm", optional(height, 50.0, 250.0)) }
        item { Box {
            OutlinedButton(onClick = { sexMenu = true }) { Text("${t("Sex")} · ${t(when (sex) { "male" -> "Male"; "female" -> "Female"; else -> "Not set" })}") }
            DropdownMenu(sexMenu, onDismissRequest = { sexMenu = false }) {
                listOf("" to "Not set", "male" to "Male", "female" to "Female").forEach { (value, label) ->
                    DropdownMenuItem(text = { BiteText(label) }, onClick = { sex = value; sexMenu = false })
                }
            }
        } }
        item { NumberField(calories, { calories = it }, "Daily energy target · kcal", calories.toIntOrNull()?.let { it in 1..100000 } == true) }
        item { Button(onClick = { save(prefs.copy(name = name.trim(), calorieGoal = calories.toInt(), age = age.toIntOrNull(),
            weightKg = weight.numeric(), heightCm = height.numeric(), sex = sex)) }, enabled = validProfile) { BiteText("Save profile") } }
        item { HorizontalDivider(); BiteText("Nutrition goals", style = MaterialTheme.typography.titleLarge)
            BiteText("Optional goals. Enable only the ones you want.", style = MaterialTheme.typography.bodySmall) }
        item { GoalSetting("Protein goal", pEnabled, { pEnabled = it }, protein, { protein = it }) }
        item { GoalSetting("Carb goal", cEnabled, { cEnabled = it }, carbs, { carbs = it }) }
        item { GoalSetting("Fat goal", fEnabled, { fEnabled = it }, fat, { fat = it }) }
        item { NumberField(powder, { powder = it }, "Protein powder target · g", optional(powder, 0.001, 100000.0)) }
        item { NumberField(creatine, { creatine = it }, "Creatine target · g", optional(creatine, 0.001, 100000.0))
            BiteText("Leave optional targets blank to remove them.", style = MaterialTheme.typography.bodySmall) }
        item { Button(enabled = validGoals, onClick = { save(prefs.copy(proteinGoalEnabled = pEnabled, proteinGoal = protein.numeric()?.takeIf { it > 0 && it <= 100000 },
            carbGoalEnabled = cEnabled, carbGoal = carbs.numeric()?.takeIf { it > 0 && it <= 100000 }, fatGoalEnabled = fEnabled, fatGoal = fat.numeric()?.takeIf { it > 0 && it <= 100000 },
            powderGoal = powder.numeric(), creatineGoal = creatine.numeric())) }) { BiteText("Save goals") } }
        item { HorizontalDivider(); BiteText("Water", style = MaterialTheme.typography.titleLarge) }
        item { SettingSwitch("Show water tracker", prefs.waterEnabled) { save(prefs.copy(waterEnabled = it)) } }
        item { SettingSwitch("Adjust base goal with my profile", autoBase, { autoBase = it }) }
        if (!autoBase || reference == null) item { NumberField(waterGoal, { waterGoal = it }, "Base water goal · ml", waterGoal.numeric()?.let { it in 1.0..100000.0 } == true) }
        item { if (reference != null) {
            BiteText("Suggested starting goal", style = MaterialTheme.typography.titleSmall)
            Text(volume(reference, prefs.waterInLiters), style = MaterialTheme.typography.titleLarge)
            if (!autoBase) TextButton(onClick = { waterGoal = reference.inputNumber() }) { BiteText("Use suggested goal") }
            BiteText("A rough planning estimate for low activity. Heat, exercise and health can change your needs.", style = MaterialTheme.typography.bodySmall)
        } else BiteText("Complete an adult profile with age, sex, height and weight for an estimate. Your custom base goal is used otherwise.", style = MaterialTheme.typography.bodySmall) }
        item { BiteText("Supplement-day extras", style = MaterialTheme.typography.titleSmall)
            BiteText("Optional personal targets, not an established water requirement. Zero keeps them off. Only supplements actually recorded for that day activate the extra.", style = MaterialTheme.typography.bodySmall) }
        item { NumberField(powderWater, { powderWater = it }, "Extra at protein powder target · ml", powderWater.numeric()?.let { it in 0.0..5000.0 } == true) }
        item { NumberField(creatineWater, { creatineWater = it }, "Extra at creatine target · ml", creatineWater.numeric()?.let { it in 0.0..5000.0 } == true)
            BiteText("Set the matching supplement target in grams above. Half the target adds half your chosen extra, up to the full daily extra. No supplement today means no extra bottles.", style = MaterialTheme.typography.bodySmall) }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            BiteText("About this suggestion", Modifier.weight(1f))
            SectionToggle(waterDetails) { waterDetails = !waterDetails }
        }
            if (waterDetails) {
                BiteText("The profile estimate uses age, sex, height and weight to estimate resting energy, assumes low activity and 20% water from food, then rounds to 50 ml. It is an adjustable planning aid, not a measured requirement.", style = MaterialTheme.typography.bodySmall)
                BiteText("Research does not establish extra litres per gram of creatine or protein powder. Dashed bottles represent only the extra you choose. They never record water as already drunk.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nationalacademies.org/read/10925/chapter/6"))) }) { BiteText("Source") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pmc.ncbi.nlm.nih.gov/articles/PMC5469049/"))) }) { BiteText("Creatine and hydration research") }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = validProfile && validExtraWater && waterGoal.numeric()?.let { it in 1.0..100000.0 } == true, onClick = {
                save(prefs.copy(waterGoalMl = waterGoal.numeric()!!, waterAutoBase = autoBase,
                    powderWaterMl = powderWater.numeric()!!, creatineWaterMl = creatineWater.numeric()!!,
                    age = age.toIntOrNull(), weightKg = weight.numeric(), heightCm = height.numeric(), sex = sex))
            }) { BiteText("Save water goal") }
            OutlinedButton(onClick = { waterUnitDialog = true }) { Text(volume(prefs.waterUnitMl, prefs.waterInLiters)) }
        }
            BiteText("Your chosen goal stays in control. Reaching it never blocks another drink.", style = MaterialTheme.typography.bodySmall)
        }
        item { HorizontalDivider(); BiteText("Dashboard", style = MaterialTheme.typography.titleLarge) }
        item { SettingSwitch("Show supplements", prefs.showSupplements) { save(prefs.copy(showSupplements = it)) } }
        item { SettingSwitch("Show trends", prefs.showTrends) { save(prefs.copy(showTrends = it)) } }
        item { HorizontalDivider(); BiteText("Appearance", style = MaterialTheme.typography.titleLarge) }
        item { LanguagePicker() }
        if (prefs.themesUnlocked) item { Box {
            OutlinedButton(onClick = { themeMenu = true }) { BiteText(palettes.find { it.id == prefs.theme }?.name ?: "Material You · wallpaper colors") }
            DropdownMenu(themeMenu, onDismissRequest = { themeMenu = false }) {
                DropdownMenuItem(text = { BiteText("Material You · wallpaper colors") }, onClick = { save(prefs.copy(theme = "system")); themeMenu = false })
                palettes.forEach { palette -> DropdownMenuItem(text = { Text(palette.name) }, onClick = { save(prefs.copy(theme = palette.id)); themeMenu = false }) }
            }
        } }
        if (prefs.theme == "system" || !prefs.themesUnlocked) item { SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("system", "light", "dark").forEachIndexed { index, mode -> SegmentedButton(selected = prefs.appearance == mode,
                onClick = { save(prefs.copy(appearance = mode)) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { BiteText(mode.replaceFirstChar { it.titlecase() }) } }
        } }
        item { WidgetSettings() }
        item { HorizontalDivider(); BiteText("Health Connect", style = MaterialTheme.typography.titleLarge) }
        item { BiteText("Share nutrition entries you record here. Permission is optional; the diary works offline.") }
        item { Button(onClick = connect) { BiteText("Grant nutrition permission") } }
        item { SettingSwitch("Automatic sync", prefs.autoSync) { save(prefs.copy(autoSync = it)) } }
        item { OutlinedButton(onClick = sync) { BiteText("Sync now · $pending pending") }
            BiteText(prefs.syncMessage, style = MaterialTheme.typography.bodySmall)
            if (prefs.lastSync != 0L) BiteText("Last sync: ${Instant.ofEpochMilli(prefs.lastSync).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))}", style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider(); Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { updates() } }
        item { TextButton(onClick = about) { BiteText("Privacy and about") } }
    }
    if (waterUnitDialog) WaterAmountDialog("Serving size", prefs.waterUnitMl, prefs.waterInLiters, { waterUnitDialog = false }) { ml, liters ->
        save(prefs.copy(waterUnitMl = ml, waterInLiters = liters)); waterUnitDialog = false
    }
}
