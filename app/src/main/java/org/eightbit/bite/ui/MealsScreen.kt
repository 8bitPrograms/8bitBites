// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.eightbit.bite.data.DiaryEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun MealsScreen(modifier:Modifier,date:LocalDate,entries:List<DiaryEntry>,changeDate:(LocalDate)->Unit,
    add:()->Unit,edit:(DiaryEntry)->Unit,repeat:(DiaryEntry)->Unit,delete:(DiaryEntry)->Unit) {
    val zone=ZoneId.systemDefault()
    val start=date.atStartOfDay(zone).toInstant().toEpochMilli();val end=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val daily=entries.filter { !it.deleted && it.time in start until end }
    val locale=LocalConfiguration.current.locales[0]
    LazyColumn(modifier.testTag("foods-meals"),contentPadding=PaddingValues(20.dp,12.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={changeDate(date.minusDays(1))}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,t("Previous day")) }
            Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM",locale)),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            IconButton(onClick={changeDate(date.plusDays(1))}) { Icon(Icons.AutoMirrored.Filled.ArrowForward,t("Next day")) }
        } }
        if(daily.isEmpty()) item { Text(ai("No meals recorded for this day.","Bu gün için öğün kaydı yok."))
            TextButton(onClick=add) { BiteText("Browse foods") } }
        meals.forEach { (type,label) ->
            val group=daily.filter { it.mealType==type }
            if(group.isNotEmpty()) {
                item { BiteText(label,style=MaterialTheme.typography.titleMedium) }
                items(group,key={it.id}) { row -> MealCard(row,{edit(row)},{repeat(row)},{delete(row)}) }
            }
        }
    }
}
