// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import java.time.*
import java.time.format.DateTimeFormatter

internal fun volume(ml: Double, liters: Boolean = false): String =
    if (liters) "${java.math.BigDecimal.valueOf(ml / 1000).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} L" else "${ml.display()} ml"

@Composable
internal fun WaterVesselIcon(vessel: WaterVessel, fill: Float, modifier: Modifier = Modifier, label: String = "", dashed: Boolean = false) {
    val outline = MaterialTheme.colorScheme.primary
    val water = MaterialTheme.colorScheme.primary.copy(alpha = .65f)
    val background = MaterialTheme.colorScheme.surfaceVariant
    val description = t(if (vessel == WaterVessel.BOTTLE) "Bottle" else "Glass") + label
    Canvas(modifier.semantics { contentDescription = description; stateDescription = "${(fill * 100).toInt()}%" }) {
        val w = size.width; val h = size.height
        val shape = Path().apply {
            if (vessel == WaterVessel.BOTTLE) {
                moveTo(w * .38f, h * .06f); lineTo(w * .62f, h * .06f); lineTo(w * .62f, h * .25f)
                lineTo(w * .82f, h * .39f); lineTo(w * .82f, h * .94f); lineTo(w * .18f, h * .94f)
                lineTo(w * .18f, h * .39f); lineTo(w * .38f, h * .25f)
            } else {
                moveTo(w * .15f, h * .12f); lineTo(w * .85f, h * .12f)
                lineTo(w * .72f, h * .94f); lineTo(w * .28f, h * .94f)
            }
            close()
        }
        drawPath(shape, background)
        clipPath(shape) { drawRect(water, Offset(0f, h * (1 - fill.coerceIn(0f, 1f))), Size(w, h)) }
        drawPath(shape, outline, style = Stroke(1.7.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WaterAmountDialog(title: String, initialMl: Double, initialLiters: Boolean,
    close: () -> Unit, save: (Double, Boolean) -> Unit) {
    var liters by rememberSaveable { mutableStateOf(initialLiters) }
    var value by rememberSaveable { mutableStateOf(if (liters) (initialMl / 1000).inputNumber() else initialMl.inputNumber()) }
    val amount = waterMl(value, liters)
    AlertDialog(onDismissRequest = close, title = { BiteText(title) }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("ml", "L").forEachIndexed { index, label -> SegmentedButton(selected = liters == (index == 1),
                    onClick = { val ml = waterMl(value, liters); liters = index == 1; if (ml != null) value = if (liters) (ml / 1000).inputNumber() else ml.inputNumber() },
                    shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) } }
            }
            OutlinedTextField(value, { value = it }, label = { BiteText("Amount") }, suffix = { Text(if (liters) "L" else "ml") },
                singleLine = true, isError = amount == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(100.0, 250.0, 500.0, 700.0, 1000.0).forEach { ml ->
                    FilterChip(selected = amount == ml, onClick = { liters = ml >= 1000; value = if (liters) (ml / 1000).inputNumber() else ml.inputNumber() },
                        label = { Text(volume(ml, ml >= 1000)) })
                }
            }
            BiteText("One tap adds this amount. 700 ml and larger servings use a bottle.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = amount != null, onClick = { amount?.let { save(it, liters) } }) { BiteText("Save") } },
        dismissButton = { TextButton(onClick = close) { BiteText("Cancel") } })
}

@Composable
internal fun WaterCard(entries: List<WaterEntry>, prefs: Preferences, add: (Double) -> Unit,
    delete: (String) -> Unit, undo: () -> Unit, canUndo: Boolean, save: (Preferences) -> Unit,
    goal: DailyWaterGoal = prefs.dailyWaterGoal(), expandRequest: Long = 0) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var unitDialog by remember { mutableStateOf(false) }
    var amountDialog by remember { mutableStateOf(false) }
    val total = entries.sumOf { it.amountMl }
    val plan = HydrationPlan(goal.baseMl, prefs.waterUnitMl)
    LaunchedEffect(expandRequest) { if (expandRequest != 0L) expanded = true }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BiteText("Water", style = MaterialTheme.typography.titleMedium)
                    Text("${volume(total, prefs.waterInLiters)} / ${volume(goal.totalMl, prefs.waterInLiters)}", style = MaterialTheme.typography.bodyMedium)
                }
                FilledTonalButton(onClick = { add(prefs.waterUnitMl) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Add, t("Add water") + " " + volume(prefs.waterUnitMl, prefs.waterInLiters), Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(volume(prefs.waterUnitMl, prefs.waterInLiters), style = MaterialTheme.typography.labelMedium)
                }
                SectionToggle(expanded) { expanded = !expanded }
            }
            LinearProgressIndicator(progress = { (total / goal.totalMl).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BiteText("Base goal", style = MaterialTheme.typography.labelMedium)
                Text(volume(goal.baseMl, prefs.waterInLiters), style = MaterialTheme.typography.labelMedium)
                if (goal.powderMl + goal.creatineMl > 0) Text("+ " + volume(goal.powderMl + goal.creatineMl, prefs.waterInLiters), style = MaterialTheme.typography.labelMedium)
            }
            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WaterVesselIcon(plan.vessel, .5f, Modifier.size(24.dp, 32.dp))
                    OutlinedButton(onClick = { unitDialog = true }) { Text(volume(prefs.waterUnitMl, prefs.waterInLiters)) }
                    BiteText("Tap + to drink", style = MaterialTheme.typography.bodySmall)
                }
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.fillMaxWidth().height((minOf(4, (plan.count + 4) / 5) * 44).dp),
                    userScrollEnabled = plan.count > 20, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(plan.count) { index -> Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) {
                        WaterVesselIcon(plan.vessel, plan.fill(index, total), Modifier.size(30.dp, 38.dp), " ${index + 1} / ${plan.count}")
                    } }
                }
                var extraStart = goal.baseMl
                listOf("Protein powder" to goal.powderMl, "Creatine" to goal.creatineMl).forEach { (name, extraMl) ->
                    if (extraMl > 0) {
                        Text("${t(name)} · +${volume(extraMl, prefs.waterInLiters)}", style = MaterialTheme.typography.labelLarge)
                        val extra = HydrationPlan(extraMl, 1000.0)
                        val consumed = (total - extraStart).coerceAtLeast(0.0)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(extra.count) { index -> WaterVesselIcon(WaterVessel.BOTTLE,
                                extra.fill(index, consumed), Modifier.size(30.dp, 38.dp), " · ${t(name)} · ${volume(minOf(1000.0, extraMl - index * 1000), true)}", dashed = true) }
                        }
                        extraStart += extraMl
                    }
                }
                if (goal.powderMl + goal.creatineMl > 0) BiteText("Dashed bottles are your planned extras. Log drinks separately.", style = MaterialTheme.typography.bodySmall)
                if (total >= goal.totalMl) BiteText("Goal reached", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { amountDialog = true }) { BiteText("Custom amount") }
                    TextButton(onClick = undo, enabled = canUndo) { BiteText("Undo last drink") }
                }
                entries.forEach { entry -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val time = Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("${volume(entry.amountMl, prefs.waterInLiters)} · $time", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { delete(entry.id) }) { Icon(Icons.Default.Delete, t("Delete")) }
                } }
            }
        }
    }
    if (unitDialog) WaterAmountDialog("Serving size", prefs.waterUnitMl, prefs.waterInLiters, { unitDialog = false }) { ml, liters ->
        save(prefs.copy(waterUnitMl = ml, waterInLiters = liters)); unitDialog = false
    }
    if (amountDialog) WaterAmountDialog("Add water", prefs.waterUnitMl, prefs.waterInLiters, { amountDialog = false }) { ml, _ ->
        add(ml); amountDialog = false
    }
}
