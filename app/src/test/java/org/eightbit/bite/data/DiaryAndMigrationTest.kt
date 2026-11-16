package org.eightbit.bite.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DiaryAndMigrationTest {
    private lateinit var db: BiteDatabase
    private val food = Food(id = "personal:whey", name = "Whey powder", kcal = 400.0, protein = 80.0)
    private fun entry(grams: Double = 35.0) = DiaryEntry(id = "breakfast", foodId = food.id, name = food.name,
        source = food.source, grams = grams, mealType = 1, time = 1000L, offsetSeconds = 0,
        kcal = grams * 4, protein = grams * .8, carbs = null, fat = null, fiber = null, sugar = null, sodium = null,
        isSupplement = true, supplementKind = "PROTEIN_POWDER")
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BiteDatabase::class.java).allowMainThreadQueries().build() }
    @After fun cleanup() { db.close() }

    @Test fun supplementIsOneBreakfastEntryAndOneIntakeEvenOnRetry() = runBlocking {
        repeat(2) { db.withTransaction { writeFoodDiary(db.dao(), food, entry()) } }
        val meals = db.dao().entries().first()
        val doses = db.dao().supplementIntakes().first()
        assertEquals(1, meals.size); assertEquals(1, doses.size)
        assertEquals(1, meals.single().mealType)
        assertEquals(28.0, meals.single().protein!!, .0001)
        assertEquals(140.0, meals.single().kcal!!, .0001)
        assertEquals(35.0, doses.single().quantity, .0001)
        assertEquals(meals.single().id, doses.single().diaryEntryId)
    }
    @Test fun editingAndUncheckingNeverDoubleCountOrDeleteTheMeal() = runBlocking {
        db.withTransaction { writeFoodDiary(db.dao(), food, entry()) }
        db.withTransaction { writeFoodDiary(db.dao(), food, entry(40.0).copy(version = 2)) }
        assertEquals(40.0, db.dao().supplementIntakes().first().single().quantity, .0001)
        db.withTransaction { writeFoodDiary(db.dao(), food, entry(40.0).copy(version = 3, isSupplement = false, supplementKind = "OTHER")) }
        assertTrue(db.dao().supplementIntakes().first().isEmpty())
        assertEquals(1, db.dao().entries().first().size)
        assertEquals(32.0, db.dao().entry("breakfast")!!.protein!!, .0001)
    }
    @Test fun existingScoopProductIsReusedWithoutAnotherLinkingStep() = runBlocking {
        db.dao().saveSupplement(Supplement(id = "legacy", name = "My whey", unit = "SCOOP", dailyTarget = 1.0,
            gramsPerUnit = 35.0, foodId = food.id, kind = "PROTEIN_POWDER"))
        db.withTransaction { writeFoodDiary(db.dao(), food, entry(70.0)) }
        assertEquals(1, db.dao().supplements().first().size)
        assertEquals("legacy", db.dao().supplementIntakes().first().single().productId)
        assertEquals(2.0, db.dao().supplementIntakes().first().single().quantity, .0001)
        assertEquals(1.0, db.dao().supplement("legacy")!!.dailyTarget!!, .0001)
    }
    @Test fun oldIntakeIdsCanBeUncheckedAndCheckedAgain() = runBlocking {
        db.dao().saveSupplement(Supplement(id = "legacy", name = "Whey powder", foodId = food.id))
        db.dao().saveFood(food); db.dao().saveEntry(entry())
        db.dao().saveIntake(SupplementIntake(id = "old-random-id", productId = "legacy", name = food.name,
            quantity = 35.0, unit = "GRAM", time = 1000, diaryEntryId = "breakfast"))
        db.withTransaction { writeFoodDiary(db.dao(), food, entry().copy(isSupplement = false)) }
        db.withTransaction { writeFoodDiary(db.dao(), food, entry(40.0)) }
        val dose = db.dao().supplementIntakes().first().single()
        assertEquals("old-random-id", dose.id)
        assertEquals(40.0, dose.quantity, .0001)
    }
    @Test fun preferencesPreserveTheProfileAndRememberTheLastMeal() {
        val context = RuntimeEnvironment.getApplication()
        val storage = context.getSharedPreferences("preferences", 0)
        storage.edit().clear().putString("name", "Demo").putInt("calorieGoal", 2300).putString("theme", "ayu")
            .putLong("avatarVersion", 123).putBoolean("themesUnlocked", true).putLong("lastSync", 987).apply()
        val store = PreferenceStore(context)
        assertFalse(store.read().proteinGoalEnabled); assertFalse(store.read().carbGoalEnabled); assertFalse(store.read().fatGoalEnabled)
        store.save(store.read().copy(age = 30, weightKg = 70.0, heightCm = 175.0, waterGoalMl = 2750.0))
        store.seedLastMeal(2)
        assertEquals(2, store.read().lastMealType)
        store.mealSelected(1)
        store.seedLastMeal(3)
        val restored = PreferenceStore(context).read()
        assertEquals("Demo", restored.name); assertEquals(2300, restored.calorieGoal)
        assertEquals(123L, restored.avatarVersion); assertTrue(restored.themesUnlocked)
        assertEquals(987L, restored.lastSync); assertEquals(1, restored.lastMealType)
        assertEquals(2750.0, restored.waterGoalMl, .0001)
        storage.edit().clear().apply()
    }
    @Test fun favoritesUseFoodIdsAndWaterUndoRemovesOnlyItsEntry() = runBlocking {
        db.dao().saveFood(food)
        repeat(2) { db.dao().favorite(FavoriteFood(food.id, 1000)) }
        assertEquals(listOf(food.id), db.dao().favoriteFoods().first().map { it.id })
        db.dao().unfavorite(food.id)
        assertTrue(db.dao().favoriteFoods().first().isEmpty())
        assertNotNull(db.dao().food(food.id))
        db.dao().saveWater(WaterEntry("one", 100.0, 1000)); db.dao().saveWater(WaterEntry("two", 750.0, 2000))
        db.dao().deleteWater("two")
        assertEquals(100.0, db.dao().waterEntries().first().sumOf { it.amountMl }, .0001)
    }
    @Test fun migrationPreservesHistoryTargetsAndNulls() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("org.eightbit.bite.data.BiteDatabase/1.json")!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO foods (id,name,brand,source,sourceUrl,kcal,protein) VALUES ('personal:whey','Whey powder','','Personal entry','',400,80)")
            old.execSQL("INSERT INTO diary (id,foodId,name,source,grams,mealType,time,offsetSeconds,kcal,protein,version,syncedVersion,deleted) VALUES ('breakfast','personal:whey','Whey powder','Personal entry',35,1,1000,0,140,28,4,3,0)")
            old.execSQL("INSERT INTO supplements (id,name,unit,dailyTarget,gramsPerUnit,foodId) VALUES ('legacy','PROTEİN TOZU','GRAM',35,NULL,'personal:whey')")
            old.execSQL("INSERT INTO supplement_intakes (id,productId,name,quantity,unit,time,diaryEntryId,deleted) VALUES ('dose','legacy','Whey powder',35,'GRAM',1000,'breakfast',0)")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, BiteDatabase::class.java, name).addMigrations(BiteMigration1To2(context)).allowMainThreadQueries().build()
        try {
            val breakfast = upgraded.dao().entries().first().single()
            assertEquals(1, breakfast.mealType); assertEquals(4L, breakfast.version); assertEquals(3L, breakfast.syncedVersion)
            assertTrue(breakfast.isSupplement); assertEquals("PROTEIN_POWDER", breakfast.supplementKind)
            assertEquals(28.0, breakfast.protein!!, .0001); assertNull(breakfast.fiber)
            assertEquals(35.0, upgraded.dao().supplement("legacy")!!.dailyTarget!!, .0001)
            assertEquals("breakfast", upgraded.dao().supplementIntakes().first().single().diaryEntryId)
            assertTrue(upgraded.dao().waterEntries().first().isEmpty())
            assertTrue(upgraded.dao().favoriteFoods().first().isEmpty())
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
