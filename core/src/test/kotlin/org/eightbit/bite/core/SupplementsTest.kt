package org.eightbit.bite.core

import kotlin.test.*

class SupplementsTest {
    @Test fun `scoops require a measured serving weight`() {
        assertNull(doseGrams(1.0, DoseUnit.SCOOP))
        assertEquals(45.0, doseGrams(1.5, DoseUnit.SCOOP, 30.0))
    }
    @Test fun `powder mass is not protein grams`() {
        val grams = doseGrams(1.0, DoseUnit.SCOOP, 30.0)!!
        assertEquals(24.0, Nutrients(protein = 80.0).portion(grams).protein)
    }
    @Test fun `milligrams convert to food grams explicitly`() {
        assertEquals(.5, doseGrams(500.0, DoseUnit.MILLIGRAM))
        assertEquals(5.0, doseGrams(5.0, DoseUnit.GRAM))
    }
    @Test fun `editing a logged dose scales its original nutrition snapshot`() {
        val recorded = Nutrients(kcal = 120.0, protein = 24.0)
        val changed = recorded.scaled(45.0 / 30.0)
        assertEquals(180.0, changed.kcal)
        assertEquals(36.0, changed.protein)
        assertNull(changed.carbs)
    }
    @Test fun `invalid supplement amounts are rejected`() {
        assertFailsWith<IllegalArgumentException> { doseGrams(-1.0, DoseUnit.GRAM) }
        assertFailsWith<IllegalArgumentException> { doseGrams(Double.NaN, DoseUnit.SCOOP, 30.0) }
        assertFailsWith<IllegalArgumentException> { doseGrams(1.0, DoseUnit.CAPSULE, 0.0) }
    }
}
