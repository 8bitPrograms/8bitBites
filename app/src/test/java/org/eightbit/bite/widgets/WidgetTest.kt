package org.eightbit.bite.widgets

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.eightbit.bite.R
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetTest {
    private val day = WidgetDay(LocalDate.of(2026, 11, 16),
        Preferences(waterUnitMl = 250.0, proteinGoalEnabled = true, proteinGoal = 120.0),
        NutrientTotal(1285.0, 0), NutrientTotal(84.0, 0), NutrientTotal(132.0, 0), NutrientTotal(45.0, 1),
        2250.0, DailyWaterGoal(2000.0, 1000.0, 1000.0))

    private fun render(provider: Class<*>, width: Int, height: Int, name: String, compact: Boolean = false): View {
        val context = RuntimeEnvironment.getApplication()
        val view = renderWidget(context, provider, day, compact).apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/reports/ui/" + name + ".png").also { it.parentFile!!.mkdirs() }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return view
    }
    private fun assertFits(root: View, target: Int) {
        val child = root.findViewById<View>(target)
        val bounds = android.graphics.Rect(0, 0, child.width, child.height)
        (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(child, bounds)
        assertTrue("Widget control is clipped: " + bounds, bounds.bottom <= root.height - root.paddingBottom)
    }
    @Test fun waterWidgetShowsRealTotalsAndThreeDifferentPortions() {
        val view = render(WaterWidget::class.java, 660, 660, "widget-water")
        assertEquals("2250 ml", view.findViewById<TextView>(R.id.widget_water_amount).text.toString())
        assertEquals("+250 ml", view.findViewById<TextView>(R.id.widget_drink_one).text.toString())
        assertEquals("+500 ml", view.findViewById<TextView>(R.id.widget_drink_two).text.toString())
        assertEquals("+100 ml", view.findViewById<TextView>(R.id.widget_drink_three).text.toString())
        assertTrue(view.findViewById<TextView>(R.id.widget_water_breakdown).text.contains("Creatine"))
        assertTrue(view.findViewById<View>(R.id.widget_drink_one).performClick())
        assertFits(view, R.id.widget_drink_three)
    }
    @Test fun compactWidgetKeepsDrinkingButtonsAndHidesOnlyTheIllustration() {
        val view = render(WaterWidget::class.java, 540, 420, "widget-water-compact", true)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_water_vessels).visibility)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_drink_one).visibility)
        assertFits(view, R.id.widget_drink_three)
    }
    @Test fun summaryAndQuickWidgetsRenderWithoutClippingAtTheirMinimumSizes() {
        val overview = render(OverviewWidget::class.java, 780, 720, "widget-overview")
        assertTrue(overview.findViewById<TextView>(R.id.widget_energy).text.contains("1285"))
        assertTrue(overview.findViewById<TextView>(R.id.widget_macros).text.contains("84/120"))
        assertFits(overview, R.id.widget_camera)
        val quick = render(QuickAddWidget::class.java, 540, 300, "widget-quick")
        assertFits(quick, R.id.widget_camera)
    }
    @Test fun retriedTapDoesNotDuplicateWaterAndTwoTapsRemainSeparate() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BiteDatabase::class.java).allowMainThreadQueries().build()
        try {
            saveWidgetWater(db.dao(), "tap-one", 250.0, 1000)
            saveWidgetWater(db.dao(), "tap-one", 250.0, 1000)
            saveWidgetWater(db.dao(), "tap-two", 250.0, 1001)
            assertEquals(2, db.dao().dailyWater(0, 2000).size)
            assertEquals(500.0, db.dao().dailyWater(0, 2000).sumOf { it.amountMl }, .001)
            assertTrue(db.dao().dailyWater(2000, 3000).isEmpty())
        } finally { db.close() }
    }
    @Test fun quickWidgetUsesTheSelectedProviderLabelAndAnActualIcon() {
        val context=RuntimeEnvironment.getApplication() as android.content.Context
        context.getSharedPreferences("ai-settings",0).edit().putString("provider","CHATGPT").commit()
        val view=render(QuickAddWidget::class.java,540,300,"widget-quick-chatgpt")
        val button=view.findViewById<TextView>(R.id.widget_ai)
        assertEquals("ChatGPT",button.text.toString())
        assertNotNull(button.compoundDrawables[1])
        assertFits(view,R.id.widget_ai)
    }
}
