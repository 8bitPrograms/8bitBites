// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class BiteMigration1To2(context: Context) : Migration(1, 2) {
    private val statements = context.assets.open("migrations/1-2.sql").bufferedReader().use { it.readText() }
        .split(';').map(String::trim).filter(String::isNotEmpty)
    override fun migrate(db: SupportSQLiteDatabase) { statements.forEach(db::execSQL) }
}
