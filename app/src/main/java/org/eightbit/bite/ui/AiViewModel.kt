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
data class AiChatMessage(val id:String=UUID.randomUUID().toString(),val user:Boolean,val text:String,val photo:ByteArray?=null,
    val wireText:String=text,val foods:List<AiFoodMention> = emptyList())

data class AiState(val busy: Boolean=false,val status: String="",val error: String?=null,
    val text: String="",val jpeg: ByteArray?=null,val draft: List<AiReview> = emptyList(),
    val modelName: String="",val provider:AiProvider=AiProvider.LOCAL,val base: String="https://api.openai.com/v1",
    val model: String="",val key: String="",val saved: Boolean=false,val messages:List<AiChatMessage> = emptyList(),
    val draftMessageId:String="",val savedMessages:Set<String> = emptySet(),val errorCode:String?=null)

class AiViewModel(application: Application): AndroidViewModel(application) {
    private val app=application as BiteApplication
    val models=app.models
    private val modelFile=models.file("imported")
    private val settings=app.getSharedPreferences("ai-settings",0)
    private val localDiagnostics=LocalAiDiagnostics(app)
    internal val chatGpt=ChatGptConnection(app)
    val lastMeal get() = app.preferences.read().lastMealType
    fun selectMeal(meal: Int) = app.preferences.mealSelected(meal)
    val state=MutableStateFlow(AiState(error=localDiagnostics.recover()?.let { localAiError(app,it) },modelName=models.active()?.name.orEmpty(),
        provider=selectedAiProvider(app),base=settings.getString("base","https://api.openai.com/v1").orEmpty(),
        model=settings.getString("model","").orEmpty()))
    private var job: Job?=null
    private var accountJob:Job?=null
    private val searchJobs=mutableMapOf<String,Job>()
    init { viewModelScope.launch { models.state.collect { state.update { it.copy(modelName=models.active()?.name.orEmpty()) } } } }
    private fun accountTask(block:suspend ()->Unit) {
        if(accountJob?.isActive==true) return
        accountJob=viewModelScope.launch(Dispatchers.IO) { try { block() } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { chatGpt.failure(error) } }
    }
    fun edit(change:(AiState)->AiState) { if(!state.value.busy) state.update(change) }
    fun configure(provider:AiProvider,base:String=state.value.base,model:String=state.value.model) {
        if(state.value.busy || chatGpt.state.value.busy) return
        val changed=provider!=state.value.provider
        edit { it.copy(provider=provider,base=base.take(500),model=model.take(200),error=null,
            messages=if(changed) emptyList() else it.messages,draft=if(changed) emptyList() else it.draft,
            draftMessageId=if(changed) "" else it.draftMessageId,jpeg=if(changed) null else it.jpeg,
            savedMessages=if(changed) emptySet() else it.savedMessages) }
        settings.edit().putString("provider",provider.name).putBoolean("api",provider==AiProvider.REMOTE).putString("base",base.take(500)).putString("model",model.take(200)).apply()
        if(changed) org.eightbit.bite.widgets.WidgetUpdates.request(app)
    }
    fun connectChatGpt(profileId:String?=null,consent:Boolean=false) { if(state.value.busy || chatGpt.state.value.busy) return
        accountTask { chatGpt.signIn(profileId,consent) } }
    fun cancelChatGpt() { accountJob?.cancel() }
    fun selectChatGptAccount(id:String) { if(state.value.busy || chatGpt.state.value.busy) return
        if(id!=chatGpt.state.value.activeId) newChat()
        accountTask { chatGpt.select(id) } }
    fun selectChatGptModel(slug:String) { if(!state.value.busy) accountTask { chatGpt.selectModel(slug) } }
    fun reloadChatGptModels() { if(!state.value.busy && !chatGpt.state.value.busy) accountTask { chatGpt.reloadModels() } }
    fun disconnectChatGpt() { if(!state.value.busy && !chatGpt.state.value.busy) {
        newChat();accountTask { chatGpt.signOut() }
    } }
    private fun run(status:String,block:suspend ()->Unit) {
        if(state.value.busy) return
        state.update { it.copy(busy=true,status=status,error=null,errorCode=null,saved=false) }
        job=viewModelScope.launch {
            try { block() }
            catch(timeout:TimeoutCancellationException) { if(!currentCoroutineContext().isActive) throw timeout
                state.update { it.copy(error=if(app.resources.configuration.locales[0].language=="tr") "Yanıt süresi doldu. Yeniden deneyebilirsin." else "The reply timed out. You can retry.") } }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Throwable) {
                if(error is VirtualMachineError && error !is OutOfMemoryError) throw error
                state.update { it.copy(errorCode=(error as? SiwcException)?.code,error=if(error is SiwcException) chatGptError(app,error.code) else if(error is OutOfMemoryError) "Not enough memory. Try a smaller model or API mode." else
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
    fun recognize()=send(false)
    fun retry()=send(true)
    private fun send(retry:Boolean) {
        val input=state.value
        val user=if(retry) input.messages.lastOrNull { it.user } ?: return else AiChatMessage(user=true,text=input.text.trim(),photo=input.jpeg)
        run(if(app.resources.configuration.locales[0].language=="tr") "Yanıt hazırlanıyor…" else "Preparing reply…") {
            require(user.text.length<=4000 && (user.text.isNotBlank() || user.photo!=null)) { "Describe a meal or select a photo" }
            if(input.provider==AiProvider.LOCAL) require(user.photo==null || models.active()?.vision!=false) { "This model supports text only. Choose a photo-capable model or an API vision model." }
            val conversation=if(retry) input.messages else (input.messages+user).takeLast(80)
            val turns=conversation.map { AiChatTurn(if(it.user) "user" else "assistant",it.wireText,it.photo) }
            state.update { it.copy(messages=conversation,text="",jpeg=null,draft=emptyList(),draftMessageId="") }
            val output=when(input.provider) {
                AiProvider.REMOTE -> apiMeal(input.base,input.key,input.model,user.text,user.photo,turns)
                AiProvider.CHATGPT -> withTimeout(180000) { chatGpt.chat(turns) }
                AiProvider.LOCAL -> models.inferenceGate.withLock {
                    val model=models.active() ?: error("Download or import a local model first")
                    localMeal(app,model.file,AiChat.localPrompt(turns),user.photo,chat=true,onStage={ stage ->
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
            }
            val reply=AiChat.parse(output,photo=user.photo!=null)
            if(reply.foods.isNotEmpty()) app.catalogue.initialize()
            val drafts=reply.foods.map { mention -> AiReview(mention=mention,options=matches(mention.name)) }
            val message=AiChatMessage(user=false,text=reply.reply.ifBlank { if(app.resources.configuration.locales[0].language=="tr") "Öğünü kaydetmeden önce kontrol edebilirsin." else "Review the meal before saving." },
                wireText=output,foods=reply.foods)
            state.update { it.copy(messages=(it.messages+message).takeLast(80),draft=drafts,draftMessageId=message.id) }
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
    fun clear() { if(!state.value.busy) state.update { it.copy(draft=emptyList(),draftMessageId="",error=null) } }
    fun newChat() { if(!state.value.busy) { searchJobs.values.forEach { it.cancel() };searchJobs.clear()
        state.update { it.copy(text="",jpeg=null,draft=emptyList(),error=null,saved=false,messages=emptyList(),draftMessageId="",savedMessages=emptySet()) } } }
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
        val reply=if(app.resources.configuration.locales[0].language=="tr") "Öğün günlüğüne eklendi." else "Meal added to your diary."
        val message=AiChatMessage(user=false,text=reply,wireText=org.json.JSONObject().put("reply",reply).put("saved",true)
            .put("foods",org.json.JSONArray().apply { rows.forEach { row -> put(org.json.JSONObject().put("name",row.selected!!.name).put("grams",row.weight()).put("estimated",false).put("note","")) } }).toString())
        state.update { it.copy(draft=emptyList(),text="",jpeg=null,saved=true,savedMessages=it.savedMessages+it.draftMessageId,
            draftMessageId="",messages=(it.messages+message).takeLast(80)) }
    }
}
