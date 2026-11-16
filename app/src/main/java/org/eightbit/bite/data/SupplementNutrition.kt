// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.data

import org.eightbit.bite.core.Nutrients

data class SupplementNutrientContribution(
    val productId: String?,
    val nutrients: Nutrients,
)

/** Inputs belong to one local day. Recorded meal snapshots are authoritative;
 * a linked supplement dose must not count the same nutrition a second time.
 * An unlinked dose has no label snapshot, so its nutrient values stay unknown.
 */
fun supplementNutrientContributions(meals: List<DiaryEntry>, intakes: List<SupplementIntake>): List<SupplementNutrientContribution> {
    val active = intakes.filter { !it.deleted }
    val linked = active.filter { it.diaryEntryId != null }.groupBy { it.diaryEntryId }
    val recorded = meals.filter { !it.deleted && (it.isSupplement || it.id in linked) }.associateBy { it.id }
    val known = recorded.values.map { entry ->
        SupplementNutrientContribution(linked[entry.id]?.firstOrNull()?.productId, entry.nutrients())
    }
    val unresolved = active.filter { it.diaryEntryId == null || it.diaryEntryId !in recorded }
        .distinctBy { it.diaryEntryId?.let { id -> "meal:" + id } ?: "dose:" + it.id }
        .map { SupplementNutrientContribution(it.productId, Nutrients()) }
    return known + unresolved
}
