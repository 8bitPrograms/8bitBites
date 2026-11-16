// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.core

enum class DoseUnit(val label: String) {
    GRAM("g"), MILLIGRAM("mg"), SCOOP("scoop"), CAPSULE("capsule"), TABLET("tablet"), MILLILITER("ml")
}

/** Converts product amount, not active ingredient or protein content, to food weight. */
fun doseGrams(quantity: Double, unit: DoseUnit, gramsPerUnit: Double? = null): Double? {
    require(quantity.isFinite() && quantity > 0) { "Enter a positive dose" }
    require(gramsPerUnit == null || (gramsPerUnit.isFinite() && gramsPerUnit > 0)) {
        "Serving weight must be positive"
    }
    val factor = when (unit) {
        DoseUnit.GRAM -> 1.0
        DoseUnit.MILLIGRAM -> .001
        else -> gramsPerUnit
    } ?: return null
    return (quantity * factor).also { require(it.isFinite()) { "Dose is too large" } }
}
