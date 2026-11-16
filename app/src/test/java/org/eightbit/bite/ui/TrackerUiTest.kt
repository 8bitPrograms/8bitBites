package org.eightbit.bite.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.eightbit.bite.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
// Robolectric #8460: a nondefault width qualifier makes Dialog text fields oscillate.
// Use the default window width for dialog interaction tests; render-only tests override it.
@Config(sdk = [34], application = Application::class, qualifiers = "h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrackerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hundredMlShowsTwentyGlassesAndAddsOneServing() {
        var added = 0.0
        compose.setContent { MaterialTheme { WaterCard(emptyList(), Preferences(), { added += it }, {}, {}, false, {}) } }
        compose.onNodeWithContentDescription("Expand").performClick()
        compose.onNodeWithContentDescription("Glass 20 / 20").assertExists()
        compose.onNodeWithContentDescription("Add water 100 ml").performClick()
        assertEquals(100.0, added, .0001)
    }
    @Test fun sevenHundredMlUsesBottlesAndStillAddsBeyondGoal() {
        var added = 0.0
        compose.setContent { MaterialTheme { WaterCard(listOf(WaterEntry("one", 1400.0, 1)),
            Preferences(waterGoalMl = 1400.0, waterUnitMl = 700.0), { added += it }, {}, {}, false, {}) } }
        compose.onNodeWithContentDescription("Expand").performClick()
        compose.onNodeWithContentDescription("Bottle 2 / 2").assertExists()
        compose.onNodeWithText("Goal reached").assertExists()
        compose.onNodeWithContentDescription("Add water 700 ml").performClick()
        assertEquals(700.0, added, .0001)
    }
    @Test fun portionRemembersBreakfastAndCheckboxKeepsThatMeal() {
        var savedMeal = -1
        var savedSupplement = false
        var savedKind = ""
        compose.setContent { MaterialTheme { PortionEditor(Food(id = "personal:test-whey", name = "Whey powder"), null, 1, {}, false, {},
            save = { _, _, meal, supplement, kind -> savedMeal = meal; savedSupplement = supplement; savedKind = kind }) } }
        compose.onNodeWithText("Breakfast").assertExists()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Save entry").performClick()
        assertEquals(1, savedMeal); assertTrue(savedSupplement); assertEquals("PROTEIN_POWDER", savedKind)
    }
    @Test fun customLitersRetainSmallVolumePrecision() {
        var saved = 0.0
        compose.setContent { MaterialTheme { WaterAmountDialog("Serving size", 10.0, true, {}, { ml, _ -> saved = ml }) } }
        compose.onNodeWithText("0.01").assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals(10.0, saved, .0001)
    }
    private lateinit var rootView: android.view.View
    private fun dashboard() {
        compose.setContent { BiteTheme(Preferences()) { Surface(Modifier.fillMaxSize()) {
            rootView = LocalView.current
            DashboardScreen(modifier = Modifier, date = LocalDate.of(2026, 11, 16), entries = emptyList(), water = emptyList(),
                products = emptyList(), intakes = emptyList(), prefs = Preferences(name = "Demo"), changeDate = {},
                add = {}, addSupplement = {}, delete = {}, repeat = {}, edit = {}, addWater = {}, deleteWater = {},
                undoWater = {}, canUndoWater = false, save = {}, settings = {}, dose = {}, editDose = {}, deleteDose = {}, editTarget = {})
        } } }
    }
    private fun capture(name: String) = compose.runOnIdle {
        val image = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        rootView.draw(android.graphics.Canvas(image))
        val file = File("build/reports/ui/$name.png").also { it.parentFile!!.mkdirs() }
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test @Config(qualifiers = "w393dp-h852dp-xxhdpi") fun dashboardRendersCompactSections() {
        dashboard()
        compose.onNodeWithText("Water").assertExists()
        compose.onNodeWithText("Supplements").assertExists()
        compose.onNodeWithText("Last seven days").assertExists()
        capture("dashboard")
    }
    @Test @Config(qualifiers = "ja-w393dp-h852dp-xxhdpi") fun japaneseDashboardUsesLocalizedLabels() {
        dashboard()
        compose.onNodeWithText("水分").assertExists()
        compose.onNodeWithText("サプリメント").assertExists()
        compose.onNodeWithText("過去7日間").assertExists()
        capture("dashboard-ja")
    }
}
