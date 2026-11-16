// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.core

/** Null means unreported. Every food stores nutrients per 100 g. */
data class Nutrients(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val sodium: Double? = null,
) {
    init {
        listOf(kcal, protein, carbs, fat, fiber, sugar, sodium).forEach {
            require(it == null || (it.isFinite() && it >= 0)) { "Nutrients must be finite and non-negative" }
        }
    }

    fun portion(grams: Double): Nutrients {
        require(grams.isFinite() && grams > 0) { "Enter a positive weight in grams" }
        return scaled(grams / 100.0)
    }

    fun scaled(factor: Double): Nutrients {
        require(factor.isFinite() && factor > 0) { "Scale must be positive" }
        return Nutrients(kcal?.times(factor), protein?.times(factor), carbs?.times(factor),
            fat?.times(factor), fiber?.times(factor), sugar?.times(factor), sodium?.times(factor))
    }
}

data class NutrientTotal(val known: Double, val missingEntries: Int) {
    val complete get() = missingEntries == 0
}

fun Iterable<Nutrients>.total(value: (Nutrients) -> Double?): NutrientTotal {
    var sum = 0.0
    var missing = 0
    for (nutrients in this) {
        val amount = value(nutrients)
        if (amount == null) missing++ else sum += amount
    }
    return NutrientTotal(sum, missing)
}

/** Conservative text helper, not an inference model: unsupported input stays unresolved. */
data class FoodMention(val name: String, val grams: Double)

fun parseGramMention(text: String): FoodMention? {
    val match = Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:g|gr|gram|grams)\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
        .matchEntire(text) ?: return null
    val grams = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    if (!grams.isFinite() || grams <= 0) return null
    return FoodMention(match.groupValues[2], grams)
}
