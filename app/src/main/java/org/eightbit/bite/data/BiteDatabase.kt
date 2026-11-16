// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.eightbit.bite.core.Nutrients
import org.eightbit.bite.core.DoseUnit
import java.util.UUID

@Entity(tableName = "foods")
data class Food(
    @PrimaryKey val id: String = "personal:${UUID.randomUUID()}",
    val name: String,
    val brand: String = "",
    val source: String = "Personal entry",
    val sourceUrl: String = "",
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    /** Sodium in grams per 100 g, not salt. */
    val sodium: Double? = null,
) {
    fun nutrients() = Nutrients(kcal, protein, carbs, fat, fiber, sugar, sodium)
}

@Entity(tableName = "diary", indices = [Index("time")])
data class DiaryEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val foodId: String,
    val name: String,
    val source: String,
    val grams: Double,
    val mealType: Int,
    val time: Long,
    val offsetSeconds: Int,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val sodium: Double?,
    val version: Long = 1,
    val syncedVersion: Long = 0,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isSupplement: Boolean = false,
    @ColumnInfo(defaultValue = "'OTHER'") val supplementKind: String = "OTHER",
) {
    fun nutrients() = Nutrients(kcal, protein, carbs, fat, fiber, sugar, sodium)
}

@Entity(tableName = "supplements")
data class Supplement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val unit: String = DoseUnit.GRAM.name,
    val dailyTarget: Double? = null,
    val gramsPerUnit: Double? = null,
    val foodId: String? = null,
    @ColumnInfo(defaultValue = "'OTHER'") val kind: String = "OTHER",
)

@Entity(tableName = "water_entries", indices = [Index("time")])
data class WaterEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amountMl: Double,
    val time: Long,
)

@Entity(tableName = "favorite_foods")
data class FavoriteFood(@PrimaryKey val foodId: String, val savedAt: Long)

@Entity(tableName = "supplement_intakes", indices = [Index("time"), Index(value = ["diaryEntryId"], unique = true)])
data class SupplementIntake(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val time: Long,
    val diaryEntryId: String? = null,
    val deleted: Boolean = false,
)

@Dao
interface BiteDao {
    @Query("SELECT * FROM water_entries ORDER BY time DESC")
    fun waterEntries(): Flow<List<WaterEntry>>
    @Upsert suspend fun saveWater(entry: WaterEntry)
    @Query("DELETE FROM water_entries WHERE id = :id") suspend fun deleteWater(id: String)
    @Query("SELECT f.* FROM foods f INNER JOIN favorite_foods v ON v.foodId = f.id ORDER BY v.savedAt DESC")
    fun favoriteFoods(): Flow<List<Food>>
    @Upsert suspend fun favorite(food: FavoriteFood)
    @Query("DELETE FROM favorite_foods WHERE foodId = :id") suspend fun unfavorite(id: String)
    @Query("SELECT * FROM supplements WHERE foodId = :foodId ORDER BY id LIMIT 1")
    suspend fun supplementForFood(foodId: String): Supplement?
    @Query("SELECT * FROM supplements WHERE id = :id LIMIT 1") suspend fun supplement(id: String): Supplement?
    @Query("SELECT * FROM supplements ORDER BY name COLLATE NOCASE")
    fun supplements(): Flow<List<Supplement>>
    @Upsert suspend fun saveSupplement(supplement: Supplement)
    @Query("SELECT * FROM supplement_intakes WHERE deleted = 0 ORDER BY time DESC")
    fun supplementIntakes(): Flow<List<SupplementIntake>>
    @Upsert suspend fun saveIntake(intake: SupplementIntake)
    @Query("SELECT * FROM supplement_intakes WHERE diaryEntryId = :diaryId AND deleted = 0 LIMIT 1")
    suspend fun intakeForDiary(diaryId: String): SupplementIntake?
    @Query("SELECT * FROM supplement_intakes WHERE diaryEntryId = :diaryId LIMIT 1")
    suspend fun anyIntakeForDiary(diaryId: String): SupplementIntake?
    @Query("UPDATE supplement_intakes SET deleted = 1 WHERE id = :id")
    suspend fun deleteIntake(id: String)
    @Query("SELECT * FROM foods ORDER BY name COLLATE NOCASE")
    fun foods(): Flow<List<Food>>
    @Query("SELECT * FROM foods WHERE id = :id LIMIT 1")
    suspend fun food(id: String): Food?
    @Upsert suspend fun saveFood(food: Food)
    @Query("SELECT * FROM diary WHERE deleted = 0 ORDER BY time DESC")
    fun entries(): Flow<List<DiaryEntry>>
    @Query("SELECT * FROM diary WHERE id = :id LIMIT 1")
    suspend fun entry(id: String): DiaryEntry?
    @Upsert suspend fun saveEntry(entry: DiaryEntry)
    @Query("UPDATE diary SET deleted = 1, version = version + 1 WHERE id = :id AND deleted = 0")
    suspend fun deleteEntry(id: String)
    @Query("SELECT * FROM diary WHERE version > syncedVersion ORDER BY time LIMIT 100")
    suspend fun pending(): List<DiaryEntry>
    @Query("SELECT COUNT(*) FROM diary WHERE version > syncedVersion")
    fun pendingCount(): Flow<Int>
    @Query("UPDATE diary SET syncedVersion = :version WHERE id = :id AND version = :version")
    suspend fun acknowledge(id: String, version: Long)
}

@Database(entities = [Food::class, DiaryEntry::class, Supplement::class, SupplementIntake::class, WaterEntry::class, FavoriteFood::class], version = 2, exportSchema = true)
abstract class BiteDatabase : RoomDatabase() { abstract fun dao(): BiteDao }
