package org.eightbit.bite.data
import org.eightbit.bite.core.*
import org.junit.Test
import org.junit.Assert.*

class DailyHydrationTest {
    private val meal = DiaryEntry(id = "breakfast", foodId = "personal:whey", name = "Whey", source = "test",
        grams = 30.0, mealType = 1, time = 1000, offsetSeconds = 0, kcal = 120.0, protein = 24.0,
        carbs = null, fat = null, fiber = null, sugar = null, sodium = null,
        isSupplement = true, supplementKind = "PROTEIN_POWDER")
    private val product = Supplement(id = "whey", name = "Whey", kind = "PROTEIN_POWDER")

    @Test fun linkedFoodAndSupplementDoNotDoubleTheWaterExtra() {
        val linked = SupplementIntake(productId = product.id, name = "Whey", quantity = 30.0, unit = "GRAM", time = 1000, diaryEntryId = meal.id)
        val standalone = SupplementIntake(productId = product.id, name = "Whey", quantity = 15.0, unit = "GRAM", time = 1000)
        val actual = supplementAmount(SupplementKind.PROTEIN_POWDER, listOf(meal), listOf(product), listOf(linked, standalone))
        assertEquals(45.0, actual.grams, .001)
        assertTrue(actual.complete)
        assertEquals(1000.0, Preferences(powderGoal = 45.0, powderWaterMl = 1000.0).dailyWaterGoal(actual.grams).powderMl, .001)
    }
    @Test fun unknownScoopsAndDeletedEntriesDoNotInventAnAmount() {
        val scoop = product.copy(unit = "SCOOP")
        val unknown = SupplementIntake(productId = product.id, name = "Whey", quantity = 1.0, unit = "SCOOP", time = 1000)
        val actual = supplementAmount(SupplementKind.PROTEIN_POWDER, listOf(meal.copy(deleted = true)), listOf(scoop), listOf(unknown))
        assertEquals(0.0, actual.grams, .001); assertFalse(actual.complete)
        assertEquals(0.0, Preferences(powderGoal = 30.0, powderWaterMl = 1000.0).dailyWaterGoal(actual.grams).powderMl, .001)
    }
    @Test fun profileModeAndCustomModeKeepTheChosenBaseDistinctFromTheExtras() {
        val prefs = Preferences(age = 30, sex = "male", heightCm = 175.0, weightKg = 70.0, waterGoalMl = 2500.0,
            powderGoal = 30.0, powderWaterMl = 1000.0, creatineGoal = 5.0, creatineWaterMl = 1000.0)
        assertEquals(1850.0, prefs.dailyWaterGoal().totalMl, .001)
        assertEquals(2500.0, prefs.copy(waterAutoBase = false).dailyWaterGoal().totalMl, .001)
        val half = prefs.dailyWaterGoal(15.0, 0.0)
        assertEquals(2350.0, half.totalMl, .001); assertEquals(0.0, half.creatineMl, .001)
    }
}
