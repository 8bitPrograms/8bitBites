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

data class AiReview(val id:String=UUID.randomUUID().toString(),val mention:AiFoodMention,
    val grams:String=mention.grams?.toString().orEmpty(),val isSupplement:Boolean=false,
    val supplementKind:String=suggestedSupplementKind(mention.name).name) {
    fun weight()=grams.replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() && it>0 && it<=100000 }
    fun nutrients()=weight()?.let { (mention.nutrition ?: Nutrients()).portion(it) }
}

data class AiState(val busy:Boolean=false,val status:String="",val error:String?=null,
    val text:String="",val jpeg:ByteArray?=null,val draft:List<AiReview> = emptyList(),
    val modelName:String="",val provider:AiProvider=AiProvider.LOCAL,val base:String="https://api.openai.com/v1",
    val model:String="",val key:String="",val saved:Boolean=false,val messages:List<AiChatMessage> = emptyList(),
    val draftMessageId:String="",val savedMessages:Set<String> = emptySet(),val errorCode:String?=null,
    val sessionId:String=UUID.randomUUID().toString(),val createdAt:Long=System.currentTimeMillis(),val accountId:String="")

@OptIn(FlowPreview::class)
class AiViewModel(application:Application):AndroidViewModel(application) {
    private val app=application as BiteApplication
    val models=app.models
    private val modelFile=models.file("imported")
    private val settings=app.getSharedPreferences("ai-settings",0)
    private val localDiagnostics=LocalAiDiagnostics(app)
    internal val chatGpt=app.chatGpt
    private val historyStore=app.chatHistory
    private val historyPrefs=app.getSharedPreferences("ai-history",0)
    val history=MutableStateFlow<List<ChatSummary>>(emptyList())
    val historyLoading=MutableStateFlow(false)
    val historyError=MutableStateFlow<String?>(null)
    val lastMeal get()=app.preferences.read().lastMealType
    fun selectMeal(meal:Int)=app.preferences.mealSelected(meal)
    val state=MutableStateFlow(AiState(busy=true,error=localDiagnostics.recover()?.let { localAiError(app,it) },
        modelName=models.active()?.name.orEmpty(),provider=selectedAiProvider(app),
        base=settings.getString("base","https://api.openai.com/v1").orEmpty(),model=settings.getString("model","").orEmpty()))
    private var job:Job?=null
    private var accountJob:Job?=null
    private var lastSave:Job?=null
    private var sessionModel=""

