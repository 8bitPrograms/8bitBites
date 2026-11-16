package org.eightbit.bite.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eightbit.bite.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CatalogueInfo(val version: String, val count: Int, val highlights: Boolean,
    val notes: Map<String, List<String>>)

fun JSONObject.releaseNotes(): Map<String,List<String>> = optJSONObject("notes")?.let { notes ->
    notes.keys().asSequence().associateWith { language -> notes.getJSONArray(language).let { array ->
        (0 until array.length()).map(array::getString)
    } }
} ?: emptyMap()

class CatalogueStore(private val context: Context) {
    private val gate = Mutex()
    private val current = File(context.filesDir, "catalogue/foods.sqlite")
    val info = MutableStateFlow<CatalogueInfo?>(null)
    val translator: FoodTranslator by lazy {
        val rows = JSONArray(context.assets.open("food-terms.json").bufferedReader().use { it.readText() })
        FoodTranslator((0 until rows.length()).map { rows.getJSONObject(it).let { row -> FoodTerm(row.getString("en"), row.getString("tr")) } })
    }
    private fun open(file: File) = SQLiteDatabase.openDatabase(file.absolutePath, null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)

    suspend fun initialize() = withContext(Dispatchers.IO) { gate.withLock {
        if (info.value != null) return@withLock
        current.parentFile!!.mkdirs()
        val atomic = AtomicFile(current)
        if (current.exists() || File(current.path + ".bak").exists()) atomic.openRead().close()
        else {
            val output = atomic.startWrite()
            try {
                context.assets.open("catalogue/foods.sqlite").use { it.copyTo(output) }
                atomic.finishWrite(output)
            } catch (error: Throwable) { atomic.failWrite(output); throw error }
        }
        var installed=inspect(current)
        // An APK may include a newer catalogue. Never downgrade a separately updated catalogue.
        val bundled=File(context.cacheDir,"bundled-catalogue.sqlite")
        try {
            context.assets.open("catalogue/foods.sqlite").use { input -> bundled.outputStream().use { input.copyTo(it) } }
            val candidate=inspect(bundled)
            if(DataVersion.parse(candidate.version)>DataVersion.parse(installed.version)) {
                val output=atomic.startWrite()
                try { bundled.inputStream().use { it.copyTo(output) };atomic.finishWrite(output);installed=candidate }
                catch(error:Throwable) { atomic.failWrite(output);throw error }
            }
        } finally { bundled.delete() }
        info.value = installed
    } }

    private fun inspect(file: File): CatalogueInfo = open(file).use { db ->
        require(db.version == 1) { "Unsupported catalogue schema" }
        db.rawQuery("PRAGMA quick_check", null).use { require(it.moveToFirst() && it.getString(0) == "ok") }
        for (table in listOf("foods", "metadata", "translations")) db.rawQuery(
            "SELECT type FROM sqlite_master WHERE name=?", arrayOf(table)).use { require(it.moveToFirst() && it.getString(0) == "table") }
        val meta = db.rawQuery("SELECT value FROM metadata WHERE key='release'", null).use {
            require(it.moveToFirst()); JSONObject(it.getString(0))
        }
        DataVersion.parse(meta.getString("version"))
        require(meta.getInt("schema") == 1 && meta.getLong("minimumAppCode") <= org.eightbit.bite.BuildConfig.VERSION_CODE)
        // Validate all rows before installation. Null stays unknown; no label values are invented.
        var count = 0
        db.rawQuery("SELECT * FROM foods", null).use { cursor ->
            while (cursor.moveToNext()) {
                readFood(cursor).also { require(it.id.startsWith("catalogue:") && it.name.isNotBlank()); it.nutrients() }
                count++
            }
        }
        require(count == meta.getInt("foodCount"))
        CatalogueInfo(meta.getString("version"), count, meta.optBoolean("highlights"), meta.releaseNotes())
    }

    private fun readFood(cursor: android.database.Cursor): Food {
        fun text(key: String) = cursor.getString(cursor.getColumnIndexOrThrow(key)).orEmpty()
        fun number(key: String): Double? = cursor.getColumnIndexOrThrow(key).let { if (cursor.isNull(it)) null else cursor.getDouble(it) }
        return Food(id=text("id"), name=text("name"), brand=text("brand"),
            source=text("source"), sourceUrl=text("source_url"), kcal=number("kcal"), protein=number("protein"),
            carbs=number("carbs"), fat=number("fat"), fiber=number("fiber"), sugar=number("sugar"), sodium=number("sodium"))
    }

    suspend fun search(query: String): List<Food> = withContext(Dispatchers.IO) { gate.withLock {
        if (info.value == null) return@withLock emptyList()
        val alternatives = translator.alternatives(query).map { "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%" }
        open(current).use { db ->
            val condition = alternatives.joinToString(" OR ") { "search_key LIKE ? ESCAPE '\\'" }
            db.rawQuery("SELECT * FROM foods WHERE $condition ORDER BY name LIMIT 200", alternatives.toTypedArray()).use { cursor ->
                buildList { while (cursor.moveToNext()) add(readFood(cursor)) }
            }
        }
    } }
    suspend fun find(id: String): Food? = withContext(Dispatchers.IO) { gate.withLock {
        if (info.value == null) return@withLock null
        open(current).use { db -> db.rawQuery("SELECT * FROM foods WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) readFood(it) else null
        } }
    } }

    suspend fun install(candidate: File, release: DatabaseRelease) = withContext(Dispatchers.IO) { gate.withLock {
        val next = inspect(candidate)
        require(next.version == release.version)
        val installed = info.value
        require(installed == null || release.newerThan(installed.version)) { "Database is already current" }
        val atomic = AtomicFile(current)
        val output = atomic.startWrite()
        try {
            candidate.inputStream().use { it.copyTo(output) }
            atomic.finishWrite(output)
            info.value = next
        } catch (error: Throwable) { atomic.failWrite(output); throw error }
    } }

    suspend fun importFile(uri:android.net.Uri)=withContext(Dispatchers.IO) {
        initialize()
        val file=File.createTempFile("catalogue-import-",".sqlite",context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { output ->
                val buffer=ByteArray(8192);var size=0L
                while(true) { val count=input.read(buffer);if(count<0) break
                    size+=count;require(size<=200_000_000) { "Database is too large" };output.write(buffer,0,count)
                }
            } }
            val candidate=inspect(file)
            val digest=java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer=ByteArray(8192)
                while(true) { val count=input.read(buffer);if(count<0) break;digest.update(buffer,0,count) }
            }
            val hash=digest.digest().joinToString("") { "%02x".format(it) }
            install(file,DatabaseRelease(candidate.version,1,1,"versions/${candidate.version}/foods.sqlite",file.length(),hash,candidate.highlights,candidate.notes))
        } finally { file.delete() }
    }
}
