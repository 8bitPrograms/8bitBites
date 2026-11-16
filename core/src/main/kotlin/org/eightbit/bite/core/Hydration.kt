// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.core

import kotlin.math.ceil

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
