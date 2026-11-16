// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite

import android.app.Application
import androidx.room.Room
import org.eightbit.bite.data.BiteDatabase
import org.eightbit.bite.data.PreferenceStore
import org.eightbit.bite.data.CatalogueStore
import org.eightbit.bite.data.Updates
import org.eightbit.bite.health.SyncScheduler
import org.eightbit.bite.widgets.WidgetUpdates
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BiteApplication : Application() {
    private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val widgetPreferences=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_-> WidgetUpdates.request(this) }
    val database by lazy {
        Room.databaseBuilder(this, BiteDatabase::class.java, "bite.db")
            .addMigrations(org.eightbit.bite.data.BiteMigration1To2(this)).build()
    }
    val preferences by lazy { PreferenceStore(this) }
    val catalogue by lazy { CatalogueStore(this) }
    val updates by lazy { Updates(this, catalogue) }
    val models by lazy { org.eightbit.bite.ai.ModelLibrary(this) }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.periodic(this, preferences.read().autoSync)
        preferences.listen(widgetPreferences)
        appScope.launch {
            database.dao().entries().debounce(400).collect { WidgetUpdates.request(this@BiteApplication) }
        }
    }
}
