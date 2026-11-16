package org.eightbit.bite.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.eightbit.bite.widgets.*

@Composable
fun WidgetSettings() {
    val context=LocalContext.current
    val manager=AppWidgetManager.getInstance(context)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(ai("Home screen widgets","Ana ekran widget’ları"),style=MaterialTheme.typography.titleMedium)
        Text(ai("Add a daily summary or a shortcut to food entry, AI and the camera.","Günlük özet veya yiyecek ekleme, AI ve kamera kısayolları ekle."))
        if(manager.isRequestPinAppWidgetSupported) {
            OutlinedButton(onClick={manager.requestPinAppWidget(ComponentName(context,WaterWidget::class.java),null,null)}) { Text(ai("Add water tracker widget","Su takibi widget’ı ekle")) }
            OutlinedButton(onClick={manager.requestPinAppWidget(ComponentName(context,OverviewWidget::class.java),null,null)}) { Text(ai("Add daily summary widget","Günlük özet widget’ı ekle")) }
            OutlinedButton(onClick={manager.requestPinAppWidget(ComponentName(context,QuickAddWidget::class.java),null,null)}) { Text(ai("Add quick-entry widget","Hızlı ekleme widget’ı ekle")) }
        } else Text(ai("Long-press your home screen and choose Widgets → 8bitBites.","Ana ekranına uzun bas ve Widget’lar → 8bitBites seç."))
    }
}
