// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.eightbit.bite.core.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

enum class DownloadOutcome { COMPLETE, RETRY, STOPPED }
private class PermanentDownloadError(message:String):IOException(message)

class ModelDownloadEngine(private val context:Context,private val library:ModelLibrary) {
    suspend fun run(id:String,generation:String,progress:(Long,Boolean)->Unit):DownloadOutcome=withContext(Dispatchers.IO) {
        library.lock(id).withLock {
            suspend fun wanted() { currentCoroutineContext().ensureActive();if(!library.desired(id,generation)) throw CancellationException("Download paused") }
            fun update(phase:String,done:Long) { library.status(id) { if(it.generation==generation && it.wanted) it.copy(phase=phase,downloaded=done,error="") else it } }
            val model=library.spec(id);val part=library.partial(id)
            try {
                wanted();part.parentFile!!.mkdirs()
                if(part.length()>model.bytes) check(part.delete())
                var offset=part.length()
                require(android.os.StatFs(part.parentFile!!.path).availableBytes>=model.bytes-offset+64L*1024*1024) { "Not enough storage for this model" }
                if(offset<model.bytes) {
                    val connection=open(model.url,library.token(id),offset)
                    try {
                        wanted()
                        val code=connection.responseCode
                        if(code==401 || code==403) throw PermanentDownloadError("Hugging Face access denied. Open the model page, accept its conditions, and enter a read token with access.")
                        if(code in 400..499 && code !in setOf(408,416,429)) throw PermanentDownloadError("The model server returned HTTP $code")
                        if(code>=500 || code==408 || code==429) throw IOException("Model server is temporarily unavailable")
                        val action=try { ModelDownloadPolicy.response(code,connection.getHeaderField("Content-Range"),offset,model.bytes) }
                            catch(_:Exception) { throw PermanentDownloadError("The model server returned an invalid file range") }
                        if(action==ResumeResponse.RESTART) offset=0
                        if(action!=ResumeResponse.VERIFY) {
                            update("downloading",offset);progress(offset,false)
                            connection.inputStream.use { input -> FileOutputStream(part,action==ResumeResponse.APPEND).use { output ->
                                val buffer=ByteArray(128*1024);var checkpoint=android.os.SystemClock.elapsedRealtime()
                                while(true) {
                                    wanted()
                                    val count=input.read(buffer);if(count<0) break
                                    wanted()
                                    if(offset+count>model.bytes) throw PermanentDownloadError("The downloaded model exceeds its expected size")
                                    output.write(buffer,0,count);offset+=count
                                    val now=android.os.SystemClock.elapsedRealtime()
                                    if(now-checkpoint>=1000) {
                                        if(android.os.StatFs(part.parentFile!!.path).availableBytes<32L*1024*1024) throw PermanentDownloadError("Not enough storage to continue the download")
                                        update("downloading",offset);progress(offset,false);checkpoint=now
                                    }
                                }
                                output.fd.sync()
                            } }
                            if(offset!=model.bytes) throw EOFException("Download interrupted")
                        }
                    } finally { connection.disconnect() }
                }
                wanted();update("verifying",part.length());progress(part.length(),true)
                val digest=MessageDigest.getInstance("SHA-256")
                part.inputStream().use { input -> val buffer=ByteArray(1024*1024)
                    while(true) { wanted();val count=input.read(buffer);if(count<0) break;digest.update(buffer,0,count) }
                }
                val hash=digest.digest().joinToString("") { "%02x".format(it) }
                if(part.length()!=model.bytes || hash!=model.sha256) { part.delete();throw PermanentDownloadError("Model verification failed. Download it again.") }
                library.inferenceGate.withLock {
                    wanted()
                    check(part.renameTo(library.file(id))) { "Could not install the model" }
                    library.status(id) { it.copy(phase="ready",downloaded=model.bytes,wanted=false,error="") }
                    library.forgetToken(id)
                }
                if(library.state.value.selected.isBlank()) library.select(id)
                DownloadOutcome.COMPLETE
            } catch(cancelled:CancellationException) {
                library.status(id) { if(it.generation==generation && it.wanted) it.copy(phase="waiting",downloaded=part.length()) else it }
                throw cancelled
            } catch(error:Exception) {
                val retry=error is IOException && error !is PermanentDownloadError
                library.status(id) { if(it.generation==generation && it.wanted) it.copy(phase=if(retry) "waiting" else "failed",
                    downloaded=part.length(),wanted=retry,error=if(retry) "Connection interrupted. The partial download is kept." else error.message ?: "Download failed") else it }
                if(retry) DownloadOutcome.RETRY else DownloadOutcome.STOPPED
            }
        }
    }
    private fun open(url:String,token:String,offset:Long):HttpURLConnection {
        var uri=URI(url)
        repeat(8) {
            require(ModelDownloadPolicy.allowedUrl(uri)) { "Unexpected model download host" }
            val connection=uri.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false;connection.connectTimeout=20000;connection.readTimeout=30000
            connection.setRequestProperty("Accept-Encoding","identity")
            if(offset>0) connection.setRequestProperty("Range","bytes=$offset-")
            // Never forward the user's token to a CDN or another host.
            if(token.isNotBlank() && ModelDownloadPolicy.sendToken(uri)) connection.setRequestProperty("Authorization","Bearer $token")
            try {
                if(connection.responseCode !in setOf(301,302,303,307,308)) return connection
                val location=connection.getHeaderField("Location") ?: throw PermanentDownloadError("Missing model redirect")
                uri=uri.resolve(location)
            } catch(error:Exception) { connection.disconnect();throw error }
            connection.disconnect()
        }
        throw PermanentDownloadError("Too many model download redirects")
    }
}
