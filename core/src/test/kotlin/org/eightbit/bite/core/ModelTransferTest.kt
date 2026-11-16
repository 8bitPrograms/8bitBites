package org.eightbit.bite.core

import java.io.*
import java.net.*
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class ModelTransferTest {
    private val bytes = ByteArray(4096) { (it * 31).toByte() }
    private fun spec(hash: String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }) =
        ModelSpec("test-model", "Test", "owner/repo", "a".repeat(40), "model.litertlm", bytes.size.toLong(), hash, false, false, "test", 1)
    private class Response(uri: URI, private val code: Int, private val body: ByteArray = byteArrayOf(),
        private val range: String? = null, private val location: String? = null, private val failAfter: Int? = null) : HttpURLConnection(uri.toURL()) {
        var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = when(name) { "Content-Range" -> range; "Location" -> location; else -> null }
        override fun getInputStream(): InputStream = object : InputStream() {
            var position = 0
            override fun read(): Int {
                if (failAfter != null && position >= failAfter) throw IOException("Disconnected")
                return if (position == body.size) -1 else body[position++].toInt() and 255
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (failAfter != null && position >= failAfter) throw IOException("Disconnected")
                if (position == body.size) return -1
                val count = minOf(length, body.size - position, (failAfter ?: body.size) - position)
                body.copyInto(buffer, offset, position, position + count); position += count
                return count
            }
        }
    }
    private fun temp(block: (File) -> Unit) {
        val root = Files.createTempDirectory("model-transfer").toFile()
        try { block(File(root, "model.part")) } finally { root.deleteRecursively() }
    }
    @Test fun completedDownloadIsHashedAndConnectionsClose() = temp { part ->
        val connections = mutableListOf<Response>()
        var verifying = false
        ModelTransfer({ Response(it, 200, bytes).also(connections::add) }).download(spec(), part, "", progress = { _, value -> verifying = value })
        assertContentEquals(bytes, part.readBytes()); assertTrue(verifying); assertTrue(connections.single().closed)
    }
    @Test fun droppedConnectionKeepsBytesAndNextRunResumesExactly() = temp { part ->
        assertFailsWith<IOException> { ModelTransfer({ Response(it, 200, bytes, failAfter = 777) }).download(spec(), part, "") }
        assertEquals(777L, part.length())
        lateinit var resumed: Response
        ModelTransfer({ Response(it, 206, bytes.copyOfRange(777, bytes.size), "bytes 777-4095/4096").also { resumed = it } }).download(spec(), part, "")
        assertEquals("bytes=777-", resumed.getRequestProperty("Range"))
        assertContentEquals(bytes, part.readBytes())
    }
    @Test fun validServerChunksContinueWithoutWaitingForAnAndroidRetry() = temp { part ->
        var requests = 0
        ModelTransfer({ uri -> requests++; if (requests == 1) Response(uri, 206, bytes.copyOfRange(0, 2048), "bytes 0-2047/4096")
            else Response(uri, 206, bytes.copyOfRange(2048, 4096), "bytes 2048-4095/4096") }).download(spec(), part, "")
        assertEquals(2, requests); assertContentEquals(bytes, part.readBytes())
    }
    @Test fun serverIgnoringRangeRestartsInsteadOfAppending() = temp { part ->
        part.writeBytes(bytes.copyOfRange(0, 777))
        ModelTransfer({ Response(it, 200, bytes) }).download(spec(), part, "")
        assertContentEquals(bytes, part.readBytes())
    }
    @Test fun tokenStaysOnHuggingFaceAndResumeHeaderReachesCdn() = temp { part ->
        part.writeBytes(bytes.copyOfRange(0, 777))
        val calls = mutableListOf<Response>()
        ModelTransfer({ uri -> if (uri.host == "huggingface.co") Response(uri, 302, location = "https://us.aws.cdn.hf.co/model")
            .also(calls::add) else Response(uri, 206, bytes.copyOfRange(777, 4096), "bytes 777-4095/4096").also(calls::add)
        }).download(spec(), part, "private-token")
        assertEquals("Bearer private-token", calls[0].getRequestProperty("Authorization"))
        assertNull(calls[1].getRequestProperty("Authorization"))
        assertEquals("bytes=777-", calls[1].getRequestProperty("Range"))
        assertTrue(calls.all { it.closed }); assertContentEquals(bytes, part.readBytes())
    }
    @Test fun invalidRangeAndWrongHashNeverBecomeAnInstalledModel() = temp { part ->
        assertFailsWith<ModelTransferException> {
            ModelTransfer({ Response(it, 206, bytes, "bytes 1-4095/4096") }).download(spec(), part, "")
        }
        assertFalse(part.exists())
        assertFailsWith<ModelTransferException> { ModelTransfer({ Response(it, 200, bytes) }).download(spec("0".repeat(64)), part, "") }
        assertFalse(part.exists())
    }
    @Test fun gatedAccessIsActionableAndCdnFailuresCanBeRetried() = temp { part ->
        val denied = assertFailsWith<ModelTransferException> { ModelTransfer({ Response(it, 401) }).download(spec(), part, "") }
        assertFalse(denied.retryable); assertContains(denied.message.orEmpty(), "read token")
        val expired = assertFailsWith<ModelTransferException> {
            ModelTransfer({ uri -> if (uri.host == "huggingface.co") Response(uri, 302, location = "https://us.aws.cdn.hf.co/model")
                else Response(uri, 403) }).download(spec(), part, "")
        }
        assertTrue(expired.retryable)
    }
}
