// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import org.eightbit.bite.BiteApplication
import org.eightbit.bite.ai.*
import org.eightbit.bite.core.*
import org.eightbit.bite.data.*
import org.eightbit.bite.health.SyncScheduler
import java.io.File
import java.time.*
import java.util.UUID

data class AiReview(val id: String=UUID.randomUUID().toString(), val mention: AiFoodMention,
    val search: String=mention.name, val options: List<Food> = emptyList(), val selected: Food?=null,
    val grams: String=mention.grams?.toString().orEmpty(), val isSupplement: Boolean=false,
    val supplementKind: String=suggestedSupplementKind(mention.name).name) {
    fun weight()=grams.replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() && it>0 && it<=100000 }
}
data class AiState(val busy: Boolean=false,val status: String="",val error: String?=null,
    val text: String="",val jpeg: ByteArray?=null,val draft: List<AiReview> = emptyList(),
    val modelName: String="",val api: Boolean=false,val base: String="https://api.openai.com/v1",
    val model: String="",val key: String="",val saved: Boolean=false)

class AiViewModel(application: Application): AndroidViewModel(application) {
    private val app=application as BiteApplication
    val models=app.models
    private val modelFile=models.file("imported")
    private val settings=app.getSharedPreferences("ai-settings",0)
    private val localDiagnostics=LocalAiDiagnostics(app)
    val lastMeal get() = app.preferences.read().lastMealType
    fun selectMeal(meal: Int) = app.preferences.mealSelected(meal)
    val state=MutableStateFlow(AiState(error=localDiagnostics.recover()?.let { localAiError(app,it) },modelName=models.active()?.name.orEmpty(),
        api=settings.getBoolean("api",false),base=settings.getString("base","https://api.openai.com/v1").orEmpty(),
        model=settings.getString("model","").orEmpty()))
    private var job: Job?=null
    private val searchJobs=mutableMapOf<String,Job>()
    init { viewModelScope.launch { models.state.collect { state.update { it.copy(modelName=models.active()?.name.orEmpty()) } } } }
    fun edit(change:(AiState)->AiState) { if(!state.value.busy) state.update(change) }
    fun configure(api:Boolean,base:String=state.value.base,model:String=state.value.model) {
        edit { it.copy(api=api,base=base,model=model,error=null) }
        settings.edit().putBoolean("api",api).putString("base",base).putString("model",model).apply()
    }
    private fun run(status:String,block:suspend ()->Unit) {
        if(state.value.busy) return
        state.update { it.copy(busy=true,status=status,error=null,saved=false) }
        job=viewModelScope.launch {
            try { block() }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Throwable) {
                if(error is VirtualMachineError && error !is OutOfMemoryError) throw error
                state.update { it.copy(error=if(error is OutOfMemoryError) "Not enough memory. Try a smaller model or API mode." else
                    if(error is LinkageError) "On-device AI is unavailable on this device." else error.message ?: "Could not recognize this meal") }
            } finally { state.update { it.copy(busy=false,status="") } }
        }
    }
    fun cancel() { job?.cancel() }
    fun diagnosticDetails()=localDiagnostics.report()
    fun photo(uri:Uri,removeAfter:File?=null) {
        if(state.value.busy) { removeAfter?.delete();return }
        run("Preparing photo…") { try { val bytes=localJpeg(app,uri);state.update { it.copy(jpeg=bytes) } } finally { removeAfter?.delete() } }
    }
    fun importModel(uri:Uri)=run("Importing model…") {
        withContext(Dispatchers.IO) {
            val name=app.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst()) it.getString(0) else null
            } ?: "Imported model.litertlm"
            require(name.endsWith(".litertlm",ignoreCase=true)) { "Choose a .litertlm model" }
            modelFile.parentFile!!.mkdirs()
            val temporary=File.createTempFile("import-",".part",modelFile.parentFile)
            try {
                app.contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { output ->
                    val buffer=ByteArray(1024*1024);var total=0L
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val count=input.read(buffer);if(count<0) break
                        total+=count;require(total<=8L*1024*1024*1024 && android.os.StatFs(modelFile.parentFile!!.path).availableBytes>64*1024*1024) { "Not enough storage for this model" }
                        output.write(buffer,0,count)
                        state.update { it.copy(status="Importing model: ${total/(1024*1024)} MB") }
                    }
                    require(total>1024) { "Model file is incomplete" };output.fd.sync()
                } }
                currentCoroutineContext().ensureActive()
                models.inferenceGate.withLock {
                    check(temporary.renameTo(modelFile)) { "Could not install model" }
                    settings.edit().putString("local-name",name).apply()
                    models.imported(name)
                }
            } finally { temporary.delete() }
        }
    }
    fun removeModel() { if(!state.value.busy) viewModelScope.launch { models.active()?.let { models.remove(it.id) } } }
    fun recognize() {
        val input=state.value
        run(if(input.api) "Recognizing meal…" else "Loading model and recognizing meal…") {
            require(input.text.length<=4000 && (input.text.isNotBlank() || input.jpeg!=null)) { "Describe a meal or select a photo" }
            val output=if(input.api) apiMeal(input.base,input.key,input.model,input.text,input.jpeg)
                else models.inferenceGate.withLock {
                    val model=models.active() ?: error("Download or import a local model first")
                    require(input.jpeg==null || model.vision!=false) { "This model supports text only. Choose a photo-capable model or an API vision model." }
                    localMeal(app,model.file,input.text,input.jpeg,onStage={ stage ->
                        val tr=app.resources.configuration.locales[0].language=="tr"
                        val message=when(stage) {
                            "loading" -> if(tr) "Model yükleniyor…" else "Loading model…"
                            "preparing" -> if(tr) "Model hazırlanıyor…" else "Preparing model…"
                            "generating" -> if(tr) "Öğün çözümleniyor…" else "Recognizing meal…"
                            "closing" -> if(tr) "Sonuç hazırlanıyor…" else "Preparing result…"
                            else -> if(tr) "Model başlatılıyor…" else "Starting model…"
                        }
                        state.update { it.copy(status=message) }
                    })
                }
            val mentions=AiMeals.parse(output,photo=input.jpeg!=null)
            require(mentions.isNotEmpty()) { "No foods identified. Add a more specific description." }
            app.catalogue.initialize()
            val drafts=mentions.map { mention -> AiReview(mention=mention,options=matches(mention.name)) }
            state.update { it.copy(draft=drafts) }
        }
    }
    private suspend fun matches(query:String):List<Food> {
        val alternatives=app.catalogue.translator.alternatives(query)
        val personal=app.database.dao().foods().first().filter { food -> alternatives.any { foldFoodText("${food.name} ${food.brand}").contains(it) } }
        return (personal+app.catalogue.search(query)).distinctBy { it.id }.take(20)
    }
    fun review(id:String,change:(AiReview)->AiReview) { if(!state.value.busy) state.update { it.copy(draft=it.draft.map { row -> if(row.id==id) change(row) else row }) } }
    fun search(id:String,query:String) {
        review(id) { it.copy(search=query,selected=null) }
        searchJobs.remove(id)?.cancel()
        searchJobs[id]=viewModelScope.launch {
            delay(250)
            try {
                val result=if(query.isBlank()) emptyList() else matches(query)
                review(id) { row -> if(row.search==query) row.copy(options=result) else row }
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { state.update { it.copy(error="Could not search foods") } }
        }
    }
    fun drop(id:String) { if(!state.value.busy) state.update { it.copy(draft=it.draft.filterNot { row -> row.id==id }) } }
    fun clear() { if(!state.value.busy) state.update { it.copy(text="",jpeg=null,draft=emptyList(),error=null,saved=false) } }
    fun save(date:LocalDate,meal:Int)=run("Saving meal…") {
        val rows=state.value.draft
        require(rows.isNotEmpty() && rows.all { it.selected!=null && it.weight()!=null }) { "Select a food and weight for every item" }
        val now=date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault())
        app.database.withTransaction {
            for(row in rows) {
                val dao=app.database.dao()
                // Review IDs survive retries; a second tap cannot duplicate diary or Health Connect entries.
                val id="ai:${row.id}"
                if(dao.entry(id)!=null) continue
                val food=row.selected!!;val grams=row.weight()!!;val n=food.nutrients().portion(grams)
                writeFoodDiary(dao,food,DiaryEntry(id=id,foodId=food.id,name=food.name,source=food.source,grams=grams,mealType=meal,
                    time=now.toInstant().toEpochMilli(),offsetSeconds=now.offset.totalSeconds,kcal=n.kcal,protein=n.protein,
                    carbs=n.carbs,fat=n.fat,fiber=n.fiber,sugar=n.sugar,sodium=n.sodium,
                    isSupplement=row.isSupplement,supplementKind=if(row.isSupplement) row.supplementKind else "OTHER"))
            }
        }
        selectMeal(meal)
        if(app.preferences.read().autoSync) SyncScheduler.now(app)
        state.update { it.copy(draft=emptyList(),text="",jpeg=null,saved=true) }
    }
}
