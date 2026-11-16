// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import org.eightbit.bite.BuildConfig
import java.io.File

internal enum class LocalAiFailure { START, CRASH, MEMORY, TEMPLATE, RUNTIME, TIMEOUT, INTERRUPTED }

internal class LocalAiException(context:Context,val failure:LocalAiFailure):IllegalStateException(localAiError(context,failure))

internal fun localAiError(context:Context,failure:LocalAiFailure):String {
    val tr=context.resources.configuration.locales[0].language=="tr"
    return when(failure) {
        LocalAiFailure.START -> if(tr) "Yerel model başlatılamadı. Yeniden deneyebilirsin." else "The local model could not start. You can try again."
        LocalAiFailure.CRASH -> if(tr) "Yerel model beklenmedik biçimde durdu. Hata bilgisini kopyalayıp paylaşabilirsin." else "The local model stopped unexpectedly. You can copy and share the error details."
        LocalAiFailure.MEMORY -> if(tr) "Model için yeterli boş bellek bulunamadı. Diğer uygulamaları kapatıp tekrar dene." else "Not enough available memory for this model. Close other apps and try again."
        LocalAiFailure.TEMPLATE -> if(tr) "Modelin metin şablonu çalıştırılamadı. Hata bilgisini paylaşabilirsin." else "The model's text template could not run. You can share the error details."
        LocalAiFailure.RUNTIME -> if(tr) "Yerel model yanıt üretemedi. Hata bilgisini kopyalayıp paylaşabilirsin." else "The local model could not generate a response. You can copy and share the error details."
        LocalAiFailure.TIMEOUT -> if(tr) "Model süresi içinde yanıt vermedi. İşlem durduruldu; tekrar deneyebilirsin." else "The model did not respond in time. Processing stopped; you can try again."
        LocalAiFailure.INTERRUPTED -> if(tr) "Önceki yerel AI işlemi tamamlanamadı. Metni yeniden gönderebilirsin." else "The previous local AI request did not finish. You can send the description again."
    }
}

/** Main-process only. No prompt, photo, response, API key or native error text is stored. */
internal class LocalAiDiagnostics(private val context:Context) {
    private val prefs=context.getSharedPreferences("local-ai-diagnostics",Context.MODE_PRIVATE)
    fun begin(id:String,model:String) {
        val memory=ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        prefs.edit(commit=true) {
            clear();putString("id",id);putString("model",model);putString("stage","starting")
            putString("result","running");putString("app",BuildConfig.VERSION_NAME)
            putLong("time",System.currentTimeMillis());putLong("available",memory.availMem);putLong("total",memory.totalMem)
        }
    }
    fun stage(id:String,value:String) {
        if(prefs.getString("id","")==id && value in setOf("loading","preparing","generating","closing"))
            prefs.edit(commit=true) { putString("stage",value) }
    }
    fun finish(id:String,result:String,errorClass:String="") {
        if(prefs.getString("id","")==id) prefs.edit(commit=true) {
            putString("result",result)
            putString("errorClass",errorClass.filter { it.isLetterOrDigit() || it=='.' }.take(100))
        }
    }
    fun recover():LocalAiFailure? {
        if(prefs.getString("result","")!="running") return null
        val id=prefs.getString("id","").orEmpty()
        if(Regex("[0-9a-f-]{36}").matches(id)) File(context.cacheDir,"local-ai-input/$id.jpg").delete()
        finish(id,LocalAiFailure.INTERRUPTED.name)
        return LocalAiFailure.INTERRUPTED
    }
    fun report():String=buildString {
        appendLine("8bitBites local AI")
        appendLine("App: "+prefs.getString("app",BuildConfig.VERSION_NAME))
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("ABI: "+Build.SUPPORTED_ABIS.joinToString())
        appendLine("Runtime: LiteRT-LM 0.16.1 / CPU / context 4096 / output 1024")
        appendLine("Model: "+prefs.getString("model","unknown"))
        appendLine("Stage: "+prefs.getString("stage","unknown"))
        appendLine("Result: "+prefs.getString("result","unknown"))
        appendLine("Error class: "+prefs.getString("errorClass",""))
        appendLine("Available/total memory at start: ${prefs.getLong("available",0)/(1024*1024)} / ${prefs.getLong("total",0)/(1024*1024)} MB")
        if(Build.VERSION.SDK_INT>=30) try {
            val since=prefs.getLong("time",System.currentTimeMillis()-10*60*1000)-1000
            context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName,0,8)
                .firstOrNull { it.timestamp>=since && it.processName in setOf(context.packageName,context.packageName+":local_ai") }
                ?.let { appendLine("Android exit: ${it.processName.substringAfter(context.packageName)} reason=${it.reason} status=${it.status}") }
        } catch(_:Exception) { /* Exit history is optional on vendor builds. */ }
    }
}
