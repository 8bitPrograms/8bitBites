package org.eightbit.bite.core
import kotlin.test.*

class CataloguePolicyTest {
    private fun release(version: String = "1.10.0", schema: Int = 1, app: Long = 2, file: String = "versions/$version/foods.sqlite") =
        DatabaseRelease(version, schema, app, file, 4096, "a".repeat(64), true, emptyMap())
    @Test fun `versions are numeric and cannot downgrade`() {
        assertTrue(release().newerThan("1.9.0")); assertFalse(release("1.0.0").newerThan("2.0.0"))
        assertFalse(release("1.0.0").newerThan("1.0.0"))
    }
    @Test fun `schema and app compatibility are separate`() {
        assertTrue(release().compatible(2)); assertFalse(release(schema=2).compatible(2)); assertFalse(release(app=3).compatible(2))
    }
    @Test fun `manifest paths and versions are strict`() {
        assertFailsWith<IllegalArgumentException> { release(file="../../personal.db") }
        assertFailsWith<IllegalArgumentException> { release("01.0.0") }
        assertFailsWith<IllegalArgumentException> { release("1.0.0-beta") }
    }
    private val translator = FoodTranslator(listOf(FoodTerm("protein powder", "protein tozu"), FoodTerm("creatine", "kreatin")))
    @Test fun `translations preserve brands and quantities`() {
        assertEquals("Brand protein tozu 100 g", translator.translate("Brand protein powder 100 g", "tr"))
        assertEquals("CreatineTech", translator.translate("CreatineTech", "tr"))
        assertEquals("Unknown Brand", translator.translate("Unknown Brand", "tr"))
    }
    @Test fun `search works in Turkish and English`() {
        assertTrue("protein powder" in translator.alternatives("protein tozu"))
        assertEquals("suzme yogurt", foldFoodText("SÜZME YOĞURT"))
    }
}
