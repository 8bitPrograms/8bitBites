// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun SectionToggle(expanded: Boolean, change: () -> Unit) {
    IconButton(onClick = change) { Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
        t(if (expanded) "Collapse" else "Expand")) }
}

@Composable
private fun GoalProgress(label: String, actual: Double, target: Double?, unit: String = "g", complete: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BiteText(label, style = MaterialTheme.typography.labelLarge)
            Text("${actual.display()}${if (complete) "" else "+?"} ${target?.let { "/ ${it.display()} " } ?: ""}$unit", style = MaterialTheme.typography.bodyMedium)
        }
        if (target != null) LinearProgressIndicator(progress = { (actual / target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MealCard(entry: DiaryEntry, edit: () -> Unit, repeat: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BiteText(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${entry.grams.display()} g · ${entry.kcal.nutrient("kcal")}", style = MaterialTheme.typography.bodyMedium)
                if (entry.isSupplement) BiteText(SupplementKind.valueOf(entry.supplementKind).label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, t("Entry actions")) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    listOf("Edit" to edit, "Repeat" to repeat, "Delete" to delete).forEach { (label, action) ->
                        DropdownMenuItem(text = { BiteText(label) }, onClick = { menu = false; action() })
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardScreen(modifier: Modifier, date: LocalDate, entries: List<DiaryEntry>, water: List<WaterEntry>,
    products: List<Supplement>, intakes: List<SupplementIntake>, prefs: Preferences, changeDate: (LocalDate) -> Unit,
    add: () -> Unit, addSupplement: () -> Unit, delete: (DiaryEntry) -> Unit, repeat: (DiaryEntry) -> Unit,
    edit: (DiaryEntry) -> Unit, addWater: (Double) -> Unit, deleteWater: (String) -> Unit, undoWater: () -> Unit,
    canUndoWater: Boolean, save: (Preferences) -> Unit, settings: () -> Unit, dose: (Supplement) -> Unit,
    editDose: (SupplementIntake) -> Unit, deleteDose: (SupplementIntake) -> Unit, editTarget: (Supplement) -> Unit,
    waterRequest: Long = 0) {
    val zone = ZoneId.systemDefault()
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val daily = entries.filter { it.time in start until end }
    val drinks = water.filter { it.time in start until end }
    val doses = intakes.filter { it.time in start until end }
    val energy = daily.map { it.nutrients() }.total { it.kcal }
    var summaryExpanded by rememberSaveable { mutableStateOf(true) }
    var supplementsExpanded by rememberSaveable { mutableStateOf(false) }
    var trendsExpanded by rememberSaveable { mutableStateOf(false) }
    val (powder, powderComplete) = supplementAmount(SupplementKind.PROTEIN_POWDER, daily, products, doses)
    val (creatine, creatineComplete) = supplementAmount(SupplementKind.CREATINE, daily, products, doses)
    val listState = rememberLazyListState()
    LaunchedEffect(waterRequest) { if (waterRequest != 0L && prefs.waterEnabled) listState.animateScrollToItem(3) }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(prefs, Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(prefs.name.ifBlank { "8bitBites" }, style = MaterialTheme.typography.titleMedium)
                BiteText("Your daily dashboard", style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { changeDate(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Previous day")) }
            Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { changeDate(date.plusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Next day")) }
        } }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BiteText("Energy consumed", style = MaterialTheme.typography.labelLarge)
                        Text("${energy.known.display()} / ${prefs.calorieGoal} kcal${if (energy.complete) "" else " +?"}", style = MaterialTheme.typography.titleLarge)
                    }
                    SectionToggle(summaryExpanded) { summaryExpanded = !summaryExpanded }
                }
                LinearProgressIndicator(progress = { (energy.known / prefs.calorieGoal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                if (summaryExpanded) {
                    listOf(Triple("Protein", prefs.proteinGoal.takeIf { prefs.proteinGoalEnabled }, { n: Nutrients -> n.protein }),
                        Triple("Carbs", prefs.carbGoal.takeIf { prefs.carbGoalEnabled }, { n: Nutrients -> n.carbs }),
                        Triple("Fat", prefs.fatGoal.takeIf { prefs.fatGoalEnabled }, { n: Nutrients -> n.fat })).forEach { (label, goal, select) ->
                        val total = daily.map { it.nutrients() }.total(select)
                        GoalProgress(label, total.known, goal, complete = total.complete)
                    }
                }
            }
        } }
    if (prefs.waterEnabled) item { WaterCard(drinks, prefs, addWater, deleteWater, undoWater, canUndoWater, save,
        prefs.dailyWaterGoal(powder, creatine), waterRequest) }
        if (prefs.showSupplements) item { Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BiteText("Supplements", style = MaterialTheme.typography.titleMedium)
                        Text("${t("Protein powder")} ${powder.display()}${if(powderComplete) "" else "+?"} g · ${t("Creatine")} ${creatine.display()}${if(creatineComplete) "" else "+?"} g", style = MaterialTheme.typography.bodySmall)
                    }
                    SectionToggle(supplementsExpanded) { supplementsExpanded = !supplementsExpanded }
                }
                if (supplementsExpanded) {
                    GoalProgress("Protein powder", powder, prefs.powderGoal, complete = powderComplete)
                    GoalProgress("Creatine", creatine, prefs.creatineGoal, complete = creatineComplete)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = addSupplement) { BiteText("Add supplement") }
                        TextButton(onClick = settings) { BiteText("Goals") }
                    }
                    BiteText("Powder weight and dietary protein are tracked separately.", style = MaterialTheme.typography.bodySmall)
                    products.forEach { product ->
                        val consumed = doses.filter { it.productId == product.id }.sumOf { it.quantity }
                        if (product.dailyTarget != null || product.foodId == null) {
                            ListItem(headlineContent = { BiteText(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${consumed.display()}${product.dailyTarget?.let { " / ${it.display()}" } ?: ""} ${t(DoseUnit.valueOf(product.unit).label)}") },
                                trailingContent = { Column { TextButton(onClick = { dose(product) }) { BiteText("Log dose") }
                                    TextButton(onClick = { editTarget(product) }) { BiteText("Daily target") } } })
                        }
                    }
                    doses.forEach { intake ->
                        val entry = intake.diaryEntryId?.let { id -> daily.find { it.id == id } }
                        var menu by remember(intake.id) { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                BiteText(intake.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${intake.quantity.display()} ${t(DoseUnit.valueOf(intake.unit).label)}${entry?.let { " · ${t(meals[it.mealType] ?: "Snack")}" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, t("Entry actions")) }
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { BiteText("Edit") }, onClick = { menu = false; if (entry != null) edit(entry) else editDose(intake) })
                                    DropdownMenuItem(text = { BiteText("Delete") }, onClick = { menu = false; deleteDose(intake) })
                                }
                            }
                        }
                    }
                }
            }
        } }
        if (prefs.showTrends) item { Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BiteText("Last seven days", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    SectionToggle(trendsExpanded) { trendsExpanded = !trendsExpanded }
                }
                if (trendsExpanded) (0L..6L).forEach { ago ->
                    val day = date.minusDays(ago)
                    val a = day.atStartOfDay(zone).toInstant().toEpochMilli(); val b = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val kcal = entries.filter { it.time in a until b }.map { it.nutrients() }.total { it.kcal }
                    val ml = water.filter { it.time in a until b }.sumOf { it.amountMl }
                    Column { Text(day.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = MaterialTheme.typography.labelMedium)
                        Text("${kcal.known.display()}${if (kcal.complete) "" else "+?"} kcal · ${volume(ml, prefs.waterInLiters)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        } }
        item { BiteText("Meals", style = MaterialTheme.typography.titleLarge) }
        if (daily.isEmpty()) item { BiteText("Your diary is empty. Add a food from your catalogue or create a personal entry.")
            TextButton(onClick = add) { BiteText("Browse foods") } }
        meals.forEach { (meal, label) ->
            val group = daily.filter { it.mealType == meal }
            if (group.isNotEmpty()) {
                item { BiteText(label, style = MaterialTheme.typography.titleMedium) }
                items(group, key = { it.id }) { entry -> MealCard(entry, { edit(entry) }, { repeat(entry) }, { delete(entry) }) }
            }
        }
    }
}
