package org.eightbit.bite.ui

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.json.JSONArray
private var patterns: List<Pair<String,Regex>>? = null
private fun resource(source: String) = translatedIds[source] ?: 0
fun localized(context: Context, source: String): String {
    val id=resource(source)
    if(id!=0) return context.getString(id)
    if(patterns==null) {
        val json=JSONArray(context.assets.open("localized-patterns.json").bufferedReader().use { it.readText() })
        patterns=(0 until json.length()).map { json.getJSONObject(it).let { row -> row.getString("source") to Regex(row.getString("pattern")) } }
            .sortedByDescending { it.first.length }
    }
    for((template,regex) in patterns.orEmpty()) {
        val match=regex.matchEntire(source) ?: continue
        val arguments=match.groupValues.drop(1).map { value -> resource(value).let { if(it==0)value else context.getString(it) } }.toTypedArray()
        return context.getString(resource(template),*arguments)
    }
    return source
}
@Composable fun t(source: String): String {
    LocalConfiguration.current
    return localized(LocalContext.current,source)
}
@Composable fun BiteText(text: String,modifier: Modifier=Modifier,style: TextStyle=LocalTextStyle.current,
    maxLines: Int=Int.MAX_VALUE,overflow: TextOverflow=TextOverflow.Clip) {
    Text(t(text),modifier=modifier,style=style,maxLines=maxLines,overflow=overflow)
}
