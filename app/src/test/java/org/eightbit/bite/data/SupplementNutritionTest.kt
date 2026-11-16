package org.eightbit.bite.data

import org.eightbit.bite.core.*
import org.junit.Test
import org.junit.Assert.*

class SupplementNutritionTest {
    private val whey = DiaryEntry(id = "breakfast", foodId = "personal:whey", name = "Whey", source = "label",
        grams = 30.0, mealType = 1, time = 1000, offsetSeconds = 0, kcal = 120.0, protein = 24.0,
        carbs = 3.0, fat = 2.0, fiber = .5, sugar = 1.0, sodium = .05,
        isSupplement = true, supplementKind = "PROTEIN_POWDER")
    private val dose = SupplementIntake(id = "dose", productId = "whey", name = "Whey", quantity = 30.0,
        unit = "GRAM", time = 1000, diaryEntryId = whey.id)

    @Test fun linkedDoseUsesTheRecordedMealOnceAndExcludesNormalFood() {
        val dinner = whey.copy(id = "normal-dinner", isSupplement = false, protein = 40.0)
        val values = supplementNutrientContributions(listOf(whey, dinner), listOf(dose))
        assertEquals(1, values.size)
        assertEquals("whey", values.single().productId)
        assertEquals(24.0, values.map { it.nutrients }.total { it.protein }.known, .001)
        assertEquals(3.0, values.map { it.nutrients }.total { it.carbs }.known, .001)
        assertEquals(1.0, values.map { it.nutrients }.total { it.sugar }.known, .001)
    }
    @Test fun unlinkedDoseKeepsUnknownValuesAndDoesNotLoseTheKnownSubtotal() {
        val unknown = dose.copy(id = "unknown", productId = "unlinked", diaryEntryId = null)
        val values = supplementNutrientContributions(listOf(whey), listOf(dose, unknown)).map { it.nutrients }
        val protein = values.total { it.protein }
        assertEquals(24.0, protein.known, .001)
        assertEquals(1, protein.missingEntries)
        assertFalse(protein.complete)
        assertNull(values.last().protein)
    }
    @Test fun productsStaySeparateAndEditsUseUpdatedHistoricalSnapshots() {
        val second = whey.copy(id = "second", protein = 6.0, carbs = 2.0, sugar = null)
        val secondDose = dose.copy(id = "second-dose", productId = "other", diaryEntryId = second.id)
        val values = supplementNutrientContributions(listOf(whey, second), listOf(dose, secondDose))
        assertEquals(6.0, values.filter { it.productId == "other" }.single().nutrients.protein!!, .001)
        val changed = supplementNutrientContributions(listOf(whey.copy(grams = 60.0, protein = 48.0, carbs = 6.0, sugar = 2.0)), listOf(dose.copy(quantity = 60.0)))
        assertEquals(48.0, changed.single().nutrients.protein!!, .001)
    }
    @Test fun deletedRecordsDisappearAndMissingLinkedSnapshotsStayUnknown() {
        assertTrue(supplementNutrientContributions(listOf(whey.copy(deleted = true)), listOf(dose.copy(deleted = true))).isEmpty())
        val missing = supplementNutrientContributions(emptyList(), listOf(dose)).single()
        assertEquals("whey", missing.productId)
        assertNull(missing.nutrients.protein)
        assertNull(missing.nutrients.carbs)
        assertNull(missing.nutrients.sugar)
    }
}
