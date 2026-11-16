// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eightbit.bite.core.ModelSpec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ModelStatus(val phase:String="available",val downloaded:Long=0,val error:String="",val generation:String="",val wanted:Boolean=false,val unmetered:Boolean=true,val attempts:Int=0)
data class LocalModel(val id:String,val name:String,val file:File,val vision:Boolean?)
data class ModelLibraryState(val selected:String="",val states:Map<String,ModelStatus> = emptyMap(),val importedName:String="")

class ModelLibrary(private val context:Context) {
    val catalogue:List<ModelSpec> by lazy {
        val rows=JSONArray(context.assets.open("models.json").bufferedReader().use { it.readText() })
        (0 until rows.length()).map { rows.getJSONObject(it).let { row ->
            ModelSpec(row.getString("id"),row.getString("name"),row.getString("repository"),row.getString("revision"),
                row.getString("filename"),row.getLong("bytes"),row.getString("sha256"),row.getBoolean("vision"),
                row.getBoolean("gated"),row.getString("license"),row.getInt("memoryGb"))
        } }
    }
    private val prefs=context.getSharedPreferences("model-library",Context.MODE_PRIVATE)
    private val tokens=DownloadTokens(context)
    private val locks=mutableMapOf<String,Mutex>()
    private val connections=java.util.concurrent.ConcurrentHashMap<String,java.net.HttpURLConnection>()
    private val disconnectScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun connection(id:String,connection:java.net.HttpURLConnection?) { if(connection==null) connections.remove(id) else connections[id]=connection }
    fun interrupt(id:String) { connections[id]?.let { connection -> disconnectScope.launch { connection.disconnect() } } }
    val inferenceGate=Mutex()
    val state=MutableStateFlow(read())
    private val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->state.value=read() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun spec(id:String)=catalogue.first { it.id==id }
    @Synchronized fun lock(id:String)=locks.getOrPut(id) { Mutex() }
    fun file(id:String):File {
        require(id=="imported" || catalogue.any { it.id==id })
        return File(context.filesDir,if(id=="imported") "models/meal.litertlm" else "models/$id.litertlm")
    }
    fun partial(id:String)=File(file(id).path+".part")
    fun current(id:String):ModelStatus {
        val json=JSONObject(prefs.getString("status:$id","{}").orEmpty())
        val ready=file(id).isFile && (id=="imported" || (json.optString("phase")=="ready" && file(id).length()==spec(id).bytes))
        return ModelStatus(if(ready) "ready" else json.optString("phase","available").let { if(it=="ready") "available" else it },
            json.optLong("downloaded"),json.optString("error"),json.optString("generation"),json.optBoolean("wanted"),json.optBoolean("unmetered",true),json.optInt("attempts"))
    }
    private fun read():ModelLibraryState {
        val imported=file("imported").isFile
        val selected=prefs.getString("selected",if(imported) "imported" else "").orEmpty()
        val name=prefs.getString("imported-name",context.getSharedPreferences("ai-settings",0).getString("local-name","Imported model")).orEmpty()
        return ModelLibraryState(selected,(catalogue.map { it.id }+if(imported) listOf("imported") else emptyList()).associateWith(::current),name)
    }
    @Synchronized fun status(id:String,change:(ModelStatus)->ModelStatus) {
        val next=change(current(id))
        val json=JSONObject().put("phase",next.phase).put("downloaded",next.downloaded).put("error",next.error)
            .put("generation",next.generation).put("wanted",next.wanted).put("unmetered",next.unmetered).put("attempts",next.attempts)
        check(prefs.edit().putString("status:$id",json.toString()).commit()) { "Could not store model download state" }
    }
    fun desired(id:String,generation:String)=current(id).let { it.generation==generation && it.wanted }
    fun active():LocalModel? {
        val selected=state.value.selected
        if(selected.isBlank() || current(selected).phase!="ready") return null
        return if(selected=="imported") LocalModel(selected,state.value.importedName,file(selected),null)
        else spec(selected).let { LocalModel(selected,it.name,file(selected),it.vision) }
    }
    suspend fun select(id:String)=inferenceGate.withLock {
        require(current(id).phase=="ready") { "Download the model first" }
        prefs.edit().putString("selected",id).apply()
    }
    fun imported(name:String) { prefs.edit().putString("imported-name",name).putString("selected","imported").apply() }
    fun token(id:String)=tokens.read(id)
    fun saveToken(id:String,value:String) { if(value.isNotBlank()) tokens.save(id,value) }
    fun forgetToken(id:String)=tokens.forget(id)
    suspend fun remove(id:String)=withContext(Dispatchers.IO) { lock(id).withLock { inferenceGate.withLock {
        check(!file(id).exists() || file(id).delete()) { "Could not remove the model" }
        check(!partial(id).exists() || partial(id).delete()) { "Could not remove the partial download" }
        tokens.forget(id)
        prefs.edit().remove("status:$id").apply()
        if(state.value.selected==id) prefs.edit().putString("selected","").apply()
    } } }
}

/** The read token is encrypted only while a background transfer may need it. */
private class DownloadTokens(context:Context) {
    private val prefs=context.getSharedPreferences("model-download-secrets",Context.MODE_PRIVATE)
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("bite-hf-download",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("bite-hf-download",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(id:String,value:String) {
        require(value.length<=4096 && value.none { it=='\n' || it=='\r' }) { "Invalid Hugging Face token" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted=cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(id,Base64.encodeToString(cipher.iv+encrypted,Base64.NO_WRAP)).commit())
    }
    @Synchronized fun read(id:String):String {
        val value=prefs.getString(id,null) ?: return ""
        return try {
            val data=Base64.decode(value,Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12))) }
                .doFinal(data.copyOfRange(12,data.size)).toString(Charsets.UTF_8)
        } catch(_:Exception) { prefs.edit().remove(id).apply();"" }
    }
    fun forget(id:String) { prefs.edit().remove(id).apply() }
}
