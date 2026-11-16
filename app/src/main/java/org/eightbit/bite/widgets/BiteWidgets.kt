// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.graphics.*
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.room.withTransaction
import androidx.work.*
import kotlinx.coroutines.flow.first
import org.eightbit.bite.*
import org.eightbit.bite.R
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import org.eightbit.bite.ui.localized
import org.eightbit.bite.ui.aiProviderDrawable
import org.eightbit.bite.ai.selectedAiProvider
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

open class RefreshingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdates.request(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = WidgetUpdates.request(context)
    override fun onDisabled(context: Context) = WidgetUpdates.request(context)
}
class OverviewWidget : RefreshingWidget()
class QuickAddWidget : RefreshingWidget()
class WaterWidget : RefreshingWidget()

object WidgetUpdates {
    val providers = listOf(OverviewWidget::class.java, QuickAddWidget::class.java, WaterWidget::class.java)
    fun request(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        if (providers.all { manager.getAppWidgetIds(ComponentName(context, it)).isEmpty() }) {
            WorkManager.getInstance(context).cancelUniqueWork("bite-widget-periodic")
            return
        }
        val work = WorkManager.getInstance(context)
        work.enqueueUniqueWork("bite-widget-refresh", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
        work.enqueueUniquePeriodicWork("bite-widget-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build())
    }
}
class WidgetClockChanged : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = WidgetUpdates.request(context)
}

data class WidgetDay(
    val date: LocalDate, val prefs: Preferences, val energy: NutrientTotal,
    val protein: NutrientTotal, val carbs: NutrientTotal, val fat: NutrientTotal,
    val waterMl: Double, val goal: DailyWaterGoal,
)

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BiteApplication
        val prefs = app.preferences.read()
        val date = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val day = app.database.withTransaction {
            val dao = app.database.dao()
            val entries = dao.dailyEntries(start, end)
            val nutrients = entries.map { it.nutrients() }
            val products = dao.supplements().first()
            val intakes = dao.dailyIntakes(start, end)
            val powder = supplementAmount(SupplementKind.PROTEIN_POWDER, entries, products, intakes)
            val creatine = supplementAmount(SupplementKind.CREATINE, entries, products, intakes)
            WidgetDay(date, prefs, nutrients.total { it.kcal }, nutrients.total { it.protein },
                nutrients.total { it.carbs }, nutrients.total { it.fat },
                dao.dailyWater(start, end).sumOf { it.amountMl }, prefs.dailyWaterGoal(powder.grams, creatine.grams))
        }
        val language = app.getSharedPreferences("locale", Context.MODE_PRIVATE).getString("selected", "en").orEmpty()
        val translated = app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
        val manager = AppWidgetManager.getInstance(app)
        for (provider in WidgetUpdates.providers) for (id in manager.getAppWidgetIds(ComponentName(app, provider))) {
            val options = manager.getAppWidgetOptions(id)
            val compact = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220) < 180
            manager.updateAppWidget(id, renderWidget(translated, provider, day, compact))
        }
        return Result.success()
    }
}

/** A stable entry ID makes a retried background action idempotent. Every tap has a new work ID. */
suspend fun saveWidgetWater(dao: BiteDao, requestId: String, amountMl: Double, time: Long) {
    require(amountMl.isFinite() && amountMl in 1.0..100000.0)
    dao.saveWater(WaterEntry("widget:" + requestId, amountMl, time))
}
class WidgetWaterAction : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "org.eightbit.bite.widget.DRINK") return
        val amount = intent.getDoubleExtra("amountMl", 0.0)
        if (!amount.isFinite() || amount !in 1.0..100000.0) return
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WidgetWaterWorker>()
            .setInputData(workDataOf("amountMl" to amount, "time" to System.currentTimeMillis())).build())
    }
}
class WidgetWaterWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as BiteApplication
        val amount = inputData.getDouble("amountMl", 0.0)
        if (!amount.isFinite() || amount !in 1.0..100000.0) return Result.failure()
        saveWidgetWater(app.database.dao(), id.toString(), amount, inputData.getLong("time", System.currentTimeMillis()))
        WidgetUpdates.request(app)
        return Result.success()
    }
}

private fun number(value: Double) = java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
private fun water(value: Double, liters: Boolean) = if (liters)
    java.math.BigDecimal.valueOf(value / 1000).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " L"
    else number(value) + " ml"
private fun NutrientTotal.text() = number(known) + if (complete) "" else "+?"

