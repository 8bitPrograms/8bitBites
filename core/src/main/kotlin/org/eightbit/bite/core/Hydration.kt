// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.core

import kotlin.math.ceil
import kotlin.math.round

enum class WaterVessel { GLASS, BOTTLE }
enum class SupplementKind(val label: String) {
    OTHER("Other supplement"), PROTEIN_POWDER("Protein powder"), CREATINE("Creatine")
}

fun suggestedSupplementKind(name: String): SupplementKind {
    val text = foldFoodText(name)
    return when {
        listOf("creatin", "kreatin").any(text::contains) -> SupplementKind.CREATINE
        listOf("whey", "protein powder", "protein tozu", "highpro", "casein", "kazein").any(text::contains) -> SupplementKind.PROTEIN_POWDER
        else -> SupplementKind.OTHER
    }
}

fun waterMl(value: String, liters: Boolean): Double? = value.trim().replace(',', '.').toDoubleOrNull()
    ?.times(if (liters) 1000.0 else 1.0)?.takeIf { it.isFinite() && it in 1.0..100000.0 }

data class HydrationPlan(val goalMl: Double, val unitMl: Double) {
    init { require(goalMl.isFinite() && goalMl in 1.0..100000.0 && unitMl.isFinite() && unitMl in 1.0..100000.0) }
    val vessel = if (unitMl >= 700.0) WaterVessel.BOTTLE else WaterVessel.GLASS
    val count = ceil(goalMl / unitMl).toInt()
    fun fill(index: Int, consumedMl: Double): Float {
        require(index in 0 until count && consumedMl.isFinite() && consumedMl >= 0)
        return ((consumedMl - index * unitMl) / unitMl).coerceIn(0.0, 1.0).toFloat()
    }
}

/** Optional adult drinking-water starting point, not an individual fluid prescription.
 * EFSA's 2.0/2.5 L total-water references include food; 80% is an explicit app estimate.
 * Weight, height, calories and supplement grams cannot determine a validated extra-water dose.
 */
fun adultWaterReferenceMl(age: Int?, sex: String): Int? {
    if (age == null || age !in 18..120) return null
    return when (sex) { "female" -> 1600; "male" -> 2000; else -> null }
}

/** A configurable planning estimate, not a measured fluid requirement.
 * Mifflin-St Jeor resting energy, a low-activity factor of 1.4, 1 ml/kcal,
 * and an explicit 20% food-water allowance. No supplement dose is inferred.
 */
fun profileWaterEstimateMl(age: Int?, sex: String, weightKg: Double?, heightCm: Double?): Double? {
    if (age == null || age !in 18..80 || weightKg == null || heightCm == null ||
        !weightKg.isFinite() || !heightCm.isFinite() || weightKg !in 35.0..250.0 || heightCm !in 120.0..230.0) return null
    val sexTerm = when (sex) { "male" -> 5; "female" -> -161; else -> return null }
    val restingKcal = 10 * weightKg + 6.25 * heightCm - 5 * age + sexTerm
    return (round(restingKcal * 1.4 * .8 / 50) * 50).takeIf { it in 800.0..6000.0 }
}

data class DailyWaterGoal(val baseMl: Double, val powderMl: Double = 0.0, val creatineMl: Double = 0.0) {
    init { require(baseMl.isFinite() && baseMl > 0 && listOf(powderMl, creatineMl).all { it.isFinite() && it >= 0 }) }
    val totalMl get() = baseMl + powderMl + creatineMl
}

/** User-selected extra at their daily target; it is activated by actual, known intake.
 * An unconsumed target contributes nothing. A day's extra is capped at the chosen plan.
 */
fun plannedWaterExtraMl(consumedGrams: Double, targetGrams: Double?, extraAtTargetMl: Double): Double {
    if (!consumedGrams.isFinite() || consumedGrams <= 0 || targetGrams == null ||
        !targetGrams.isFinite() || targetGrams <= 0 || !extraAtTargetMl.isFinite() || extraAtTargetMl <= 0) return 0.0
    return extraAtTargetMl * (consumedGrams / targetGrams).coerceIn(0.0, 1.0)
}
