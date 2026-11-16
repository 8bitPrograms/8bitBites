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
import androidx.compose.ui.graphics.toArgb
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
            val height=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
            val compact=when(provider) { OverviewWidget::class.java->height<280;WaterWidget::class.java->height<220;else->false }
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

internal data class WidgetColors(val background:Int,val foreground:Int,val muted:Int,val accent:Int,val tile:Int,val track:Int)
internal fun widgetColors(context:Context,prefs:Preferences):WidgetColors {
    val systemDark=context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val palette=org.eightbit.bite.ui.resolvePalette(prefs,org.eightbit.bite.ui.loadPalettes(context),systemDark)
    if(palette!=null && android.os.Build.VERSION.SDK_INT>=31) {
        val scheme=org.eightbit.bite.ui.paletteColorScheme(palette)
        return WidgetColors(scheme.surface.toArgb(),scheme.onSurface.toArgb(),scheme.onSurfaceVariant.toArgb(),
            scheme.primary.toArgb(),scheme.primaryContainer.toArgb(),scheme.surfaceContainerHigh.toArgb())
    }
    return WidgetColors(context.getColor(R.color.widget_surface),context.getColor(R.color.widget_foreground),
        context.getColor(R.color.widget_muted),context.getColor(R.color.widget_accent),context.getColor(R.color.widget_tile),context.getColor(R.color.widget_tile))
}

