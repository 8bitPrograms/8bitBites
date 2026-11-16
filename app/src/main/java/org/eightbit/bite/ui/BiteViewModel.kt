// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.eightbit.bite.BiteApplication
import org.eightbit.bite.data.*
import org.eightbit.bite.core.DoseUnit
import org.eightbit.bite.core.doseGrams
import org.eightbit.bite.core.foldFoodText
import org.eightbit.bite.core.SupplementKind
import org.eightbit.bite.health.SyncScheduler
import org.json.JSONArray
import java.time.*

class BiteViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as BiteApplication
    val foodQuery = MutableStateFlow("")
    val externalResults = MutableStateFlow<List<Food>>(emptyList())
    val externalBusy = MutableStateFlow(false)
    private val providers = ExternalFoods()
    @OptIn(ExperimentalCoroutinesApi::class)
    val foods = combine(app.database.dao().foods(), app.catalogue.info, foodQuery, externalResults) { personal, _, query, external ->
        Triple(personal, query, external)
    }.mapLatest { (personal, query, external) ->
        val alternatives=app.catalogue.translator.alternatives(query)
        val local=personal.filter { food -> alternatives.any { foldFoodText("${food.name} ${food.brand}").contains(it) } }
        (local.filter { it.id.startsWith("personal:") }+app.catalogue.search(query)+local+external).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val catalogueInfo = app.catalogue.info
    val updateState = app.updates.state
    val entries = app.database.dao().entries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val supplements = app.database.dao().supplements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val supplementIntakes = app.database.dao().supplementIntakes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val waterEntries = app.database.dao().waterEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteFoods = combine(app.database.dao().favoriteFoods(), app.catalogue.info) { saved, _ ->
        saved.map { food -> if (food.id.startsWith("catalogue:")) app.catalogue.find(food.id) ?: food else food }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lastWaterId = MutableStateFlow<String?>(null)
    val pending = app.database.dao().pendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val preferences = MutableStateFlow(app.preferences.read())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    private val versionTaps=org.eightbit.bite.core.VersionTapUnlock()
    fun versionTap():Boolean {
        if(preferences.value.themesUnlocked || !versionTaps.tap(android.os.SystemClock.elapsedRealtime())) return false
        app.preferences.unlockThemes();return true
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        preferences.value = app.preferences.read()
    }
    init { app.preferences.listen(preferenceListener) }
    fun onOpen() = task {
        app.preferences.seedLastMeal(app.database.dao().entries().first().firstOrNull()?.mealType)
        org.eightbit.bite.widgets.WidgetUpdates.request(app); app.catalogue.initialize(); app.updates.check()
    }
    fun checkUpdates() = task { app.updates.check(manual=true) }
    fun profilePhoto(uri:Uri?) = task {
        withContext(Dispatchers.IO) {
            val file=java.io.File(app.filesDir,"profile.jpg")
            if(uri==null) { check(!file.exists() || file.delete()) }
            else {
                val jpeg=localJpeg(app,uri,512)
                val atomic=android.util.AtomicFile(file)
                val output=atomic.startWrite()
                try { output.write(jpeg);atomic.finishWrite(output) }
                catch(error:Throwable) { atomic.failWrite(output);throw error }
            }
            app.preferences.avatarChanged(uri!=null)
        }
    }
    fun updateDatabase() = task { app.updates.installDatabase() }
    fun importDatabase(uri:Uri)=task { app.catalogue.importFile(uri);messages.emit("Food database updated") }
    fun externalSearch(provider: FoodProvider, key: String) = task {
        if(externalBusy.value) return@task
        externalBusy.value=true
        try { externalResults.value=providers.search(foodQuery.value.trim(),provider,key)
            messages.emit("External search complete")
        } finally { externalBusy.value=false }
    }
    fun clearExternal() { externalResults.value=emptyList() }
    private suspend fun findFood(id: String) = if(id.startsWith("catalogue:"))
        app.catalogue.find(id) ?: app.database.dao().food(id) else app.database.dao().food(id)
    override fun onCleared() { app.preferences.unlisten(preferenceListener) }

    fun settings(value: Preferences) {
        val wasAutomatic = app.preferences.read().autoSync
        app.preferences.save(value)
        if (wasAutomatic != value.autoSync) {
            SyncScheduler.periodic(app, value.autoSync)
            if (value.autoSync) SyncScheduler.now(app)
        }
    }
    private fun task(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { messages.emit(error.message ?: "Could not complete this action") }
    }
    fun saveFood(food: Food) = task {
        require(food.name.isNotBlank()) { "Enter a food name" }
        food.nutrients() // Validate imported or typed source values before persistence.
        app.database.dao().saveFood(food)
        messages.emit("Food saved")
    }
    fun selectMeal(meal: Int) = app.preferences.mealSelected(meal)
    fun favorite(food: Food, selected: Boolean) = task {
        app.database.withTransaction {
            if (selected) {
                app.database.dao().saveFood(food)
                app.database.dao().favorite(FavoriteFood(food.id, System.currentTimeMillis()))
            } else app.database.dao().unfavorite(food.id)
        }
    }
    fun addWater(amountMl: Double, date: LocalDate) = task {
        require(amountMl.isFinite() && amountMl in 1.0..100000.0)
        val entry = WaterEntry(amountMl = amountMl, time = date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        app.database.dao().saveWater(entry)
        lastWaterId.value = entry.id
    }
    fun deleteWater(id: String) = task {
        app.database.dao().deleteWater(id)
        if (lastWaterId.value == id) lastWaterId.value = null
    }
    fun undoWater() { lastWaterId.value?.let(::deleteWater) }
    fun add(food: Food, grams: Double, meal: Int, date: LocalDate, editing: DiaryEntry? = null,
        isSupplement: Boolean = editing?.isSupplement ?: false, kind: String = editing?.supplementKind ?: "OTHER") = task {
        val nutrients = food.nutrients().portion(grams)
        val localTime = LocalTime.now()
        val time = date.atTime(localTime).atZone(ZoneId.systemDefault())
        val entry = DiaryEntry(foodId = food.id, name = food.name, source = food.source,
            grams = grams, mealType = meal, time = time.toInstant().toEpochMilli(),
            offsetSeconds = time.offset.totalSeconds, kcal = nutrients.kcal,
            protein = nutrients.protein, carbs = nutrients.carbs, fat = nutrients.fat,
            fiber = nutrients.fiber, sugar = nutrients.sugar, sodium = nutrients.sodium,
            isSupplement = isSupplement, supplementKind = if (isSupplement) kind else "OTHER")
        val saved = if (editing == null) entry else entry.copy(id = editing.id,
            time = editing.time, offsetSeconds = editing.offsetSeconds,
            version = editing.version + 1, syncedVersion = editing.syncedVersion)
        app.database.withTransaction {
            writeFoodDiary(app.database.dao(), food, saved)
        }
        selectMeal(meal)
        if (preferences.value.autoSync) SyncScheduler.now(app)
        messages.emit(if (editing == null) "Added to diary" else "Entry updated")
    }
    fun delete(entry: DiaryEntry) = task {
        app.database.withTransaction {
            app.database.dao().intakeForDiary(entry.id)?.let { app.database.dao().deleteIntake(it.id) }
            app.database.dao().deleteEntry(entry.id)
        }
        if (preferences.value.autoSync) SyncScheduler.now(app)
        messages.emit("Entry deleted; its Health Connect deletion is queued")
    }
    fun repeat(entry: DiaryEntry, date: LocalDate) = task {
        val now = date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault())
        val repeated = entry.copy(id = java.util.UUID.randomUUID().toString(),
            time = now.toInstant().toEpochMilli(), offsetSeconds = now.offset.totalSeconds,
            version = 1, syncedVersion = 0, deleted = false)
        app.database.withTransaction {
            app.database.dao().saveEntry(repeated)
            app.database.dao().intakeForDiary(entry.id)?.let { intake ->
                app.database.dao().saveIntake(intake.copy(id = java.util.UUID.randomUUID().toString(),
                    time = repeated.time, diaryEntryId = repeated.id))
            }
        }
        selectMeal(entry.mealType)
        if (preferences.value.autoSync) SyncScheduler.now(app)
        messages.emit("Repeated meal entry")
    }

    fun saveSupplement(product: Supplement) = task {
        require(product.name.isNotBlank()) { "Enter a supplement name" }
        require(product.dailyTarget == null || (product.dailyTarget.isFinite() && product.dailyTarget > 0)) {
            "Enter a positive target or leave it blank"
        }
        val unit = DoseUnit.valueOf(product.unit)
        val grams = doseGrams(1.0, unit, product.gramsPerUnit)
        require(product.foodId == null || grams != null) { "Enter the weight of one serving to calculate nutrition" }
        if (product.foodId != null) {
            val food=foods.value.find { it.id==product.foodId } ?: findFood(product.foodId)
            require(food != null) { "Linked food was not found" }
            app.database.dao().saveFood(food)
        }
        app.database.dao().saveSupplement(product.copy(name = product.name.trim()))
        messages.emit("Supplement saved")
    }

    fun logSupplement(product: Supplement, quantity: Double, date: LocalDate, editing: SupplementIntake? = null) = task {
        val grams = doseGrams(quantity, DoseUnit.valueOf(product.unit), product.gramsPerUnit)
        val dao = app.database.dao()
        val food = product.foodId?.let { findFood(it) ?: error("Linked food was not found") }
        val instant = if (editing != null) Instant.ofEpochMilli(editing.time)
            else date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant()
        val intakeId = editing?.id ?: java.util.UUID.randomUUID().toString()
        app.database.withTransaction {
            val oldDiary = editing?.diaryEntryId?.let { dao.entry(it) }
            val diaryId = if (food != null) {
                val weight = grams ?: error("A serving weight is required")
                val nutrients = oldDiary?.nutrients()?.scaled(weight / oldDiary.grams) ?: food.nutrients().portion(weight)
                val entry = DiaryEntry(id = oldDiary?.id ?: java.util.UUID.randomUUID().toString(),
                    foodId = food.id, name = product.name, source = oldDiary?.source ?: food.source,
                    grams = weight, mealType = oldDiary?.mealType ?: preferences.value.lastMealType,
                    time = instant.toEpochMilli(), offsetSeconds = oldDiary?.offsetSeconds ?: instant.atZone(ZoneId.systemDefault()).offset.totalSeconds,
                    kcal = nutrients.kcal, protein = nutrients.protein, carbs = nutrients.carbs, fat = nutrients.fat,
                    fiber = nutrients.fiber, sugar = nutrients.sugar, sodium = nutrients.sodium,
                    version = oldDiary?.version?.plus(1) ?: 1, syncedVersion = oldDiary?.syncedVersion ?: 0,
                    isSupplement = true, supplementKind = product.kind)
                dao.saveEntry(entry)
                entry.id
            } else {
                oldDiary?.let { dao.deleteEntry(it.id) }
                null
            }
            dao.saveIntake(SupplementIntake(id = intakeId, productId = product.id, name = product.name,
                quantity = quantity, unit = product.unit, time = instant.toEpochMilli(), diaryEntryId = diaryId))
        }
        if (preferences.value.autoSync && food != null) SyncScheduler.now(app)
        messages.emit(if (editing == null) "Supplement logged" else "Supplement dose updated")
    }

    fun deleteSupplementIntake(intake: SupplementIntake) = task {
        app.database.withTransaction {
            app.database.dao().deleteIntake(intake.id)
            intake.diaryEntryId?.let { app.database.dao().deleteEntry(it) }
        }
        if (preferences.value.autoSync && intake.diaryEntryId != null) SyncScheduler.now(app)
        messages.emit("Supplement dose deleted")
    }
    fun importCatalogue(uri: Uri) = task {
        val imported = withContext(Dispatchers.IO) {
            val input = app.contentResolver.openInputStream(uri) ?: error("Cannot open catalogue")
            val bytes = input.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count == -1) break
                    require(output.size() + count <= 10 * 1024 * 1024) { "Catalogue must be smaller than 10 MB" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val array = JSONArray(bytes.toString(Charsets.UTF_8))
            require(array.length() <= 20000) { "Import at most 20,000 foods at a time" }
            (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                fun number(key: String): Double? = if (row.has(key) && !row.isNull(key)) row.getDouble(key) else null
                Food(id = row.getString("id"), name = row.getString("name"),
                    brand = row.optString("brand"), source = row.getString("source"),
                    sourceUrl = row.optString("sourceUrl"), kcal = number("kcal"),
                    protein = number("protein"), carbs = number("carbs"), fat = number("fat"),
                    fiber = number("fiber"), sugar = number("sugar"), sodium = number("sodium"))
                    .also { require(it.id.contains(':') && it.name.isNotBlank() && it.source.isNotBlank()) {
                        "Each food needs a namespaced ID, name and source"
                    }; it.nutrients() }
            }
        }
        require(imported.map { it.id }.distinct().size == imported.size) { "Duplicate food IDs in catalogue" }
        app.database.withTransaction { imported.forEach { app.database.dao().saveFood(it) } }
        messages.emit("Imported ${imported.size} foods")
    }
}
