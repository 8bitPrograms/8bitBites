// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.core

import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class ModelTransferException(message: String, val retryable: Boolean = false) : IOException(message)

/** Streams pinned bytes, validates every range and hashes the complete file before installation.
 * The connection factory also lets Android use the network assigned to its transfer job.
 */
class ModelTransfer(
    private val connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
    private val activeConnection: (HttpURLConnection?) -> Unit = {},
) {
    private fun open(url: String, token: String, offset: Long, check: () -> Unit): HttpURLConnection {
        var uri = URI(url)
        repeat(8) {
            check()
            if (!ModelDownloadPolicy.allowedUrl(uri)) throw ModelTransferException("Unexpected model download host")
            val connection = connect(uri)
            activeConnection(connection)
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-")
            if (token.isNotBlank() && ModelDownloadPolicy.sendToken(uri))
                connection.setRequestProperty("Authorization", "Bearer " + token)
            try {
                if (connection.responseCode !in setOf(301, 302, 303, 307, 308)) return connection
                val location = connection.getHeaderField("Location") ?: throw ModelTransferException("Missing model redirect")
                uri = uri.resolve(location)
            } catch (error: Exception) {
                connection.disconnect(); activeConnection(null); throw error
            }
            connection.disconnect(); activeConnection(null)
        }
        throw ModelTransferException("Too many model download redirects")
    }

    fun download(spec: ModelSpec, part: File, token: String, check: () -> Unit = {},
        progress: (Long, Boolean) -> Unit = { _, _ -> }) {
        part.parentFile?.mkdirs()
        if (part.length() > spec.bytes && !part.delete()) throw ModelTransferException("Could not reset the partial model")
        var offset = part.length()
        while (offset < spec.bytes) {
            check()
            val connection = open(spec.url, token, offset, check)
            try {
                val code = connection.responseCode
                if (code == 401 || code == 403) {
                    val cdn = connection.url.host != "huggingface.co"
                    throw ModelTransferException(if (cdn) "The temporary download link expired. Retrying from Hugging Face."
                        else "Hugging Face access denied. Accept the model terms and use an authorised read token.", cdn)
                }
                if (code >= 500 || code in setOf(408, 429))
                    throw ModelTransferException("The model server is temporarily unavailable (HTTP " + code + ").", true)
                val action = try { ModelDownloadPolicy.response(code, connection.getHeaderField("Content-Range"), offset, spec.bytes) }
                    catch (_: Exception) { throw ModelTransferException("The server returned an invalid model range (HTTP " + code + ").") }
                if (action == ResumeResponse.RESTART) offset = 0
                val expectedEnd = if (code == 206) connection.getHeaderField("Content-Range").substringAfter('-').substringBefore('/').toLong() + 1 else spec.bytes
                progress(offset, false)
                if (action != ResumeResponse.VERIFY) {
                    connection.inputStream.use { input -> FileOutputStream(part, action == ResumeResponse.APPEND).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var checkpoint = System.nanoTime()
                        while (true) {
                            check()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check()
                            if (offset + count > expectedEnd) throw ModelTransferException("The downloaded model exceeds the expected file range")
                            output.write(buffer, 0, count)
                            offset += count
                            if (System.nanoTime() - checkpoint >= 500_000_000L) {
                                progress(offset, false); checkpoint = System.nanoTime()
                            }
                        }
                        output.fd.sync()
                    } }
                    progress(offset, false)
                    if (offset != expectedEnd) throw EOFException("Connection interrupted. The partial model is kept.")
                }
            } finally { connection.disconnect(); activeConnection(null) }
        }
        check()
        progress(offset, true)
        val hash = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) { check(); val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        if (part.length() != spec.bytes || hash.digest().joinToString("") { "%02x".format(it) } != spec.sha256) {
            part.delete()
            throw ModelTransferException("Model verification failed. Download it again.")
        }
    }
}