    init {
        viewModelScope.launch { models.state.collect { state.update { it.copy(modelName=models.active()?.name.orEmpty()) } } }
        viewModelScope.launch {
            try {
                val sessions=withContext(Dispatchers.IO) { historyStore.list() };history.value=sessions
                val id=historyPrefs.getString("active",null) ?: sessions.firstOrNull()?.id
                if(!id.isNullOrBlank()) restore(withContext(Dispatchers.IO) { historyStore.load(id) })
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { historyError.value="Could not restore the conversation" }
            finally { state.update { it.copy(busy=false) } }
        }
        viewModelScope.launch {
            state.map { Triple(it.sessionId,it.text,it.draft) }.distinctUntilChanged().debounce(600).collect { persistCurrent() }
        }
        viewModelScope.launch {
            chatGpt.state.map { it.activeId }.distinctUntilChanged().collect { account ->
                val s=state.value
                if(!s.busy && s.provider==AiProvider.CHATGPT && s.accountId.isNotBlank() && s.accountId!=account) newChat()
            }
        }
    }
    private fun persistCurrent() {
        val s=state.value
        if(s.messages.isEmpty()) return
        val snapshot=ChatSession(s.sessionId,s.createdAt,System.currentTimeMillis(),s.provider,s.accountId,
            if(s.provider==AiProvider.REMOTE) s.base else "",sessionModel,s.messages,s.draftMessageId,
            s.draft.map { ChatReviewState(it.id,it.grams,it.isSupplement,it.supplementKind,it.mention) },s.savedMessages,s.text)
        historyPrefs.edit().putString("active",s.sessionId).apply()
        val previous=lastSave
        lastSave=app.persistChat {
            previous?.join()
            try {
                val saved=historyStore.save(snapshot)
                val photos=saved.messages.associateBy { it.id }
                state.update { current -> if(current.sessionId!=saved.id) current else current.copy(messages=current.messages.map { message ->
                    photos[message.id]?.let { stored -> if(message.photo!=null && stored.photoPath!=null) message.copy(photo=null,photoPath=stored.photoPath) else message } ?: message
                }) }
            } catch(_:Exception) { historyError.value="Could not save this conversation on the phone" }
        }
    }
    private suspend fun restore(session:ChatSession) {
        if(session.provider==AiProvider.CHATGPT && session.accountId.isNotBlank() &&
            chatGpt.state.value.profiles.any { it.id==session.accountId }) chatGpt.select(session.accountId)
        sessionModel=session.model
        val source=session.messages.firstOrNull { it.id==session.draftMessageId }
        val rows=session.reviews.mapIndexedNotNull { index,saved ->
            val mention=saved.food ?: source?.foods?.getOrNull(index) ?: return@mapIndexedNotNull null
            AiReview(saved.id,mention,grams=saved.grams,isSupplement=saved.supplement,supplementKind=saved.kind)
        }
        val previous=state.value
        val base=if(session.provider==AiProvider.REMOTE) session.endpoint.ifBlank { previous.base } else previous.base
        state.value=previous.copy(provider=session.provider,base=base,
            model=if(session.provider==AiProvider.REMOTE) session.model else previous.model,key=if(base==previous.base) previous.key else "",
            messages=session.messages,text=session.input,jpeg=null,draft=rows,draftMessageId=session.draftMessageId,
            savedMessages=session.saved,sessionId=session.id,createdAt=session.created,accountId=session.accountId,
            error=null,errorCode=null,saved=false)
        settings.edit().putString("provider",session.provider.name).putBoolean("api",session.provider==AiProvider.REMOTE)
            .putString("base",base).putString("model",state.value.model).apply()
        historyPrefs.edit().putString("active",session.id).apply()
        org.eightbit.bite.widgets.WidgetUpdates.request(app)
    }
    fun refreshHistory() {
        if(historyLoading.value) return
        historyLoading.value=true;historyError.value=null;persistCurrent()
        viewModelScope.launch {
            try { lastSave?.join();history.value=withContext(Dispatchers.IO) { historyStore.list() } }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { historyError.value="Could not load conversations" }
            finally { historyLoading.value=false }
        }
    }
    fun openConversation(id:String)=run("Opening conversation…") {
        persistCurrent();lastSave?.join();restore(withContext(Dispatchers.IO) { historyStore.load(id) })
    }
    fun deleteConversation(id:String)=run("Deleting conversation…") {
        lastSave?.join()
        withContext(Dispatchers.IO) { historyStore.delete(id) }
        if(id==state.value.sessionId) resetConversation()
        history.value=withContext(Dispatchers.IO) { historyStore.list() }
    }
    private fun accountTask(block:suspend ()->Unit) {
        if(accountJob?.isActive==true) return
        accountJob=viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { chatGpt.failure(error) }
        }
    }
    fun edit(change:(AiState)->AiState) { if(!state.value.busy) state.update(change) }
    fun configure(provider:AiProvider,base:String=state.value.base,model:String=state.value.model) {
        if(state.value.busy || chatGpt.state.value.busy) return
        val previous=state.value
        val changed=provider!=previous.provider || (provider==AiProvider.REMOTE && base!=previous.base)
        if(changed) { persistCurrent();resetConversation() }
        edit { it.copy(provider=provider,base=base.take(500),model=model.take(200),error=null,
            key=if(base!=previous.base) "" else it.key) }
        settings.edit().putString("provider",provider.name).putBoolean("api",provider==AiProvider.REMOTE)
            .putString("base",base.take(500)).putString("model",model.take(200)).apply()
        if(changed) org.eightbit.bite.widgets.WidgetUpdates.request(app)
    }
    fun connectChatGpt(profileId:String?=null,consent:Boolean=false) {
        if(!state.value.busy && !chatGpt.state.value.busy) chatGpt.startSignIn(profileId,consent)
    }
    fun cancelChatGpt() { chatGpt.cancelSignIn() }
    fun selectChatGptAccount(id:String) {
        if(state.value.busy || chatGpt.state.value.busy) return
        if(id!=chatGpt.state.value.activeId) newChat()
        accountTask { chatGpt.select(id) }
    }
    fun selectChatGptModel(slug:String) { if(!state.value.busy) accountTask { chatGpt.selectModel(slug) } }
    fun reloadChatGptModels() { if(!state.value.busy && !chatGpt.state.value.busy) accountTask { chatGpt.reloadModels() } }
    fun disconnectChatGpt() { if(!state.value.busy && !chatGpt.state.value.busy) { newChat();accountTask { chatGpt.signOut() } } }
    private fun run(status:String,block:suspend ()->Unit) {
        if(state.value.busy) return
        state.update { it.copy(busy=true,status=status,error=null,errorCode=null,saved=false) }
        job=viewModelScope.launch {
            try { block() }
            catch(timeout:TimeoutCancellationException) {
                if(!currentCoroutineContext().isActive) throw timeout
                state.update { it.copy(error=if(app.resources.configuration.locales[0].language=="tr") "Yanıt süresi doldu. Yeniden deneyebilirsin." else "The reply timed out. You can retry.") }
            }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Throwable) {
                if(error is VirtualMachineError && error !is OutOfMemoryError) throw error
                state.update { it.copy(errorCode=(error as? SiwcException)?.code,error=when(error) {
                    is SiwcException->chatGptError(app,error.code)
                    is OutOfMemoryError->"Not enough memory. Try a smaller model or a remote connection."
                    is LinkageError->"On-device AI is unavailable on this device."
                    else->error.message ?: "Could not complete this request"
                }) }
            } finally { state.update { it.copy(busy=false,status="") };persistCurrent() }
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
        val user=if(retry) input.messages.lastOrNull { it.user } ?: return
            else AiChatMessage(user=true,text=input.text.trim(),photo=input.jpeg)
        run(if(app.resources.configuration.locales[0].language=="tr") "Yanıt hazırlanıyor…" else "Preparing reply…") {
            require(user.text.length<=4000 && (user.text.isNotBlank() || user.photo!=null || user.photoPath!=null)) { "Describe a meal or select a photo" }
            require(input.messages.size<500) { "This conversation is full. Start a new chat; this one remains in History." }
            require(input.provider!=AiProvider.CHATGPT || input.accountId.isBlank() || input.accountId==chatGpt.state.value.activeId) {
                "Reconnect the original ChatGPT account in Settings to continue this conversation"
            }
            val latestPhoto=withContext(Dispatchers.IO) { user.photo ?: user.photoPath?.let { File(it).readBytes() } }
            if(input.provider==AiProvider.LOCAL) require(latestPhoto==null || models.active()?.vision!=false) { "Choose a photo-capable model in Settings." }
            val conversation=if(retry) input.messages else input.messages+user
            val turns=withContext(Dispatchers.IO) { conversation.takeLast(16).map { message ->
                AiChatTurn(if(message.user) "user" else "assistant",message.wireText,
                    message.photo ?: message.photoPath?.takeIf { input.provider!=AiProvider.LOCAL }?.let { File(it).readBytes() })
            } }
            sessionModel=when(input.provider) {
                AiProvider.CHATGPT->chatGpt.state.value.model
                AiProvider.REMOTE->input.model
                AiProvider.LOCAL->models.active()?.name.orEmpty()
            }
            state.update { it.copy(messages=conversation,text="",jpeg=null,draft=emptyList(),draftMessageId="",
                accountId=if(input.provider==AiProvider.CHATGPT) chatGpt.state.value.activeId else "") }
            persistCurrent()
            val output=when(input.provider) {
                AiProvider.REMOTE->apiMeal(input.base,input.key,input.model,user.text,latestPhoto,turns)
                AiProvider.CHATGPT->withTimeout(180000) { chatGpt.chat(turns) }
                AiProvider.LOCAL->models.inferenceGate.withLock {
                    val model=models.active() ?: error("Download or import a local model first")
                    localMeal(app,model.file,AiChat.localPrompt(turns),latestPhoto,chat=true,onStage={ stage ->
                        val tr=app.resources.configuration.locales[0].language=="tr"
                        val message=when(stage) {
                            "loading"->if(tr) "Model yükleniyor…" else "Loading model…"
                            "preparing"->if(tr) "Model hazırlanıyor…" else "Preparing model…"
                            "generating"->if(tr) "Öğün çözümleniyor…" else "Recognizing meal…"
                            "closing"->if(tr) "Sonuç hazırlanıyor…" else "Preparing result…"
                            else->if(tr) "Model başlatılıyor…" else "Starting model…"
                        }
                        state.update { it.copy(status=message) }
                    })
                }
            }
            val reply=AiChat.parse(output,photo=latestPhoto!=null)
            val messageId=UUID.randomUUID().toString()
            val drafts=reply.foods.mapIndexed { index,mention -> AiReview(id=messageId+":"+index,mention=mention) }
            val message=AiChatMessage(id=messageId,user=false,text=reply.reply.ifBlank {
                if(app.resources.configuration.locales[0].language=="tr") "Tahmini öğünü kaydetmeden önce kontrol edebilirsin." else "Review the estimated meal before saving."
            },wireText=output,foods=reply.foods)
            state.update { it.copy(messages=it.messages+message,draft=drafts,draftMessageId=message.id) }
        }
    }
    fun review(id:String,change:(AiReview)->AiReview) {
        if(!state.value.busy) state.update { it.copy(draft=it.draft.map { row -> if(row.id==id) change(row) else row }) }
    }
    fun drop(id:String) { if(!state.value.busy) state.update { it.copy(draft=it.draft.filterNot { row -> row.id==id }) } }
    fun clear() { if(!state.value.busy) state.update { it.copy(draft=emptyList(),draftMessageId="",error=null) } }
    private fun resetConversation() {
        sessionModel="";historyPrefs.edit().putString("active","").apply()
        state.update { it.copy(text="",jpeg=null,draft=emptyList(),error=null,errorCode=null,saved=false,messages=emptyList(),
            draftMessageId="",savedMessages=emptySet(),sessionId=UUID.randomUUID().toString(),createdAt=System.currentTimeMillis(),accountId="") }
    }
    fun newChat() { if(!state.value.busy) { persistCurrent();resetConversation() } }
    fun save(date:LocalDate,meal:Int)=run("Saving meal…") {
        val rows=state.value.draft
        require(rows.isNotEmpty() && rows.all { it.weight()!=null }) { "Confirm a positive weight for every item" }
        val now=date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault())
        app.database.withTransaction {
            for(row in rows) writeAiEstimate(app.database.dao(),"ai:"+row.id,row.mention,row.weight()!!,meal,now,row.isSupplement,row.supplementKind)
        }
        selectMeal(meal)
        if(app.preferences.read().autoSync) SyncScheduler.now(app)
        val reply=if(app.resources.configuration.locales[0].language=="tr") "Öğün günlüğüne eklendi." else "Meal added to your diary."
        val message=AiChatMessage(user=false,text=reply,wireText=org.json.JSONObject().put("reply",reply).put("saved",true)
            .put("foods",org.json.JSONArray().apply { rows.forEach { row ->
                put(org.json.JSONObject(AiChat.foodJson(row.mention.copy(grams=row.weight())).toString()))
            } }).toString())
        state.update { it.copy(draft=emptyList(),text="",jpeg=null,saved=true,savedMessages=it.savedMessages+it.draftMessageId,
            draftMessageId="",messages=it.messages+message) }
    }
}
