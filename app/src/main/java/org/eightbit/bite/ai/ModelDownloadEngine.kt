// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import android.net.Network
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.eightbit.bite.core.*
import java.io.IOException
import java.net.HttpURLConnection

enum class DownloadOutcome { COMPLETE, RETRY, STOPPED }

class ModelDownloadEngine(private val context: Context, private val library: ModelLibrary, private val network: Network? = null) {
    suspend fun run(id: String, generation: String, progress: (Long, Boolean) -> Unit): DownloadOutcome = withContext(Dispatchers.IO) {
        library.lock(id).withLock {
            val transferContext = currentCoroutineContext()
            fun wanted() {
                transferContext.ensureActive()
                if (!library.desired(id, generation)) throw CancellationException("Download paused")
            }
            val model = library.spec(id)
            val part = library.partial(id)
            val startingBytes = part.length()
            try {
                wanted()
                part.parentFile!!.mkdirs()
                require(android.os.StatFs(part.parentFile!!.path).availableBytes >=
                    (model.bytes - part.length()).coerceAtLeast(0) + 64L * 1024 * 1024) { "Not enough storage for this model" }
                library.status(id) { if (it.generation == generation && it.wanted) it.copy(phase = "connecting", error = "") else it }
                val transfer = ModelTransfer(connect = { uri ->
                    (network?.openConnection(uri.toURL()) ?: uri.toURL().openConnection()) as HttpURLConnection
                }, activeConnection = { library.connection(id, it) })
                transfer.download(model, part, library.token(id), ::wanted) { done, verifying ->
                    wanted()
                    if (!verifying && android.os.StatFs(part.parentFile!!.path).availableBytes < 32L * 1024 * 1024)
                        throw ModelTransferException("Not enough storage to continue the download")
                    library.status(id) { if (it.generation == generation && it.wanted)
                        it.copy(phase = if (verifying) "verifying" else "downloading", downloaded = done, error = "",
                            attempts = if (done > startingBytes) 0 else it.attempts) else it }
                    progress(done, verifying)
                }
                library.inferenceGate.withLock {
                    wanted()
                    check(part.renameTo(library.file(id))) { "Could not install the model" }
                    library.status(id) { it.copy(phase = "ready", downloaded = model.bytes, wanted = false, error = "", attempts = 0) }
                    library.forgetToken(id)
                }
                if (library.state.value.selected.isBlank()) library.select(id)
                DownloadOutcome.COMPLETE
            } catch (cancelled: CancellationException) {
                library.status(id) { if (it.generation == generation && it.wanted) it.copy(phase = "waiting", downloaded = part.length()) else it }
                throw cancelled
            } catch (error: Exception) {
                if (!library.desired(id, generation)) return@withLock DownloadOutcome.STOPPED
                val attempts = library.current(id).attempts + 1
                val transient = error is IOException && (error !is ModelTransferException || error.retryable)
                val retry = transient && attempts < 5
                library.status(id) { if (it.generation == generation && it.wanted)
                    it.copy(phase = if (retry) "waiting" else "failed", downloaded = part.length(), wanted = retry,
                        attempts = attempts, error = if (transient) "Connection interrupted. Partial file kept. Retry " + attempts +
                            "/5. " + (error.message ?: "Check your connection.") else error.message ?: "Download failed") else it }
                if (retry) DownloadOutcome.RETRY else DownloadOutcome.STOPPED
            } finally { library.connection(id, null) }
        }
    }
}
