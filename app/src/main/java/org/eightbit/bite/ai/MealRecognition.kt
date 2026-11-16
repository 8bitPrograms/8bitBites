// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import kotlinx.coroutines.*
import org.eightbit.bite.core.AiMeals
import java.io.ByteArrayOutputStream
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
