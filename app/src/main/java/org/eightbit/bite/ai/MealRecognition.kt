// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import org.eightbit.bite.core.AiMeals
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection

/** No diary, profile or background requests are sent. Credentials stay in session memory. */
suspend fun apiMeal(base: String, key: String, model: String, text: String, jpeg: ByteArray?): String = coroutineScope {
    require(key.isNotBlank() && key.length <= 4096 && key.none { it=='\r' || it=='\n' }) { "Enter your API key" }
    val connection=AiMeals.endpoint(base).toURL().openConnection() as HttpURLConnection
    val request=async(Dispatchers.IO) { try {
        connection.requestMethod="POST"
        connection.instanceFollowRedirects=false // Never forward an authorization header to another host.
        connection.connectTimeout=20000;connection.readTimeout=90000
        connection.doOutput=true
        connection.setRequestProperty("Authorization","Bearer ${key.trim()}")
        connection.setRequestProperty("Content-Type","application/json")
        val bytes=AiMeals.request(model,text,jpeg).toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        val status=connection.responseCode
        require(status in 200..299) { "AI service returned HTTP $status. Check your endpoint, key and model." }
        val body=connection.inputStream.use { input ->
            val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {
                currentCoroutineContext().ensureActive()
                val count=input.read(buffer);if(count<0) break
                require(output.size()+count<=1024*1024) { "AI response is too long" }
                output.write(buffer,0,count)
            }
            output.toString("UTF-8")
        }
        AiMeals.response(body)
    } finally { connection.disconnect() } }
    // Closing the connection unblocks IO when the user cancels or leaves the screen.
    try { request.await() } finally { connection.disconnect() }
}

suspend fun localMeal(context: Context, model: File, text: String, jpeg: ByteArray?): String = withContext(Dispatchers.Default) {
    require(model.isFile) { "Import a compatible .litertlm model first" }
    val engine=Engine(EngineConfig(modelPath=model.absolutePath,backend=Backend.CPU(),
        visionBackend=if(jpeg!=null) Backend.CPU() else null,
        maxNumTokens=4096,cacheDir=File(context.cacheDir,"litert-lm").apply { mkdirs() }.absolutePath))
    try {
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        engine.initialize()
        currentCoroutineContext().ensureActive()
        engine.createConversation(ConversationConfig(systemInstruction=Contents.of(AiMeals.instruction),maxOutputToken=1024)).use { conversation ->
            val contents=Contents.of(buildList {
                add(Content.Text(text.ifBlank { "Identify this meal." }))
                if(jpeg!=null) add(Content.ImageBytes(jpeg))
            })
            val result=StringBuilder()
            withTimeout(180000) {
                conversation.sendMessageAsync(contents).collect { message ->
                    result.append(message.toString())
                    require(result.length<=32000) { "AI response is too long" }
                }
            }
            result.toString()
        }
    } catch(error: LinkageError) {
        throw IllegalStateException("On-device AI is unavailable on this device. Use a compatible device or API mode.",error)
    } finally { if(engine.isInitialized()) engine.close() }
}
