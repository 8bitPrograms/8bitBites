// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*

@Composable
internal fun SupplementChoice(selected: Boolean, kind: String, change: (Boolean, String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().toggleable(value = selected, role = Role.Checkbox, onValueChange = { change(it, kind) }), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected, null)
        BiteText("This is a supplement", Modifier.weight(1f))
    }
    if (selected) Box {
        OutlinedButton(onClick = { menu = true }) { BiteText(SupplementKind.valueOf(kind).label) }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            SupplementKind.entries.forEach { item -> DropdownMenuItem(text = { BiteText(item.label) },
                onClick = { change(true, item.name); menu = false }) }
        }
    }
}

@Composable
internal fun PortionEditor(food: Food, editing: DiaryEntry?, lastMeal: Int, mealSelected: (Int) -> Unit,
    initialSupplement: Boolean, onDismiss: () -> Unit, save: (Food, Double, Int, Boolean, String) -> Unit) {
    var grams by rememberSaveable(food.id, editing?.id) { mutableStateOf(editing?.grams?.inputNumber() ?: "100") }
    var meal by rememberSaveable(food.id, editing?.id) { mutableIntStateOf(editing?.mealType ?: lastMeal) }
    var supplement by rememberSaveable(food.id, editing?.id) { mutableStateOf(initialSupplement) }
    var kind by rememberSaveable(food.id, editing?.id) { mutableStateOf(editing?.supplementKind?.takeIf { editing.isSupplement }
        ?: suggestedSupplementKind(food.name).name) }
    var mealMenu by remember { mutableStateOf(false) }
    val amount = grams.numeric()?.takeIf { it > 0 && it <= 100000 }
    AlertDialog(onDismissRequest = onDismiss, title = { BiteText(if (editing == null) "Add food" else "Edit portion") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BiteText(food.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            OutlinedTextField(grams, { grams = it }, label = { BiteText("Weight · grams") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = amount == null)
            Box { OutlinedButton(onClick = { mealMenu = true }) { BiteText(meals[meal] ?: "Snack") }
                DropdownMenu(mealMenu, onDismissRequest = { mealMenu = false }) { meals.forEach { (value, label) ->
                    DropdownMenuItem(text = { BiteText(label) }, onClick = { meal = value; mealSelected(value); mealMenu = false })
                } }
            }
            SupplementChoice(supplement, kind) { checked, value -> supplement = checked; kind = value }
            if (supplement) BiteText("Stays in this meal and also appears in Supplements. Nutrition is counted once.", style = MaterialTheme.typography.bodySmall)
            if (amount != null) BiteText("${food.nutrients().portion(amount).kcal.nutrient("kcal")} · ${food.nutrients().portion(amount).protein.nutrient("g protein")}")
        }
    }, confirmButton = { TextButton(enabled = amount != null, onClick = { amount?.let { save(food, it, meal, supplement, kind) } }) { BiteText("Save entry") } },
        dismissButton = { TextButton(onClick = onDismiss) { BiteText("Cancel") } })
}
