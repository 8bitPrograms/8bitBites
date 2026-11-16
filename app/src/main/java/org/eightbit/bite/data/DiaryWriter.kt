// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.data

import org.eightbit.bite.core.DoseUnit
import org.eightbit.bite.core.SupplementKind
import org.eightbit.bite.core.doseGrams

/** Called inside the caller's Room transaction: one meal row and at most one supplement intake. */
suspend fun writeFoodDiary(dao: BiteDao, food: Food, entry: DiaryEntry) {
    require(entry.foodId == food.id)
    dao.saveFood(food)
    dao.saveEntry(entry)
    // Reuse legacy IDs even after unchecking: the unique diary ID also covers tombstones.
    val previous = dao.anyIntakeForDiary(entry.id)
    if (!entry.isSupplement) {
        previous?.let { dao.deleteIntake(it.id) }
        return
    }
    SupplementKind.valueOf(entry.supplementKind)
    val product = previous?.let { dao.supplement(it.productId) } ?: dao.supplementForFood(food.id)
        ?: Supplement(id = "food:${food.id}", name = food.name, foodId = food.id)
    val factor = doseGrams(1.0, DoseUnit.valueOf(product.unit), product.gramsPerUnit)
        ?: error("Enter the weight of one serving")
    dao.saveSupplement(product.copy(kind = entry.supplementKind))
    dao.saveIntake(SupplementIntake(id = previous?.id ?: "meal:${entry.id}", productId = product.id,
        name = product.name, quantity = entry.grams / factor, unit = product.unit,
        time = entry.time, diaryEntryId = entry.id))
}
