package org.eightbit.bite.ai

import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class LocalAiClientTest {
    private val app get()=RuntimeEnvironment.getApplication() as Context
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
    private lateinit var model:File
    @Before fun setup() {
        app.getSharedPreferences("local-ai-diagnostics",0).edit().clear().commit()
        model=File(app.filesDir,"models/qwen25-15b.litertlm").apply { parentFile!!.mkdirs();writeText("test model") }
    }
    @After fun cleanup() { scope.cancel();shadowOf(Looper.getMainLooper()).idle() }

    private class WorkerContext(base:Context,var mode:String):ContextWrapper(base) {
        var connection:ServiceConnection?=null
        var unbinds=0
        var stops=0
        var inputPhoto:String?=null
        val binder=Messenger(Handler(Looper.getMainLooper()) { message ->
            if(message.what==LocalAiProtocol.STOP) { stops++;return@Handler true }
            if(message.what!=LocalAiProtocol.RUN) return@Handler true
            inputPhoto=message.data.getString("photo")
            val id=message.data.getString("id")
            fun reply(what:Int,block:Bundle.()->Unit) {
                message.replyTo.send(Message.obtain(null,what).apply { data=Bundle().apply { putString("id",id);block() } })
            }
            reply(LocalAiProtocol.STAGE) { putString("stage","generating") }
            when(mode) {
                "result" -> reply(LocalAiProtocol.RESULT) { putString("output","{\"foods\":[]}") }
                "error" -> reply(LocalAiProtocol.ERROR) { putString("failure","TEMPLATE");putString("errorClass","LiteRtLmJniException") }
                "crash" -> connection!!.onServiceDisconnected(ComponentName(this,LocalAiService::class.java))
            }
            true
        })
        override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int):Boolean {
            this.connection=connection
            if(mode=="unavailable") return false
            connection.onServiceConnected(ComponentName(this,LocalAiService::class.java),binder.binder)
            return true
        }
        override fun unbindService(connection:ServiceConnection) { unbinds++ }
    }

    @Test fun workerResultAndStagesReachTheClientAndBindingCloses()=runBlocking {
        val context=WorkerContext(app,"result")
        val stages=mutableListOf<String>()
        val result=scope.async { localMeal(context,model,"100 g cheese",null,onStage=stages::add) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        assertEquals("{\"foods\":[]}",result.await())
        assertEquals(listOf("generating"),stages)
        assertEquals(1,context.unbinds)
        assertEquals(1,context.stops)
        assertTrue(model.isFile)
    }

    @Test fun runtimeFailureBecomesACopyableErrorWithoutRecordingTheMealText()=runBlocking {
        val context=WorkerContext(app,"error")
        val result=scope.async { localMeal(context,model,"private-meal-should-not-be-logged",null) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        val failure=runCatching { result.await() }.exceptionOrNull()
        assertEquals(LocalAiFailure.TEMPLATE,(failure as LocalAiException).failure)
        val report=LocalAiDiagnostics(context).report()
        assertTrue(report.contains("qwen25-15b"));assertTrue(report.contains("generating"))
        assertTrue(report.contains("LiteRtLmJniException"));assertFalse(report.contains("private-meal"))
        assertEquals(1,context.unbinds)
    }

    @Test fun deadWorkerFailsOnlyTheRequestAndKeepsTheInstalledModel()=runBlocking {
        val context=WorkerContext(app,"crash")
        val result=scope.async { localMeal(context,model,"100 g cheese",null) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        val failure=runCatching { result.await() }.exceptionOrNull()
        assertEquals(LocalAiFailure.CRASH,(failure as LocalAiException).failure)
        assertEquals(1,context.unbinds)
        assertEquals("test model",model.readText())
        assertTrue(LocalAiDiagnostics(context).report().contains("Result: CRASH"))
    }

    @Test fun cancellationStopsTheWorkerAndUnbindsWithoutReturningAFalseSuccess() {
        val context=WorkerContext(app,"waiting")
        val result=scope.async { localMeal(context,model,"100 g cheese",null) }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(result.isCompleted)
        result.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCancelled)
        assertEquals(1,context.unbinds);assertEquals(1,context.stops)
        assertTrue(LocalAiDiagnostics(context).report().contains("Result: cancelled"))
    }

    @Test fun failedBindingReturnsAStartErrorAndDoesNotUnbindAnUnregisteredConnection()=runBlocking {
        val context=WorkerContext(app,"unavailable")
        val result=scope.async { localMeal(context,model,"100 g cheese",null) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        val failure=runCatching { result.await() }.exceptionOrNull()
        assertEquals(LocalAiFailure.START,(failure as LocalAiException).failure)
        assertEquals(0,context.unbinds)
    }

    @Test fun timeoutStopsAWaitingWorkerAndReturnsAReadableFailure()=runBlocking {
        val context=WorkerContext(app,"waiting")
        val result=scope.async { localMeal(context,model,"100 g cheese",null,timeoutMs=100) }
        shadowOf(Looper.getMainLooper()).idle()
        // The Unconfined caller's timeout uses the real coroutine delay scheduler.
        Thread.sleep(150)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        val failure=runCatching { result.await() }.exceptionOrNull()
        assertEquals(LocalAiFailure.TIMEOUT,(failure as LocalAiException).failure)
        assertEquals(1,context.unbinds);assertEquals(1,context.stops)
        assertTrue(LocalAiDiagnostics(context).report().contains("Result: TIMEOUT"))
    }

    @Test fun photoCrossesTheBindingAsAPrivateFileAndIsRemovedAfterwards()=runBlocking {
        val context=WorkerContext(app,"result")
        val result=scope.async { localMeal(context,model,"meal",ByteArray(1024)) }
        repeat(200) { if(!result.isCompleted) { shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5) } }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.isCompleted)
        assertEquals("{\"foods\":[]}",result.await())
        val photo=File(context.inputPhoto!!)
        assertEquals(File(app.cacheDir,"local-ai-input").canonicalFile,photo.parentFile.canonicalFile)
        assertFalse(photo.exists())
        assertTrue(model.exists())
    }

    @Test fun unfinishedRequestIsRecoveredWithoutPretendingToKnowTheCrashCause() {
        val diagnostics=LocalAiDiagnostics(app)
        diagnostics.begin("unfinished","qwen25-15b")
        diagnostics.stage("unfinished","loading")
        assertEquals(LocalAiFailure.INTERRUPTED,diagnostics.recover())
        assertNull(diagnostics.recover())
        assertTrue(diagnostics.report().contains("Stage: loading"))
        assertTrue(diagnostics.report().contains("Result: INTERRUPTED"))
    }

    @Suppress("DEPRECATION")
    @Test fun nativeWorkerHasAPrivateProcessAndNoExportedEntryPoint() {
        val service=app.packageManager.getServiceInfo(ComponentName(app,LocalAiService::class.java),0)
        assertFalse(service.exported)
        assertEquals(app.packageName+":local_ai",service.processName)
    }
}