internal fun renderWidget(context: Context, provider: Class<*>, day: WidgetDay, compact: Boolean = false): RemoteViews {
    val isWater = provider == WaterWidget::class.java
    val isOverview = provider == OverviewWidget::class.java
    val layout = if (isWater) R.layout.widget_water else if (isOverview) R.layout.widget_overview else R.layout.widget_quick
    val view = RemoteViews(context.packageName, layout)
    if (compact) {
        val padding = (8 * context.resources.displayMetrics.density).toInt()
        view.setViewPadding(R.id.widget_root, padding, padding, padding, padding)
        if (isWater) view.setTextViewTextSize(R.id.widget_water_amount, android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
        if (isOverview) view.setTextViewTextSize(R.id.widget_energy, android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
    }
    fun label(source: String) = localized(context, source)
    fun open(target: Int, screen: String) {
        val intent = Intent(context, MainActivity::class.java).putExtra("screen", screen)
            .setAction("org.eightbit.bite.widget." + screen).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        view.setOnClickPendingIntent(target, PendingIntent.getActivity(context, screen.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    fun drink(target: Int, amount: Double) {
        val intent = Intent(context, WidgetWaterAction::class.java).setAction("org.eightbit.bite.widget.DRINK")
            .setData(android.net.Uri.parse("bite://drink/" + number(amount))).putExtra("amountMl", amount)
        view.setTextViewText(target, "+" + water(amount, day.prefs.waterInLiters))
        view.setContentDescription(target, label("Add water") + " " + water(amount, day.prefs.waterInLiters))
        view.setOnClickPendingIntent(target, PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    view.setTextViewText(R.id.widget_title, label(if (isWater) "Water" else if (isOverview) "Today" else "Quick add"))
    view.setTextViewText(R.id.widget_date, day.date.format(DateTimeFormatter.ofPattern("d MMM", context.resources.configuration.locales[0])))
    open(R.id.widget_root, if (isWater) "water" else "today")
    if (isWater) {
        view.setTextViewText(R.id.widget_water_amount, water(day.waterMl, day.prefs.waterInLiters))
        view.setTextViewText(R.id.widget_water_target, label("Daily goal") + " " + water(day.goal.totalMl, day.prefs.waterInLiters))
        view.setTextViewText(R.id.widget_water_breakdown, label("Base goal") + " " + water(day.goal.baseMl, true) +
            (if (day.goal.powderMl > 0) " · " + label("Protein powder") + " +" + water(day.goal.powderMl, true) else "") +
            (if (day.goal.creatineMl > 0) " · " + label("Creatine") + " +" + water(day.goal.creatineMl, true) else ""))
        view.setProgressBar(R.id.widget_water_progress, 1000, (day.waterMl / day.goal.totalMl * 1000).toInt().coerceIn(0, 1000), false)
        view.setImageViewBitmap(R.id.widget_water_vessels, widgetWaterBitmap(context, day.goal, day.waterMl))
        view.setContentDescription(R.id.widget_water_vessels, label("Base goal") + " " + water(day.goal.baseMl, true) +
            " · " + label("Dashed bottles are your planned extras. Log drinks separately."))
        view.setViewVisibility(R.id.widget_water_vessels, if (compact) View.GONE else View.VISIBLE)
        view.setViewVisibility(R.id.widget_water_breakdown, if (compact) View.GONE else View.VISIBLE)
        val portions = listOf(day.prefs.waterUnitMl, 250.0, 500.0, 100.0).distinct().take(3)
        listOf(R.id.widget_drink_one, R.id.widget_drink_two, R.id.widget_drink_three).forEachIndexed { index, target -> drink(target, portions[index]) }
    } else {
        view.setTextViewText(R.id.widget_add, label("Food"))
        view.setTextViewText(R.id.widget_camera, label("Photo"))
        val provider=selectedAiProvider(context)
        view.setTextViewText(R.id.widget_ai,when(provider) { AiProvider.CHATGPT -> "ChatGPT";AiProvider.REMOTE -> label("Remote server");AiProvider.LOCAL -> label("On-device AI") })
        view.setTextViewCompoundDrawables(R.id.widget_ai,0,aiProviderDrawable(provider),0,0)
        view.setContentDescription(R.id.widget_ai, label("Describe a meal with AI"))
        view.setContentDescription(R.id.widget_avatar, label("Open your profile"))
        listOf(R.id.widget_add to "foods", R.id.widget_ai to "ai", R.id.widget_camera to "camera", R.id.widget_avatar to "profile").forEach { (target, screen) -> open(target, screen) }
        val avatar = java.io.File(context.filesDir, "profile.jpg")
        val image = if (day.prefs.avatarVersion > 0) circleAvatar(avatar) else null
        if (image == null) view.setImageViewResource(R.id.widget_avatar, R.mipmap.ic_launcher) else view.setImageViewBitmap(R.id.widget_avatar, image)
        if (isOverview) {
            view.setTextViewText(R.id.widget_energy, day.energy.text() + " / " + day.prefs.calorieGoal + " kcal")
            view.setProgressBar(R.id.widget_energy_progress, 1000, (day.energy.known / day.prefs.calorieGoal * 1000).toInt().coerceIn(0, 1000), false)
            fun macro(name: String, actual: NutrientTotal, goal: Double?) = label(name) + " " + actual.text() + (goal?.let { "/" + number(it) } ?: "") + " g"
            view.setTextViewText(R.id.widget_macros, macro("Protein", day.protein, day.prefs.proteinGoal.takeIf { day.prefs.proteinGoalEnabled }) + "\n" +
                macro("Carbs", day.carbs, day.prefs.carbGoal.takeIf { day.prefs.carbGoalEnabled }) + " · " +
                macro("Fat", day.fat, day.prefs.fatGoal.takeIf { day.prefs.fatGoalEnabled }))
            view.setTextViewText(R.id.widget_water_summary, label("Water") + "  " + water(day.waterMl, true) + " / " + water(day.goal.totalMl, true))
            view.setProgressBar(R.id.widget_water_progress, 1000, (day.waterMl / day.goal.totalMl * 1000).toInt().coerceIn(0, 1000), false)
            open(R.id.widget_water_section, "water")
            drink(R.id.widget_water_add, day.prefs.waterUnitMl)
            view.setViewVisibility(R.id.widget_water_section, if (day.prefs.waterEnabled) View.VISIBLE else View.GONE)
            view.setViewVisibility(R.id.widget_actions, if (compact) View.GONE else View.VISIBLE)
            view.setViewVisibility(R.id.widget_macros, if (compact) View.GONE else View.VISIBLE)
        }
    }
    return view
}

private fun circleAvatar(file: java.io.File): Bitmap? {
    val source = BitmapFactory.decodeFile(file.path) ?: return null
    val result = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(48f, 48f, 48f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    val side = minOf(source.width, source.height)
    val left = (source.width - side) / 2
    val top = (source.height - side) / 2
    canvas.drawBitmap(source, Rect(left, top, left + side, top + side), Rect(0, 0, 96, 96), paint)
    source.recycle()
    return result
}

internal fun widgetWaterBitmap(context: Context, goal: DailyWaterGoal, consumedMl: Double): Bitmap {
    val bitmap = Bitmap.createBitmap(480, 88, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val outline = context.getColor(R.color.widget_accent)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline; alpha = 170 }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline; style = Paint.Style.STROKE; strokeWidth = 2.5f }
    val unit = maxOf(1000.0, kotlin.math.ceil(goal.totalMl / 8000.0) * 1000)
    val segments = listOf(goal.baseMl, goal.powderMl, goal.creatineMl)
    val count = segments.sumOf { kotlin.math.ceil(it / unit).toInt() }
    val width = minOf(52f, 480f / count)
    var x = (480f - width * count) / 2
    var start = 0.0
    segments.forEachIndexed { group, ml ->
        repeat(kotlin.math.ceil(ml / unit).toInt()) { index ->
            val path = Path().apply {
                moveTo(x + width * .37f, 5f); lineTo(x + width * .63f, 5f); lineTo(x + width * .63f, 22f)
                lineTo(x + width * .81f, 35f); lineTo(x + width * .81f, 81f); lineTo(x + width * .19f, 81f)
                lineTo(x + width * .19f, 35f); lineTo(x + width * .37f, 22f); close()
            }
            val fraction = ((consumedMl - start - index * unit) / unit).coerceIn(0.0, 1.0).toFloat()
            canvas.save(); canvas.clipPath(path); canvas.drawRect(x, 81 - 76 * fraction, x + width, 82f, fill); canvas.restore()
            stroke.pathEffect = if (group > 0) DashPathEffect(floatArrayOf(5f, 4f), 0f) else null
            canvas.drawPath(path, stroke)
            x += width
        }
        start += ml
    }
    return bitmap
}
