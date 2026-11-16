// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import kotlinx.coroutines.*
import org.eightbit.bite.core.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

internal class ChatGptTransport {
    private fun connection(url:String):HttpURLConnection {
        val uri=URI(url)
        require(uri.scheme=="https" && uri.host in setOf("auth.openai.com","api.openai.com") &&
            uri.port in setOf(-1,443) && uri.rawUserInfo==null && uri.rawFragment==null)
        return (uri.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects=false;connectTimeout=20000;readTimeout=90000;useCaches=false
        }
    }
    suspend fun json(url:String,form:Map<String,String>?=null,token:String=""):JSONObject=coroutineScope {
        val connection=connection(url)
        val request=async(Dispatchers.IO) { try {
            if(token.isNotBlank()) connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("Accept","application/json")
            if(form!=null) {
                connection.requestMethod="POST";connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
                val bytes=Siwc.form(form).toByteArray(Charsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code=connection.responseCode
            val body=(if(code in 200..299) connection.inputStream else connection.errorStream)?.use { input ->
                val result=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0) break
                    require(result.size()+count<=1024*1024);result.write(buffer,0,count) }
                result.toString("UTF-8")
            }.orEmpty()
            if(code !in 200..299) {
                val error=runCatching { JSONObject(body).let { obj -> obj.optJSONObject("error")?.optString("code") ?: obj.optString("error") } }.getOrDefault("")
                throw SiwcException(error.takeIf { Regex("[a-zA-Z0-9_]{1,100}").matches(it) } ?: "http_$code")
            }
            if(body.isBlank()) JSONObject() else JSONObject(body)
        } finally { connection.disconnect() } }
        try { request.await() } finally { connection.disconnect() }
    }
    suspend fun response(token:String,model:String,turns:List<AiChatTurn>):String=coroutineScope {
        val connection=connection(Siwc.RESOURCE+"/responses")
        val request=async(Dispatchers.IO) { try {
            connection.requestMethod="POST";connection.doOutput=true
            connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Accept","text/event-stream")
            val bytes=AiChat.responsesRequest(model,turns).toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size);connection.outputStream.use { it.write(bytes) }
            val code=connection.responseCode
            if(code !in 200..299) {
                val body=connection.errorStream?.use { input -> val result=ByteArrayOutputStream();val buffer=ByteArray(4096)
                    while(result.size()<65536) { val count=input.read(buffer,0,minOf(buffer.size,65536-result.size()));if(count<0) break;result.write(buffer,0,count) }
                    result.toString("UTF-8") }.orEmpty()
                val error=runCatching { JSONObject(body).optJSONObject("error")?.optString("code") }.getOrNull()
                throw SiwcException(error?.takeIf { Regex("[a-zA-Z0-9_]{1,100}").matches(it) } ?: "http_$code")
            }
            val stream=ChatGptStream();var received=0
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val event=StringBuilder();val line=StringBuilder()
                fun dispatch() { if(event.isNotEmpty()) { stream.event(event.toString().trimEnd('\n'));event.setLength(0) } }
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val character=reader.read()
                    if(character<0) { if(line.isNotEmpty() && line.startsWith("data:")) event.append(line.substring(5).trimStart()).append('\n');dispatch();break }
                    received++;require(received<=8*1024*1024) { "AI response is too long" }
                    if(character==10) {
                        val value=line.toString().trimEnd('\r');line.setLength(0)
                        if(value.isEmpty()) { dispatch();if(stream.completed) break }
                        else if(value.startsWith("data:")) { event.append(value.substring(5).trimStart()).append('\n');require(event.length<=512*1024) }
                    } else { line.append(character.toChar());require(line.length<=512*1024) }
                }
            }
            stream.result()
        } finally { connection.disconnect() } }
        try { request.await() } finally { connection.disconnect() }
    }
}
