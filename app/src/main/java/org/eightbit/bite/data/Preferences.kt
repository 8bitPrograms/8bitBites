// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.data

import android.content.Context

data class Preferences(
    val name: String = "",
    val calorieGoal: Int = 2000,
    val theme: String = "system",
    val appearance: String = "system",
    val autoSync: Boolean = false,
    val lastSync: Long = 0,
    val syncMessage: String = "Not connected",
    val avatarVersion: Long = 0,
    val themesUnlocked: Boolean = false,
    val age: Int? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val sex: String = "",
    val waterEnabled: Boolean = true,
    val waterGoalMl: Double = 2000.0,
    val waterUnitMl: Double = 100.0,
    val waterInLiters: Boolean = false,
    val proteinGoalEnabled: Boolean = false,
    val proteinGoal: Double? = null,
    val carbGoalEnabled: Boolean = false,
    val carbGoal: Double? = null,
    val fatGoalEnabled: Boolean = false,
    val fatGoal: Double? = null,
    val powderGoal: Double? = null,
    val creatineGoal: Double? = null,
    val showSupplements: Boolean = true,
    val showTrends: Boolean = true,
    val lastMealType: Int = 4,
)

class PreferenceStore(context: Context) {
    private val store = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    fun read() = Preferences(
        name = store.getString("name", "").orEmpty(),
        calorieGoal = store.getInt("calorieGoal", 2000),
        theme = store.getString("theme", "system") ?: "system",
        appearance = store.getString("appearance", "system") ?: "system",
        autoSync = store.getBoolean("autoSync", false),
        lastSync = store.getLong("lastSync", 0),
        syncMessage = store.getString("syncMessage", "Not connected").orEmpty(),
        avatarVersion = store.getLong("avatarVersion",0),
        themesUnlocked = store.getBoolean("themesUnlocked",false),
        age = store.getString("age",null)?.toIntOrNull(),
        weightKg = number("weightKg"), heightCm = number("heightCm"),
        sex = store.getString("sex", "").orEmpty(),
        waterEnabled = store.getBoolean("waterEnabled", true),
        waterGoalMl = number("waterGoalMl") ?: 2000.0,
        waterUnitMl = number("waterUnitMl") ?: 100.0,
        waterInLiters = store.getBoolean("waterInLiters", false),
        proteinGoalEnabled = store.getBoolean("proteinGoalEnabled", false), proteinGoal = number("proteinGoal"),
        carbGoalEnabled = store.getBoolean("carbGoalEnabled", false), carbGoal = number("carbGoal"),
        fatGoalEnabled = store.getBoolean("fatGoalEnabled", false), fatGoal = number("fatGoal"),
        powderGoal = number("powderGoal"), creatineGoal = number("creatineGoal"),
        showSupplements = store.getBoolean("showSupplements", true), showTrends = store.getBoolean("showTrends", true),
        lastMealType = store.getInt("lastMealType", 4).takeIf { it in 1..4 } ?: 4,
    )

    private fun number(key: String) = store.getString(key, null)?.toDoubleOrNull()?.takeIf { it.isFinite() }

    fun save(value: Preferences) {
        require(value.calorieGoal in 1..100000)
        require(value.age == null || value.age in 1..120)
        require(value.weightKg == null || value.weightKg.isFinite() && value.weightKg in 10.0..500.0)
        require(value.heightCm == null || value.heightCm.isFinite() && value.heightCm in 50.0..250.0)
        require(value.sex in setOf("", "female", "male"))
        require(value.waterGoalMl.isFinite() && value.waterGoalMl in 1.0..100000.0)
        require(value.waterUnitMl.isFinite() && value.waterUnitMl in 1.0..100000.0)
        for (goal in listOf(value.proteinGoal, value.carbGoal, value.fatGoal, value.powderGoal, value.creatineGoal))
            require(goal == null || goal.isFinite() && goal in 0.001..100000.0)
        require(!value.proteinGoalEnabled || value.proteinGoal != null)
        require(!value.carbGoalEnabled || value.carbGoal != null)
        require(!value.fatGoalEnabled || value.fatGoal != null)
        // Sync status is owned by the worker. Settings writes must not overwrite it.
        store.edit().putString("name", value.name).putInt("calorieGoal", value.calorieGoal)
            .putString("theme", value.theme).putString("appearance", value.appearance)
            .putBoolean("autoSync", value.autoSync)
            .putString("age", value.age?.toString()).putString("weightKg", value.weightKg?.toString())
            .putString("heightCm", value.heightCm?.toString()).putString("sex", value.sex)
            .putBoolean("waterEnabled", value.waterEnabled).putString("waterGoalMl", value.waterGoalMl.toString())
            .putString("waterUnitMl", value.waterUnitMl.toString()).putBoolean("waterInLiters", value.waterInLiters)
            .putBoolean("proteinGoalEnabled", value.proteinGoalEnabled).putString("proteinGoal", value.proteinGoal?.toString())
            .putBoolean("carbGoalEnabled", value.carbGoalEnabled).putString("carbGoal", value.carbGoal?.toString())
            .putBoolean("fatGoalEnabled", value.fatGoalEnabled).putString("fatGoal", value.fatGoal?.toString())
            .putString("powderGoal", value.powderGoal?.toString()).putString("creatineGoal", value.creatineGoal?.toString())
            .putBoolean("showSupplements", value.showSupplements).putBoolean("showTrends", value.showTrends).apply()
    }

    fun syncStatus(message: String, success: Boolean = false) {
        val editor = store.edit().putString("syncMessage", message)
        if (success) editor.putLong("lastSync", System.currentTimeMillis())
        editor.apply()
    }

    fun avatarChanged(present:Boolean) { store.edit().putLong("avatarVersion",if(present) System.currentTimeMillis() else 0).apply() }
    fun unlockThemes() { store.edit().putBoolean("themesUnlocked",true).apply() }
    fun mealSelected(meal: Int) { require(meal in 1..4); store.edit().putInt("lastMealType",meal).apply() }
    fun seedLastMeal(meal: Int?) { if (!store.contains("lastMealType") && meal != null && meal in 1..4) mealSelected(meal) }

    fun listen(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        store.registerOnSharedPreferenceChangeListener(listener)
    fun unlisten(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        store.unregisterOnSharedPreferenceChangeListener(listener)
}