internal fun renderWidget(context: Context, provider: Class<*>, day: WidgetDay, compact: Boolean = false): RemoteViews {
    val isWater = provider == WaterWidget::class.java
    val isOverview = provider == OverviewWidget::class.java
    val layout = if (isWater) R.layout.widget_water else if (isOverview) R.layout.widget_overview else R.layout.widget_quick
    val view = RemoteViews(context.packageName, layout)
    val colors=widgetColors(context,day.prefs)
    fun text(id:Int,value:String,color:Int=colors.foreground) { view.setTextViewText(id,value);view.setTextColor(id,color) }
    fun tint(id:Int,color:Int,method:String="setBackgroundTintList") {
        if(android.os.Build.VERSION.SDK_INT>=31) view.setColorStateList(id,method,android.content.res.ColorStateList.valueOf(color))
    }
    tint(R.id.widget_root,colors.background)
    if(compact) {
        val padding=(8*context.resources.displayMetrics.density).toInt()
        view.setViewPadding(R.id.widget_root,padding,padding,padding,padding)
        if(isWater || isOverview) view.setTextViewTextSize(if(isWater) R.id.widget_water_amount else R.id.widget_energy,android.util.TypedValue.COMPLEX_UNIT_SP,if(isWater) 22f else 20f)
    }
    fun label(source: String) = localized(context, source)
    fun open(target: Int, screen: String) {
        val intent = Intent(context, MainActivity::class.java).putExtra("screen", screen)
            .setAction("org.eightbit.bite.widget." + screen).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        view.setOnClickPendingIntent(target, PendingIntent.getActivity(context, screen.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    fun progress(id:Int,fraction:Double) {
        view.setProgressBar(id,1000,(fraction*1000).toInt().coerceIn(0,1000),false)
        tint(id,colors.accent,"setProgressTintList");tint(id,colors.track,"setProgressBackgroundTintList")
    }
    fun drink(target: Int, amount: Double) {
        val intent = Intent(context, WidgetWaterAction::class.java).setAction("org.eightbit.bite.widget.DRINK")
            .setData(android.net.Uri.parse("bite://drink/" + number(amount))).putExtra("amountMl", amount)
        text(target,"+"+water(amount,day.prefs.waterInLiters))
        tint(target,colors.tile)
        view.setContentDescription(target, label("Add water") + " " + water(amount, day.prefs.waterInLiters))
        view.setOnClickPendingIntent(target, PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
    if(isWater || isOverview) {
        text(R.id.widget_title,label(if(isWater) "Water" else "Today"))
        text(R.id.widget_date,day.date.format(DateTimeFormatter.ofPattern("d MMM",context.resources.configuration.locales[0])),colors.muted)
    }
    open(R.id.widget_root,if(isWater) "water" else "today")
    if(isWater) {
        text(R.id.widget_water_amount,water(day.waterMl,day.prefs.waterInLiters),colors.accent)
        text(R.id.widget_water_target,label("Daily goal")+" "+water(day.goal.totalMl,day.prefs.waterInLiters),colors.muted)
        text(R.id.widget_water_breakdown,label("Base goal")+" "+water(day.goal.baseMl,true)+
            (if(day.goal.powderMl>0) " · "+label("Protein powder")+" +"+water(day.goal.powderMl,true) else "")+
            (if(day.goal.creatineMl>0) " · "+label("Creatine")+" +"+water(day.goal.creatineMl,true) else ""),colors.muted)
        progress(R.id.widget_water_progress,day.waterMl/day.goal.totalMl)
        view.setImageViewBitmap(R.id.widget_water_vessels,widgetWaterBitmap(context,day.goal,day.waterMl,colors.accent))
        view.setContentDescription(R.id.widget_water_vessels,label("Base goal")+" "+water(day.goal.baseMl,true)+" · "+label("Dashed bottles are your planned extras. Log drinks separately."))
        view.setViewVisibility(R.id.widget_water_vessels,if(compact) View.GONE else View.VISIBLE)
        view.setViewVisibility(R.id.widget_water_breakdown,if(compact) View.GONE else View.VISIBLE)
        val portions=listOf(day.prefs.waterUnitMl,250.0,500.0,100.0).distinct().take(3)
        listOf(R.id.widget_drink_one,R.id.widget_drink_two,R.id.widget_drink_three).forEachIndexed { index,id -> drink(id,portions[index]) }
    } else {
        val provider=selectedAiProvider(context)
        text(R.id.widget_add_label,label("Food"))
        text(R.id.widget_camera_label,label("Photo"))
        text(R.id.widget_ai_label,when(provider) { AiProvider.CHATGPT->"ChatGPT";AiProvider.REMOTE->"Server";AiProvider.LOCAL->"On-device" })
        listOf(R.id.widget_add,R.id.widget_ai,R.id.widget_camera).forEach { tint(it,colors.tile) }
        listOf(R.id.widget_add_icon to R.drawable.ic_chat_new,R.id.widget_ai_icon to aiProviderDrawable(provider),
            R.id.widget_camera_icon to R.drawable.ic_chat_camera).forEach { (id,icon) -> view.setImageViewBitmap(id,widgetIcon(context,icon,colors.foreground)) }
        view.setContentDescription(R.id.widget_ai,label("Describe a meal with AI"))
        listOf(R.id.widget_add to "foods",R.id.widget_ai to "ai",R.id.widget_camera to "camera").forEach { (id,screen) -> open(id,screen) }
        if(isOverview) {
            val avatar=java.io.File(context.filesDir,"profile.jpg")
            val image=if(day.prefs.avatarVersion>0) circleAvatar(avatar) else null
            if(image==null) view.setImageViewResource(R.id.widget_avatar,R.mipmap.ic_launcher) else view.setImageViewBitmap(R.id.widget_avatar,image)
            view.setContentDescription(R.id.widget_avatar,label("Open your profile"));open(R.id.widget_avatar,"profile")
            text(R.id.widget_energy,day.energy.text()+(if(compact) " / "+day.prefs.calorieGoal else "")+" kcal")
            text(R.id.widget_energy_target,label("Daily goal")+" "+day.prefs.calorieGoal+" kcal",colors.muted)
            val fraction=day.energy.known/day.prefs.calorieGoal
            progress(R.id.widget_energy_progress,fraction)
            view.setImageViewBitmap(R.id.widget_energy_ring,widgetEnergyRing(context,fraction,colors))
            view.setContentDescription(R.id.widget_energy_ring,day.energy.text()+" / "+day.prefs.calorieGoal+" kcal")
            fun macro(name:String,actual:NutrientTotal,goal:Double?)=label(name)+"\n"+actual.text()+(goal?.let { "/"+number(it) } ?: "")+" g"
            text(R.id.widget_protein,macro("Protein",day.protein,day.prefs.proteinGoal.takeIf { day.prefs.proteinGoalEnabled }))
            text(R.id.widget_carbs,macro("Carbs",day.carbs,day.prefs.carbGoal.takeIf { day.prefs.carbGoalEnabled }))
            text(R.id.widget_fat,macro("Fat",day.fat,day.prefs.fatGoal.takeIf { day.prefs.fatGoalEnabled }))
            text(R.id.widget_water_summary,label("Water")+"  "+water(day.waterMl,true)+" / "+water(day.goal.totalMl,true))
            progress(R.id.widget_water_progress,day.waterMl/day.goal.totalMl)
            open(R.id.widget_water_section,"water");drink(R.id.widget_water_add,day.prefs.waterUnitMl)
            view.setViewVisibility(R.id.widget_water_section,if(day.prefs.waterEnabled) View.VISIBLE else View.GONE)
            view.setViewVisibility(R.id.widget_macros,if(compact) View.GONE else View.VISIBLE)
            view.setViewVisibility(R.id.widget_energy_ring,if(compact) View.GONE else View.VISIBLE)
            view.setViewVisibility(R.id.widget_energy_target,if(compact) View.GONE else View.VISIBLE)
        }
    }
    return view
}

private fun widgetIcon(context:Context,id:Int,color:Int):Bitmap {
    val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
    context.getDrawable(id)?.mutate()?.let { drawable ->
        drawable.setTint(color);drawable.setBounds(0,0,64,64);drawable.draw(Canvas(bitmap))
    }
    return bitmap
}

private fun widgetEnergyRing(context:Context,fraction:Double,colors:WidgetColors):Bitmap {
    val bitmap=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888)
    val canvas=Canvas(bitmap);val ring=RectF(22f,22f,218f,218f)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=18f;strokeCap=Paint.Cap.ROUND;color=colors.track }
    canvas.drawOval(ring,paint);paint.color=colors.accent
    canvas.drawArc(ring,-90f,(fraction.coerceIn(0.0,1.0)*360).toFloat(),false,paint)
    paint.style=Paint.Style.FILL;paint.color=colors.foreground;paint.textAlign=Paint.Align.CENTER;paint.textSize=48f
    paint.typeface=context.resources.getFont(R.font.inter_variable)
    canvas.drawText((fraction*100).toInt().coerceAtMost(999).toString()+"%",120f,137f,paint)
    return bitmap
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

internal fun widgetWaterBitmap(context: Context, goal: DailyWaterGoal, consumedMl: Double, accent:Int=context.getColor(R.color.widget_accent)): Bitmap {
    val bitmap = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val outline = accent
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline; alpha = 220 }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline; style = Paint.Style.STROKE; strokeWidth = 3.5f }
    val unit = maxOf(1000.0, kotlin.math.ceil(goal.totalMl / 8000.0) * 1000)
    val segments = listOf(goal.baseMl, goal.powderMl, goal.creatineMl)
    val count = segments.sumOf { kotlin.math.ceil(it / unit).toInt() }
    val width = minOf(90f, 480f / count)
    var x = (480f - width * count) / 2
    var start = 0.0
    segments.forEachIndexed { group, ml ->
        repeat(kotlin.math.ceil(ml / unit).toInt()) { index ->
            val path = Path().apply {
                moveTo(x + width * .37f, 5f); lineTo(x + width * .63f, 5f); lineTo(x + width * .63f, 50f)
                lineTo(x + width * .81f, 85f); lineTo(x + width * .81f, 225f); lineTo(x + width * .19f, 225f)
                lineTo(x + width * .19f, 85f); lineTo(x + width * .37f, 50f); close()
            }
            val fraction = ((consumedMl - start - index * unit) / unit).coerceIn(0.0, 1.0).toFloat()
            canvas.save(); canvas.clipPath(path); canvas.drawRect(x, 225 - 210 * fraction, x + width, 226f, fill); canvas.restore()
            stroke.pathEffect = if (group > 0) DashPathEffect(floatArrayOf(10f, 8f), 0f) else null
            canvas.drawPath(path, stroke)
            x += width
        }
        start += ml
    }
    return bitmap
}
