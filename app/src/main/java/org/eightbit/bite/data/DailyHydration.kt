// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.data

import org.eightbit.bite.core.*

data class SupplementAmount(val grams: Double, val complete: Boolean)

/** Inputs are already filtered to one local day. Linked food/dose records count once. */
fun supplementAmount(kind: SupplementKind, meals: List<DiaryEntry>, products: List<Supplement>,
    intakes: List<SupplementIntake>): SupplementAmount {
    var grams = meals.filter { !it.deleted && it.isSupplement && it.supplementKind == kind.name }.sumOf { it.grams }
    var complete = true
    for (intake in intakes.filter { !it.deleted && it.diaryEntryId == null }) {
        products.find { it.id == intake.productId && it.kind == kind.name }?.let { product ->
            val amount = doseGrams(intake.quantity, DoseUnit.valueOf(intake.unit), product.gramsPerUnit)
            if (amount == null) complete = false else grams += amount
        }
    }
    return SupplementAmount(grams, complete)
}

fun Preferences.dailyWaterGoal(powderGrams: Double = 0.0, creatineGrams: Double = 0.0): DailyWaterGoal {
    val base = if (waterAutoBase) profileWaterEstimateMl(age, sex, weightKg, heightCm) ?: waterGoalMl else waterGoalMl
    return DailyWaterGoal(base, plannedWaterExtraMl(powderGrams, powderGoal, powderWaterMl),
        plannedWaterExtraMl(creatineGrams, creatineGoal, creatineWaterMl))
}
