// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.*
import kotlinx.coroutines.flow.first
import org.eightbit.bite.*
import org.eightbit.bite.R
import org.eightbit.bite.core.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class OverviewWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { WidgetUpdates.request(context) }
}
class QuickAddWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) { WidgetUpdates.request(context) }
}
object WidgetUpdates {
    fun request(context:Context) {
        val manager=AppWidgetManager.getInstance(context)
        if(listOf(OverviewWidget::class.java,QuickAddWidget::class.java).all { manager.getAppWidgetIds(ComponentName(context,it)).isEmpty() }) return
        WorkManager.getInstance(context).enqueueUniqueWork("bite-widget-refresh",ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }
}
class WidgetRefreshWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as BiteApplication
        val prefs=app.preferences.read()
        val date=LocalDate.now();val zone=ZoneId.systemDefault()
        val start=date.atStartOfDay(zone).toInstant().toEpochMilli();val end=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val entries=app.database.dao().entries().first().filter { it.time in start until end }.map { it.nutrients() }
        val language=app.getSharedPreferences("locale",Context.MODE_PRIVATE).getString("selected","en").orEmpty()
        val turkish=language=="tr"
        val translatedContext=app.createConfigurationContext(android.content.res.Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        })
        fun label(source:String)=org.eightbit.bite.ui.localized(translatedContext,source)
        fun total(select:(Nutrients)->Double?):String { val n=entries.total(select);return "%.0f".format(Locale.ROOT,n.known)+if(n.complete) "" else "+?" }
        val manager=AppWidgetManager.getInstance(app)
        for((provider,layout) in listOf(OverviewWidget::class.java to R.layout.widget_overview,QuickAddWidget::class.java to R.layout.widget_quick)) {
            for(id in manager.getAppWidgetIds(ComponentName(app,provider))) {
                val view=RemoteViews(app.packageName,layout)
                view.setTextViewText(R.id.widget_title,if(provider==OverviewWidget::class.java) "8bitBites" else translatedContext.getString(R.string.widget_quick_name))
                view.setTextViewText(R.id.widget_add,translatedContext.getString(R.string.widget_add))
                view.setTextViewText(R.id.widget_camera,translatedContext.getString(R.string.widget_camera))
                view.setTextViewText(R.id.widget_ai,label("AI"))
                view.setContentDescription(R.id.widget_ai,translatedContext.getString(R.string.widget_ai))
                view.setContentDescription(R.id.widget_avatar,translatedContext.getString(R.string.widget_profile))
                if(provider==OverviewWidget::class.java) {
                    view.setTextViewText(R.id.widget_date,date.format(DateTimeFormatter.ofPattern("d MMM",Locale.forLanguageTag(language))))
                    view.setTextViewText(R.id.widget_energy,"${total { it.kcal }} / ${prefs.calorieGoal} kcal")
                    view.setTextViewText(R.id.widget_macros,if(turkish) "P ${total { it.protein }}g · K ${total { it.carbs }}g · Y ${total { it.fat }}g"
                        else "P ${total { it.protein }}g · C ${total { it.carbs }}g · F ${total { it.fat }}g")
                }
                if(prefs.avatarVersion>0) circleAvatar(File(app.filesDir,"profile.jpg"))?.let { view.setImageViewBitmap(R.id.widget_avatar,it) }
                else view.setImageViewResource(R.id.widget_avatar,R.mipmap.ic_launcher)
                listOf(R.id.widget_root to "today",R.id.widget_add to "foods",R.id.widget_ai to "ai",R.id.widget_camera to "camera",R.id.widget_avatar to "profile").forEach { (target,screen) ->
                    val intent=Intent(app,MainActivity::class.java).putExtra("screen",screen).setAction("org.eightbit.bite.widget.$screen").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    view.setOnClickPendingIntent(target,PendingIntent.getActivity(app,screen.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
                // Always replace RemoteViews when the avatar changes; do not retain old bitmap actions.
                manager.updateAppWidget(id,view)
            }
        }
        return Result.success()
    }
    private fun circleAvatar(file:File):Bitmap? {
        val bitmap=BitmapFactory.decodeFile(file.path) ?: return null
        val result=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(result);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(48f,48f,48f,paint);paint.xfermode=PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val side=minOf(bitmap.width,bitmap.height);val left=(bitmap.width-side)/2;val top=(bitmap.height-side)/2
        canvas.drawBitmap(bitmap,Rect(left,top,left+side,top+side),Rect(0,0,96,96),paint);bitmap.recycle();return result
    }
}
