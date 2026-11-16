// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun Double.amount() = java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
private fun String.positive() = replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

@Composable
fun SupplementDoseEditor(product: Supplement, intake: SupplementIntake?, onDismiss: () -> Unit, save: (Double) -> Unit) {
    var quantity by rememberSaveable(product.id, intake?.id) { mutableStateOf(intake?.quantity?.amount() ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { BiteText(product.name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(quantity, { quantity = it }, label = { BiteText("Amount · ${DoseUnit.valueOf(product.unit).label}") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (product.foodId != null) BiteText("Nutrition is recorded once in your food diary.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = quantity.positive() != null, onClick = { quantity.positive()?.let(save) }) {
        BiteText(if (intake == null) "Log dose" else "Update dose")
    } }, dismissButton = { TextButton(onClick = onDismiss) { BiteText("Cancel") } })
}

@Composable
fun SupplementTargetEditor(product: Supplement, onDismiss: () -> Unit, save: (Double?) -> Unit) {
    var target by rememberSaveable(product.id) { mutableStateOf(product.dailyTarget?.amount() ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { BiteText("Daily target") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BiteText(product.name)
            OutlinedTextField(target, { target = it }, label = { BiteText("Amount · ${DoseUnit.valueOf(product.unit).label}") },
                supportingText = { BiteText("Leave blank to remove the target") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }, confirmButton = { TextButton(enabled = target.isBlank() || target.positive() != null, onClick = { save(target.positive()) }) { BiteText("Save target") } },
        dismissButton = { TextButton(onClick = onDismiss) { BiteText("Cancel") } })
}
