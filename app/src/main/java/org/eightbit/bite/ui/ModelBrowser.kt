// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import org.eightbit.bite.BiteApplication
import org.eightbit.bite.ai.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBrowser(busy:Boolean,onClose:()->Unit) {
    val context=LocalContext.current
    val app=context.applicationContext as BiteApplication
    val library=app.models
    val libraryState by library.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var unmetered by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<String?>(null) }
    fun action(block:suspend ()->Unit) { scope.launch {
        try { block();error=null }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(failed:Exception) { error=failed.message ?: "Could not change this model" }
    } }
    fun start(id:String) { scope.launch {
        try { ModelDownloadJobs.start(app,id,token,unmetered);token="";error=null }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(failed:Exception) { error=failed.message }
    } }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingDownload?.let(::start);pendingDownload=null
    }
    fun download(id:String) {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            pendingDownload=id;notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else start(id)
    }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ModelDownloadJobs.reconcile(app) } }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Scaffold(topBar={TopAppBar(title={Text(ai("On-device models","Cihazdaki modeller"))},navigationIcon={IconButton(onClick=onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack,ai("Back","Geri")) }})}) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item { Text(ai("Download from Hugging Face","Hugging Face üzerinden indir"),style=MaterialTheme.typography.headlineSmall)
                    Text(ai("Supported LiteRT-LM files are listed below. Downloads continue when you leave this screen or put the app in the background. You can pause and resume them. Android may pause work under system restrictions.",
                        "Desteklenen LiteRT-LM dosyaları aşağıda. Ekrandan ayrılsan veya uygulama arka plana geçse de indirme sürer. Duraklatıp devam edebilirsin. Android sistem koşullarına göre işi duraklatabilir.")) }
                item { ListItem(headlineContent={Text(ai("Unmetered network only","Yalnızca tarifesiz ağ"))},supportingContent={Text(ai("Wi-Fi or another unmetered connection","Wi-Fi veya başka bir tarifesiz bağlantı"))},trailingContent={Switch(unmetered,{unmetered=it})}) }
                item { OutlinedTextField(token,{token=it},label={Text(ai("Hugging Face read token (optional)","Hugging Face okuma anahtarı (isteğe bağlı)"))},visualTransformation=PasswordVisualTransformation(),
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth())
                    Text(ai("Qwen does not require a token. For Gemma, open its model page, accept the access conditions and use a read token from that account. Tokens used by background downloads are encrypted on this device and removed after completion or deletion.",
                        "Qwen için anahtar gerekmez. Gemma için model sayfasındaki erişim koşullarını kabul et ve o hesaptan bir okuma anahtarı kullan. Arka plan indirme anahtarı cihazda şifrelenir; indirme bitince veya silinince kaldırılır."),style=MaterialTheme.typography.bodySmall) }
                error?.let { message -> item { Text(message,color=MaterialTheme.colorScheme.error) } }
                items(library.catalogue,key={it.id}) { model ->
                    val status=libraryState.states[model.id] ?: ModelStatus()
                    Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(model.name,style=MaterialTheme.typography.titleLarge)
                        val size=String.format(Locale.ROOT,"%.2f",model.bytes/1_000_000_000.0)
                        Text("$size GB · "+if(model.vision) ai("Text + photos","Metin + fotoğraf") else ai("Text only","Yalnızca metin"))
                        Text(ai("Suggested RAM: ${model.memoryGb} GB or more. Actual compatibility and speed depend on the device.",
                            "Önerilen RAM: ${model.memoryGb} GB veya üzeri. Uyumluluk ve hız cihaza bağlıdır."),style=MaterialTheme.typography.bodySmall)
                        Text("${model.repository} · ${model.license}",style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(model.page)))}) { Text(ai("Model page & license","Model sayfası ve lisansı")) }
                        if(status.wanted || status.phase=="paused" || status.phase=="failed") {
                            LinearProgressIndicator(progress={ (status.downloaded.toDouble()/model.bytes).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                            val phase=when(status.phase) {
                                "queued"->ai("Waiting for the selected network","Seçilen ağ bekleniyor")
                                "waiting"->ai("Waiting to resume","Devam etmek için bekleniyor")
                                "verifying"->ai("Verifying model","Model doğrulanıyor")
                                "paused"->ai("Paused","Duraklatıldı")
                                "failed"->ai("Download needs attention","İndirme kontrol gerektiriyor")
                                else->ai("Downloading","İndiriliyor")
                            }
                            Text("$phase · ${status.downloaded/(1024*1024)} / ${model.bytes/(1024*1024)} MB")
                        }
                        if(status.error.isNotBlank()) Text(status.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            when {
                                status.phase=="ready" -> {
                                    Button(onClick={action { library.select(model.id) }},enabled=!busy && libraryState.selected!=model.id) { Text(if(libraryState.selected==model.id) ai("Selected","Seçili") else ai("Use model","Modeli kullan")) }
                                    TextButton(onClick={scope.launch { try { library.remove(model.id) } catch(failed:Exception) {error=failed.message} }},enabled=!busy) { Text(ai("Delete","Sil")) }
                                }
                                status.wanted -> OutlinedButton(onClick={ModelDownloadJobs.pause(app,model.id)}) { Text(ai("Pause","Duraklat")) }
                                else -> {
                                    Button(onClick={download(model.id)},enabled=pendingDownload==null) { Text(if(status.downloaded>0) ai("Resume","Devam et") else ai("Download","İndir")) }
                                    if(status.downloaded>0) TextButton(onClick={scope.launch { try { library.remove(model.id) } catch(failed:Exception) {error=failed.message} }}) { Text(ai("Discard","Vazgeç ve sil")) }
                                }
                            }
                        }
                    } }
                }
                if(libraryState.importedName.isNotBlank() && libraryState.states["imported"]?.phase=="ready") item {
                    Card { Column(Modifier.padding(16.dp)) {
                        Text(libraryState.importedName,style=MaterialTheme.typography.titleMedium)
                        Text(ai("Imported model · capabilities depend on the file","İçe aktarılan model · yetenekler dosyaya bağlıdır"))
                        Button(onClick={action { library.select("imported") }},enabled=!busy && libraryState.selected!="imported") { Text(ai("Use imported model","İçe aktarılan modeli kullan")) }
                        TextButton(onClick={action { library.remove("imported") }},enabled=!busy) { Text(ai("Delete","Sil")) }
                    } }
                }
                item { Text(ai("Downloads are verified before use. Model licenses are separate from the app license.",
                    "İndirilen modeller kullanılmadan önce doğrulanır. Model lisansları uygulama lisansından ayrıdır."),style=MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
