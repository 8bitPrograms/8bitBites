// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Message
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import org.eightbit.bite.core.AiMeals
import org.eightbit.bite.core.AiChat
import java.io.File

/** Native code lives only in :local_ai. This service is private and only bound for one request. */
class LocalAiService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private var activeId:String?=null
    private val messenger=Messenger(Handler(Looper.getMainLooper()) { message ->
        when(message.what) {
            LocalAiProtocol.RUN -> startRequest(message)
            LocalAiProtocol.STOP -> if(message.data.getString("id")==activeId) stopWorker()
        }
        true
    })
    override fun onBind(intent:Intent):IBinder=messenger.binder
    override fun onUnbind(intent:Intent):Boolean { stopWorker();return false }
    override fun onDestroy() { stopWorker();super.onDestroy() }
    private fun stopWorker() {
        // Cancellation cannot unwind JNI safely. Ending this dedicated process releases the model
        // without racing native callbacks or affecting the application's diary process.
        if(Application.getProcessName()==packageName+":local_ai") android.os.Process.killProcess(android.os.Process.myPid())
        scope.cancel()
    }
    private fun startRequest(message:Message) {
        val reply=message.replyTo ?: return
        val id=message.data.getString("id") ?: return
        fun send(what:Int,body:Bundle.()->Unit) {
            try { reply.send(Message.obtain(null,what).apply { data=Bundle().apply { putString("id",id);body() } }) }
            catch(_:RemoteException) { stopWorker() }
        }
        if(activeId!=null) { send(LocalAiProtocol.ERROR) { putString("failure",LocalAiFailure.START.name) };return }
        activeId=id
        val request=Bundle(message.data)
        scope.launch {
            try {
                val models=File(filesDir,"models").canonicalFile
                val model=File(request.getString("model").orEmpty()).canonicalFile
                require(model.parentFile==models && model.extension=="litertlm" && model.isFile)
                val text=request.getString("text").orEmpty()
                val chat=request.getBoolean("chat")
                require(text.length<=(if(chat) 8000 else 4000))
                val photoPath=request.getString("photo")
                val jpeg=photoPath?.let { path ->
                    val file=File(path).canonicalFile
                    require(file.parentFile==File(cacheDir,"local-ai-input").canonicalFile && file.isFile && file.length()<=LocalAiProtocol.MAX_PHOTO_BYTES)
                    file.readBytes()
                }
                val output=runModel(model,text,jpeg,chat) { stage -> send(LocalAiProtocol.STAGE) { putString("stage",stage) } }
                send(LocalAiProtocol.RESULT) { putString("output",output) }
            } catch(error:Throwable) {
                if(error is VirtualMachineError && error !is OutOfMemoryError) throw error
                val failure=when {
                    error is OutOfMemoryError || error.message.orEmpty().contains("bad_alloc",true) -> LocalAiFailure.MEMORY
                    error.message.orEmpty().contains("template",true) -> LocalAiFailure.TEMPLATE
                    error is LinkageError -> LocalAiFailure.START
                    else -> LocalAiFailure.RUNTIME
                }
                send(LocalAiProtocol.ERROR) { putString("failure",failure.name);putString("errorClass",error.javaClass.simpleName) }
            }
        }
    }
    private fun runModel(model:File,text:String,jpeg:ByteArray?,chat:Boolean,stage:(String)->Unit):String {
        stage("loading")
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        val engine=Engine(EngineConfig(modelPath=model.absolutePath,backend=Backend.CPU(),
            visionBackend=if(jpeg!=null) Backend.CPU() else null,maxNumTokens=4096,
            cacheDir=File(cacheDir,"litert-lm").apply { mkdirs() }.absolutePath))
        try {
            engine.initialize()
            stage("preparing")
            engine.createConversation(ConversationConfig(systemInstruction=Contents.of(if(chat) AiChat.instruction else AiMeals.instruction),
                automaticToolCalling=false,maxOutputToken=1024)).use { conversation ->
                val contents=Contents.of(buildList {
                    add(Content.Text(text.ifBlank { "Identify this meal." }))
                    if(jpeg!=null) add(Content.ImageBytes(jpeg))
                })
                stage("generating")
                // A single synchronous JNI call stays on this worker thread. Its Kotlin errors are
                // caught here, and the client can terminate the worker on timeout or cancellation.
                val output=conversation.sendMessage(contents).toString()
                require(output.length<=32000) { "AI response is too long" }
                return output
            }
        } finally {
            stage("closing")
            if(engine.isInitialized()) engine.close()
        }
    }
}
