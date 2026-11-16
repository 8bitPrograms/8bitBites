package org.eightbit.bite.core

import kotlin.test.*

class HydrationTest {
    @Test fun profileEstimateRespondsToEachBodyMeasurement() {
        val base = profileWaterEstimateMl(30, "male", 70.0, 175.0)!!
        assertEquals(1850.0, base)
        assertTrue(profileWaterEstimateMl(30, "male", 90.0, 175.0)!! > base)
        assertTrue(profileWaterEstimateMl(30, "male", 70.0, 195.0)!! > base)
        assertTrue(profileWaterEstimateMl(60, "male", 70.0, 175.0)!! < base)
        assertTrue(profileWaterEstimateMl(30, "female", 70.0, 175.0)!! < base)
        assertNull(profileWaterEstimateMl(17, "male", 70.0, 175.0))
        assertNull(profileWaterEstimateMl(30, "", 70.0, 175.0))
        assertNull(profileWaterEstimateMl(30, "male", Double.NaN, 175.0))
    }
    @Test fun supplementalPlanActivatesOnlyForActualIntakeAndNeverExceedsItsDailyExtra() {
        assertEquals(0.0, plannedWaterExtraMl(0.0, 30.0, 1000.0))
        assertEquals(500.0, plannedWaterExtraMl(15.0, 30.0, 1000.0))
        assertEquals(1000.0, plannedWaterExtraMl(60.0, 30.0, 1000.0))
        assertEquals(0.0, plannedWaterExtraMl(30.0, null, 1000.0))
        assertEquals(0.0, plannedWaterExtraMl(30.0, 30.0, 0.0))
        assertEquals(4000.0, DailyWaterGoal(2000.0, 1000.0, 1000.0).totalMl)
    }
    @Test fun twentyGlassesAndPartialProgress() {
        val plan = HydrationPlan(2000.0, 100.0)
        assertEquals(20, plan.count)
        assertEquals(WaterVessel.GLASS, plan.vessel)
        assertEquals(1f, plan.fill(0, 150.0))
        assertEquals(.5f, plan.fill(1, 150.0))
        assertEquals(0f, plan.fill(2, 150.0))
    }
    @Test fun bottleBoundaryIncludesSevenHundred() {
        assertEquals(WaterVessel.GLASS, HydrationPlan(2000.0, 699.0).vessel)
        assertEquals(WaterVessel.BOTTLE, HydrationPlan(2000.0, 700.0).vessel)
        assertEquals(2, HydrationPlan(2000.0, 1000.0).count)
    }
    @Test fun customLitersAndDecimalComma() {
        assertEquals(750.0, waterMl("0,75", true))
        assertEquals(125.5, waterMl("125.5", false))
        assertEquals(1.0, waterMl("0.001", true))
        for (value in listOf("-1", "0", "NaN", "Infinity", "", "100001")) assertNull(waterMl(value, false))
    }
    @Test fun partialGoalAndExcessRemainRepresentable() {
        val plan = HydrationPlan(2050.0, 100.0)
        assertEquals(21, plan.count)
        assertEquals(.5f, plan.fill(20, 2050.0))
        assertEquals(1f, plan.fill(20, 2500.0))
        assertEquals(1, HydrationPlan(500.0, 1000.0).count)
    }
    @Test fun invalidPlansAreRejected() {
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { HydrationPlan(value, 100.0) }
            assertFailsWith<IllegalArgumentException> { HydrationPlan(2000.0, value) }
        }
    }
    @Test fun supplementKindIsSuggestedButAlwaysEditable() {
        assertEquals(SupplementKind.CREATINE, suggestedSupplementKind("Kreatin monohidrat"))
        assertEquals(SupplementKind.PROTEIN_POWDER, suggestedSupplementKind("HIQ HighPro Whey"))
        assertEquals(SupplementKind.OTHER, suggestedSupplementKind("Vitamin C"))
    }
    @Test fun adultReferenceDoesNotInventChildOrSupplementDoses() {
        assertNull(adultWaterReferenceMl(null, "male"))
        assertNull(adultWaterReferenceMl(17, "male"))
        assertNull(adultWaterReferenceMl(25, ""))
        assertEquals(2000, adultWaterReferenceMl(25, "male"))
        assertEquals(1600, adultWaterReferenceMl(70, "female"))
    }
}
