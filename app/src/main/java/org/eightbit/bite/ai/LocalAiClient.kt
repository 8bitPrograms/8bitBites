// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.*
import android.os.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object LocalAiProtocol {
    const val RUN=1
    const val STAGE=2
    const val RESULT=3
    const val ERROR=4
    const val STOP=5
    const val MAX_PHOTO_BYTES=4*1024*1024
}

/** A dead native worker fails this request instead of terminating the diary process. */
internal suspend fun localMeal(context:Context,model:File,text:String,jpeg:ByteArray?,
    onStage:(String)->Unit={},timeoutMs:Long=180000,chat:Boolean=false):String {
    require(model.isFile) { "Import a compatible .litertlm model first" }
    require(text.length<=(if(chat) 8000 else 4000) && (text.isNotBlank() || jpeg!=null)) { "Describe a meal or select a photo" }
    require(jpeg==null || jpeg.size<=LocalAiProtocol.MAX_PHOTO_BYTES) { "Photo is too large" }
    val id=UUID.randomUUID().toString()
    val diagnostics=LocalAiDiagnostics(context)
    diagnostics.begin(id,model.nameWithoutExtension)
    var photo:File?=null
    try {
        if(jpeg!=null) {
            val inputPhoto=File(File(context.cacheDir,"local-ai-input"),"$id.jpg")
            photo=inputPhoto
            withContext(Dispatchers.IO) { inputPhoto.parentFile!!.mkdirs();inputPhoto.writeBytes(jpeg) }
        }
        return withTimeout(timeoutMs) { withContext(Dispatchers.Main.immediate) {
            var registered=false
            var connection:ServiceConnection?=null
            var worker:Messenger?=null
            try {
                suspendCancellableCoroutine { continuation ->
                    fun fail(failure:LocalAiFailure,errorClass:String="") {
                        if(continuation.isActive) {
                            diagnostics.finish(id,failure.name,errorClass)
                            continuation.resumeWithException(LocalAiException(context,failure))
                        }
                    }
                    val reply=Messenger(Handler(Looper.getMainLooper()) { message ->
                        if(message.data.getString("id")!=id || !continuation.isActive) return@Handler true
                        when(message.what) {
                            LocalAiProtocol.STAGE -> {
                                val stage=message.data.getString("stage").orEmpty()
                                diagnostics.stage(id,stage);onStage(stage)
                            }
                            LocalAiProtocol.RESULT -> {
                                val output=message.data.getString("output").orEmpty()
                                if(output.length>32000) fail(LocalAiFailure.RUNTIME)
                                else { diagnostics.finish(id,"complete");continuation.resume(output) }
                            }
                            LocalAiProtocol.ERROR -> fail(LocalAiFailure.entries.firstOrNull { it.name==message.data.getString("failure") }
                                ?: LocalAiFailure.RUNTIME,message.data.getString("errorClass").orEmpty())
                        }
                        true
                    })
                    connection=object:ServiceConnection {
                        override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                            if(!continuation.isActive) return
                            worker=Messenger(binder)
                            try { worker!!.send(Message.obtain(null,LocalAiProtocol.RUN).apply {
                                replyTo=reply
                                data=Bundle().apply { putString("id",id);putString("model",model.absolutePath)
                                    putString("text",text);putString("photo",photo?.absolutePath);putBoolean("chat",chat) }
                            }) } catch(_:RemoteException) { fail(LocalAiFailure.CRASH) }
                        }
                        override fun onServiceDisconnected(name:ComponentName)=fail(LocalAiFailure.CRASH)
                        override fun onBindingDied(name:ComponentName)=fail(LocalAiFailure.CRASH)
                        override fun onNullBinding(name:ComponentName)=fail(LocalAiFailure.START)
                    }
                    try {
                        registered=context.bindService(Intent(context,LocalAiService::class.java),connection!!,Context.BIND_AUTO_CREATE)
                        if(!registered) fail(LocalAiFailure.START)
                    } catch(_:Exception) { fail(LocalAiFailure.START) }
                }
            } finally {
                // Stop the worker process on cancellation/timeout. Never free an in-flight native
                // conversation in the diary process while an asynchronous JNI callback is running.
                try { worker?.send(Message.obtain(null,LocalAiProtocol.STOP).apply { data=Bundle().apply { putString("id",id) } }) }
                catch(_:RemoteException) { }
                if(registered) connection?.let { context.unbindService(it) }
            }
        } }
    } catch(timeout:TimeoutCancellationException) {
        if(!currentCoroutineContext().isActive) { diagnostics.finish(id,"cancelled");throw timeout }
        diagnostics.finish(id,LocalAiFailure.TIMEOUT.name)
        throw LocalAiException(context,LocalAiFailure.TIMEOUT)
    } catch(cancelled:CancellationException) {
        diagnostics.finish(id,"cancelled");throw cancelled
    } catch(error:Exception) {
        if(error !is LocalAiException) diagnostics.finish(id,LocalAiFailure.START.name,error.javaClass.simpleName)
        throw error
    } finally { photo?.delete() }
}
