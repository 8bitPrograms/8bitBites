package org.eightbit.bite.core

import kotlin.test.*

class NutritionTest {
    @Test fun `portion scales values and preserves unknown nutrients`() {
        val portion = Nutrients(kcal = 120.0, protein = 10.0).portion(250.0)
        assertEquals(300.0, portion.kcal)
        assertEquals(25.0, portion.protein)
        assertNull(portion.carbs)
    }
    @Test fun `incomplete totals disclose missing entries`() {
        val sum = listOf(Nutrients(protein = 10.0), Nutrients(), Nutrients(protein = 3.0)).total { it.protein }
        assertEquals(13.0, sum.known)
        assertEquals(1, sum.missingEntries)
        assertFalse(sum.complete)
    }
    @Test fun `invalid weights cannot enter a diary`() {
        for (grams in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Nutrients().portion(grams) }
        }
    }
    @Test fun `invalid source data is rejected`() {
        assertFailsWith<IllegalArgumentException> { Nutrients(kcal = -1.0) }
        assertFailsWith<IllegalArgumentException> { Nutrients(protein = Double.NaN) }
    }
    @Test fun `Turkish decimal input can be parsed without inventing a food match`() {
        assertEquals(FoodMention("beyaz peynir", 100.5), parseGramMention("100,5 gram beyaz peynir"))
        assertEquals(FoodMention("cheese", 100.0), parseGramMention("100 grams cheese"))
        assertNull(parseGramMention("a bowl of cheese"))
        assertNull(parseGramMention("0 g cheese"))
    }
}
