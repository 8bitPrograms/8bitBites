// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eightbit.bite.BiteApplication
import org.eightbit.bite.data.DiaryEntry
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

val nutritionPermissions = setOf(HealthPermission.getWritePermission(NutritionRecord::class))
private val syncMutex = Mutex()

object SyncScheduler {
    fun now(context: Context, manual: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<HealthSyncWorker>()
            .setInputData(workDataOf("manual" to manual))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        // Append rather than KEEP: a save racing a running worker always gets another pass.
        WorkManager.getInstance(context).enqueueUniqueWork(if (manual) "health-sync-manual" else "health-sync-now",
            ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun periodic(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (enabled) manager.enqueueUniquePeriodicWork("health-sync-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.HOURS).build())
        else {
            manager.cancelUniqueWork("health-sync-periodic")
            manager.cancelUniqueWork("health-sync-now")
        }
    }
}

fun DiaryEntry.toHealthRecord(): NutritionRecord {
    val start = Instant.ofEpochMilli(time)
    val offset = ZoneOffset.ofTotalSeconds(offsetSeconds)
    return NutritionRecord(
        startTime = start, endTime = start.plusMillis(1),
        startZoneOffset = offset, endZoneOffset = offset,
        name = name, mealType = mealType,
        energy = kcal?.let(Energy::kilocalories), protein = protein?.let(Mass::grams),
        totalCarbohydrate = carbs?.let(Mass::grams), totalFat = fat?.let(Mass::grams),
        dietaryFiber = fiber?.let(Mass::grams), sugar = sugar?.let(Mass::grams),
        sodium = sodium?.let(Mass::grams),
        metadata = Metadata.manualEntry(clientRecordId = id, clientRecordVersion = version),
    )
}

class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = syncMutex.withLock { synchronize() }

    private suspend fun synchronize(): Result {
        val app = applicationContext as BiteApplication
        val manual = inputData.getBoolean("manual", false)
        if (!manual && !app.preferences.read().autoSync) return Result.success()
        if (HealthConnectClient.getSdkStatus(applicationContext) != HealthConnectClient.SDK_AVAILABLE) {
            app.preferences.syncStatus("Health Connect is unavailable or needs updating")
            return Result.failure()
        }
        try {
            val client = HealthConnectClient.getOrCreate(applicationContext)
            if (!client.permissionController.getGrantedPermissions().containsAll(nutritionPermissions)) {
                app.preferences.syncStatus("Nutrition permission is required")
                return Result.failure()
            }
            val dao = app.database.dao()
            repeat(100) {
                if (!manual && !app.preferences.read().autoSync) return Result.success()
                val batch = dao.pending()
                if (batch.isEmpty()) {
                    app.preferences.syncStatus("Up to date", success = true)
                    return Result.success()
                }
                for (entry in batch) {
                    if (!manual && !app.preferences.read().autoSync) return Result.success()
                    if (entry.deleted) client.deleteRecords(NutritionRecord::class,
                        recordIdsList = emptyList(), clientRecordIdsList = listOf(entry.id))
                    else client.insertRecords(listOf(entry.toHealthRecord()))
                    // Only acknowledge the exact snapshot exported. A concurrent edit stays pending.
                    dao.acknowledge(entry.id, entry.version)
                }
            }
            return Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            app.preferences.syncStatus("Nutrition permission was revoked")
            return Result.failure()
        } catch (_: Exception) {
            app.preferences.syncStatus("Sync failed; entries are safely queued")
            return if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
