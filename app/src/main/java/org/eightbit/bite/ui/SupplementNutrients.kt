// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.eightbit.bite.core.*

internal fun supplementNutrientAmount(total: NutrientTotal): String = when {
    !total.complete && total.known == 0.0 -> "—"
    !total.complete -> total.known.display() + " + ? g"
    else -> total.known.display() + " g"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupplementNutrients(values: List<Nutrients>, modifier: Modifier = Modifier) {
    val totals = listOf("Protein" to values.total { it.protein }, "Carbs" to values.total { it.carbs },
        "Sugars" to values.total { it.sugar })
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        totals.forEach { (label, amount) ->
            Text(t(label) + " " + supplementNutrientAmount(amount), style = MaterialTheme.typography.bodySmall)
        }
    }
}
