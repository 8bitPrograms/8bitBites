package org.eightbit.bite.ai

import android.app.Application
import kotlinx.coroutines.*
import org.eightbit.bite.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ChatGptCallbackTest {
    private fun server()=ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));soTimeout=100 }
    private fun request(port:Int,target:String):String=Socket("127.0.0.1",port).use { socket ->
        socket.soTimeout=2000
        socket.getOutputStream().write(("GET $target HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n\r\n").toByteArray())
        socket.getInputStream().bufferedReader().readText()
    }
    @Test fun realLoopbackRejectsAnUnrelatedStateThenAcceptsTheOriginalAttemptWithoutEchoingItsCode()=runBlocking {
        val connection=ChatGptConnection(RuntimeEnvironment.getApplication())
        server().use { server ->
            val attempt=SiwcAttempt("http://127.0.0.1:${server.localPort}/auth/callback",null,"original")
            val incoming=async(Dispatchers.IO) { connection.receive(server,attempt) }
            val rejected=withContext(Dispatchers.IO) { request(server.localPort,"/auth/callback?code=bad&state=unrelated&client_id=oaiapp_test") }
            assertTrue(rejected.startsWith("HTTP/1.1 400"));assertFalse(incoming.isCompleted)
            val accepted=withContext(Dispatchers.IO) { request(server.localPort,"/auth/callback?code=private_code&state=original&client_id=oaiapp_test") }
            assertTrue(accepted.startsWith("HTTP/1.1 200"));assertTrue(accepted.contains("Cache-Control: no-store"))
            assertFalse(accepted.contains("private_code"));assertFalse(accepted.contains("state=original"))
            assertTrue(accepted.contains("width=device-width, initial-scale=1"))
            assertTrue(accepted.contains("eightbitbites://settings/ai"))
            java.io.File("build/reports/ui/chatgpt-return.html").also { it.parentFile!!.mkdirs() }.writeText(accepted.substringAfter("\r\n\r\n"))
            assertEquals("private_code" to "oaiapp_test",withTimeout(2000) { incoming.await() })
        }
    }
    @Test fun deniedPlanPermissionEndsTheAttemptWithoutAnAuthorizationCode()=runBlocking {
        val connection=ChatGptConnection(RuntimeEnvironment.getApplication())
        server().use { server ->
            val attempt=SiwcAttempt("http://127.0.0.1:${server.localPort}/auth/callback",null,"original")
            val incoming=async(Dispatchers.IO) { runCatching { connection.receive(server,attempt) } }
            val rejected=withContext(Dispatchers.IO) { request(server.localPort,"/auth/callback?error=access_denied&state=original") }
            assertTrue(rejected.startsWith("HTTP/1.1 400"))
            assertEquals("access_denied",(withTimeout(2000) { incoming.await() }.exceptionOrNull() as SiwcException).code)
        }
    }
    @Test fun anIdleBrowserPreconnectionCannotAbortTheFollowingValidCallback()=runBlocking {
        val connection=ChatGptConnection(RuntimeEnvironment.getApplication())
        server().use { server ->
            val attempt=SiwcAttempt("http://127.0.0.1:${server.localPort}/auth/callback",null,"original")
            val incoming=async(Dispatchers.IO) { connection.receive(server,attempt,headerTimeoutMs=50) }
            Socket("127.0.0.1",server.localPort).use {
                delay(150)
                assertFalse(incoming.isCompleted)
                val accepted=withContext(Dispatchers.IO) { request(server.localPort,"/auth/callback?code=valid&state=original&client_id=oaiapp_test") }
                assertTrue(accepted.startsWith("HTTP/1.1 200"))
                assertEquals("valid" to "oaiapp_test",withTimeout(2000) { incoming.await() })
            }
        }
    }
}
