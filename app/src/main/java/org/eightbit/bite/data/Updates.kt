package org.eightbit.bite.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eightbit.bite.BuildConfig
import org.eightbit.bite.core.DatabaseRelease
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppRelease(val code: Long, val name: String, val highlights: Boolean, val notes: Map<String,List<String>>)
data class UpdateState(val checking: Boolean = false, val installing: Boolean = false,
    val app: AppRelease? = null, val database: DatabaseRelease? = null,
    val appError: Boolean = false, val databaseError: Boolean = false, val installError: Boolean = false)
data class FeatureNews(val appCode: Int, val databaseVersion: String?, val notes: List<String>)

class Updates(private val context: Context, private val catalogue: CatalogueStore) {
    val state = MutableStateFlow(UpdateState())
    private val checkGate = Mutex()
    private val installGate = Mutex()
    private var lastAutomaticCheck = 0L
    private val repo = BuildConfig.UPDATE_REPOSITORY
    private val root = "https://raw.githubusercontent.com/$repo/main/"
    val appReleaseUrl get() = "https://github.com/$repo/releases"

    private fun connection(url: String): HttpURLConnection {
        require(URL(url).protocol == "https")
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout=15000; readTimeout=20000; instanceFollowRedirects=false
            setRequestProperty("User-Agent", "8bitBites/${BuildConfig.VERSION_NAME} ($appReleaseUrl)")
        }
    }
    private fun json(path: String): JSONObject {
        val connection = connection(root+path)
        try {
            require(connection.responseCode == 200)
            val data = connection.inputStream.use { it.readBytesBounded(256 * 1024) }
            return JSONObject(data.toString(Charsets.UTF_8)).also { require(it.getInt("formatVersion") == 1) }
        } finally { connection.disconnect() }
    }
    suspend fun check(manual: Boolean = false) = withContext(Dispatchers.IO) { checkGate.withLock {
        val now = System.currentTimeMillis()
        if (!manual && now-lastAutomaticCheck < 15*60*1000) return@withLock
        lastAutomaticCheck=now
        state.value=state.value.copy(checking=true, appError=false, databaseError=false)
        var app: AppRelease? = null; var db: DatabaseRelease? = null
        var appError=false; var dbError=false
        try {
            val data=json("app-update.json")
            app=AppRelease(data.getLong("versionCode"),data.getString("versionName"),data.optBoolean("highlights"),data.releaseNotes())
        } catch (cancelled: CancellationException) {
            lastAutomaticCheck=0L
            state.value=state.value.copy(checking=false)
            throw cancelled
        } catch (_: Exception) { appError=true }
        try {
            val data=json("database/manifest.json")
            db=DatabaseRelease(data.getString("version"),data.getInt("schema"),data.getLong("minimumAppCode"),
                data.getString("file"),data.getLong("bytes"),data.getString("sha256"),data.optBoolean("highlights"),data.releaseNotes())
        } catch (cancelled: CancellationException) { lastAutomaticCheck=0L; throw cancelled } catch (_: Exception) { dbError=true }
        finally { state.value=state.value.copy(checking=false, app=app, database=db, appError=appError,databaseError=dbError) }
    } }

    suspend fun installDatabase() = withContext(Dispatchers.IO) { installGate.withLock {
        val release=state.value.database ?: return@withLock
        if (!release.compatible(BuildConfig.VERSION_CODE.toLong())) return@withLock
        if (catalogue.info.value?.let { !release.newerThan(it.version) } == true) return@withLock
        state.value=state.value.copy(installing=true,installError=false)
        val candidate=File.createTempFile("catalogue-", ".sqlite",context.cacheDir)
        try {
            val connection=connection(root+"database/"+release.file)
            try {
                require(connection.responseCode == 200)
                val hash=MessageDigest.getInstance("SHA-256"); var count=0L
                connection.inputStream.use { input -> candidate.outputStream().use { output ->
                    val buffer=ByteArray(32768)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n=input.read(buffer); if(n<0) break
                        count+=n; require(count<=release.bytes)
                        hash.update(buffer,0,n); output.write(buffer,0,n)
                    }
                } }
                require(count==release.bytes && hash.digest().joinToString("") { "%02x".format(it) }.equals(release.sha256,true))
            } finally { connection.disconnect() }
            catalogue.install(candidate,release)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state.value=state.value.copy(installError=true) }
        finally { candidate.delete(); state.value=state.value.copy(installing=false) }
    } }

    fun news(language: String): FeatureNews? {
        val seen=context.getSharedPreferences("feature-news",Context.MODE_PRIVATE)
        val app=JSONObject(context.assets.open("app-whats-new.json").bufferedReader().use { it.readText() })
        val lines=mutableListOf<String>()
        if(seen.getInt("app",0)<BuildConfig.VERSION_CODE && app.optBoolean("highlights"))
            lines += app.releaseNotes()[language] ?: app.releaseNotes()["en"].orEmpty()
        val db=catalogue.info.value
        if(db!=null && seen.getString("database",null)!=db.version && db.highlights)
            lines += db.notes[language] ?: db.notes["en"].orEmpty()
        return if(lines.isEmpty()) null else FeatureNews(BuildConfig.VERSION_CODE,db?.version,lines.distinct())
    }
    fun acknowledge(news: FeatureNews) {
        context.getSharedPreferences("feature-news",Context.MODE_PRIVATE).edit()
            .putInt("app",news.appCode).apply {
                if(news.databaseVersion!=null) putString("database",news.databaseVersion)
            }.apply()
    }
}

fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
    while(true) { val n=read(buffer); if(n<0)break; require(output.size()+n<=limit); output.write(buffer,0,n) }
    return output.toByteArray()
}
