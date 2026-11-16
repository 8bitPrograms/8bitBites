// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.eightbit.bite.ai.*
import org.eightbit.bite.core.*

internal data class AiSettingsActions(val choose:(AiProvider)->Unit={},val remote:(String,String)->Unit={_,_->},val key:(String)->Unit={},
    val browse:()->Unit={},val importModel:()->Unit={},val removeModel:()->Unit={},val connect:(String?,Boolean)->Unit={_,_->},
    val cancel:()->Unit={},val account:(String)->Unit={},val model:(String)->Unit={},val reload:()->Unit={},val disconnect:()->Unit={})

@Composable internal fun AiSettingsScreen(modifier:Modifier,vm:AiViewModel,modelsRequest:Long=0) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connection by vm.chatGpt.state.collectAsStateWithLifecycle()
    var showModels by remember { mutableStateOf(false) }
    val model=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.importModel(uri) }
    LaunchedEffect(modelsRequest) { if(modelsRequest>0) showModels=true }
    AiSettingsContent(modifier,state,connection,AiSettingsActions(
        choose={vm.configure(it)},remote={base,id -> vm.configure(state.provider,base,id)},key={value -> vm.edit { it.copy(key=value) }},
        browse={showModels=true},importModel={model.launch(arrayOf("*/*"))},removeModel=vm::removeModel,
        connect=vm::connectChatGpt,cancel=vm::cancelChatGpt,account=vm::selectChatGptAccount,model=vm::selectChatGptModel,
        reload=vm::reloadChatGptModels,disconnect=vm::disconnectChatGpt))
    if(showModels) ModelBrowser(state.busy,onClose={showModels=false})
    ChatGptWelcome(connection,vm.chatGpt::acknowledgeWelcome)
}

@Composable internal fun ChatGptWelcome(connection:ChatGptState,acknowledge:()->Unit) {
    if(connection.welcome) AlertDialog(onDismissRequest=acknowledge,icon={AiProviderIcon(AiProvider.CHATGPT,Modifier.size(36.dp))},
        title={Text(ai("You're using your ChatGPT plan","ChatGPT planını kullanıyorsun"))},
        text={Text(ai("Eligible AI requests in 8bitBites use your ChatGPT plan. You can manage usage and app limits in ChatGPT settings.",
            "8bitBites'taki uygun AI istekleri ChatGPT planından kullanılır. Kullanımı ve uygulama limitini ChatGPT ayarlarından yönetebilirsin."))},
        confirmButton={TextButton(onClick=acknowledge) { Text(ai("Got it","Anladım")) }})
}

@Composable internal fun AiProviderChoices(provider:AiProvider,enabled:Boolean,choose:(AiProvider)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        AiProvider.entries.forEach { item ->
            OutlinedCard(onClick={choose(item)},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("provider-"+item.name),
                border=BorderStroke(if(provider==item) 2.dp else 1.dp,if(provider==item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    AiProviderIcon(item,Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(aiProviderName(item),style=MaterialTheme.typography.titleSmall)
                        Text(when(item) {
                            AiProvider.LOCAL -> ai("A downloaded model on this phone","Bu telefona indirilen model")
                            AiProvider.REMOTE -> ai("An OpenAI-compatible endpoint","OpenAI uyumlu sunucu")
                            AiProvider.CHATGPT -> ai("Your own ChatGPT account and plan","Kendi ChatGPT hesabın ve planın")
                        },style=MaterialTheme.typography.bodySmall)
                    }
                    RadioButton(selected=provider==item,onClick=null,enabled=enabled)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AiSettingsContent(modifier:Modifier,state:AiState,connection:ChatGptState,actions:AiSettingsActions) {
    val context=LocalContext.current
    val enabled=!state.busy && !connection.busy
    var accounts by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(false) }
    LazyColumn(modifier.imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(ai("Choose your AI connection","AI bağlantını seç"),style=MaterialTheme.typography.headlineSmall)
            Text(ai("Connection settings live here. Changing the provider starts a new chat.","Bağlantı ayarları burada. Sağlayıcı değişince yeni sohbet başlar."),style=MaterialTheme.typography.bodyMedium) }
        item { AiProviderChoices(state.provider,enabled,actions.choose) }
        when(state.provider) {
            AiProvider.LOCAL -> {
                item { Text(ai("On-device model","Cihazdaki model"),style=MaterialTheme.typography.titleLarge)
                    Text(state.modelName.ifBlank { ai("No model selected","Model seçilmedi") })
                    Text(ai("Text and photos are processed on this phone. A photo-capable model is needed for images.",
                        "Metin ve fotoğraflar bu telefonda işlenir. Fotoğraflar için görsel destekli model gerekir."),style=MaterialTheme.typography.bodySmall) }
                item { Button(onClick=actions.browse,enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text(ai("Browse & download models","Modellere göz at ve indir")) } }
                item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=actions.importModel,enabled=enabled) { Text(ai("Import model","Model içe aktar")) }
                    if(state.modelName.isNotBlank()) TextButton(onClick=actions.removeModel,enabled=enabled) { Text(ai("Remove selected model","Seçili modeli kaldır")) }
                } }
            }
            AiProvider.REMOTE -> {
                item { Text(ai("OpenAI-compatible server","OpenAI uyumlu sunucu"),style=MaterialTheme.typography.titleLarge)
                    Text(ai("Chat messages and attached photos go to the endpoint you choose.","Sohbet mesajları ve eklediğin fotoğraflar seçtiğin sunucuya gönderilir."),style=MaterialTheme.typography.bodySmall) }
                item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={actions.remote("https://api.openai.com/v1","")},enabled=enabled) { Text("OpenAI API") }
                    OutlinedButton(onClick={actions.remote("https://generativelanguage.googleapis.com/v1beta/openai","")},enabled=enabled) { Text("Gemini") }
                } }
                item { OutlinedTextField(state.base,{actions.remote(it,state.model)},label={Text(ai("Server address (HTTPS)","Sunucu adresi (HTTPS)"))},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth()) }
                item { OutlinedTextField(state.model,{actions.remote(state.base,it)},label={Text(ai("Model ID","Model kimliği"))},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth()) }
                item { OutlinedTextField(state.key,actions.key,label={Text(ai("API key (if required)","API anahtarı (gerekiyorsa)"))},
                    supportingText={Text(ai("Kept in memory for this app session.","Bu uygulama oturumu boyunca bellekte tutulur."))},
                    visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth()) }
            }
            AiProvider.CHATGPT -> {
                item { Text(ai("Your ChatGPT connection","ChatGPT bağlantın"),style=MaterialTheme.typography.titleLarge)
                    Text(ai("Sign in in your browser and allow this app to use your plan. Usage counts against your ChatGPT limits. No API key is needed.",
                        "Tarayıcıda kendi hesabına girip bu uygulamaya plan kullanım izni ver. Kullanım ChatGPT limitlerinden düşer. API anahtarı gerekmez."),style=MaterialTheme.typography.bodyMedium) }
                if(connection.profiles.isNotEmpty()) item { Box {
                    OutlinedButton(onClick={accounts=true},enabled=enabled,modifier=Modifier.fillMaxWidth()) {
                        Text(connection.profiles.firstOrNull { it.id==connection.activeId }?.label ?: ai("Choose account","Hesap seç"),Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown,null)
                    }
                    DropdownMenu(accounts,onDismissRequest={accounts=false}) { connection.profiles.forEach { profile ->
                        DropdownMenuItem(text={Text(profile.label)},onClick={accounts=false;actions.account(profile.id)})
                    } }
                } }
                item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        AiProviderIcon(AiProvider.CHATGPT,Modifier.size(24.dp))
                        Text(if(connection.connected) ai("Connected","Bağlandı") else ai("Not connected","Bağlı değil"),style=MaterialTheme.typography.titleMedium)
                    }
                    if(connection.email.isNotBlank()) Text(connection.email)
                    if(connection.connected) Text(if(connection.sharing) ai("ChatGPT plan usage is enabled","ChatGPT plan kullanımı açık") else ai("Plan usage permission is not enabled","Plan kullanım izni açık değil"),style=MaterialTheme.typography.bodySmall)
                    if(!connection.connected || !connection.sharing) Button(onClick={actions.connect(connection.activeId.takeIf { it.isNotBlank() }
                        ?: connection.profiles.lastOrNull { !it.connected }?.id,connection.connected)},enabled=enabled,modifier=Modifier.fillMaxWidth(),
                        colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF0D0D0D),contentColor=Color.White)) {
                        AiProviderIcon(AiProvider.CHATGPT,Modifier.size(21.dp),color=Color.White);Spacer(Modifier.width(8.dp))
                        Text(ai("Continue with ChatGPT","ChatGPT ile devam et"))
                    }
                    if(connection.connected) TextButton(onClick=actions.disconnect,enabled=enabled) { Text(ai("Sign out","Çıkış yap")) }
                } } }
                if(connection.connected && connection.sharing) {
                    item { Box {
                        OutlinedButton(onClick={models=true},enabled=enabled && connection.models.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {
                            Text(connection.models.firstOrNull { it.slug==connection.model }?.name ?: ai("Choose model","Model seç"),Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowDown,null)
                        }
                        DropdownMenu(models,onDismissRequest={models=false}) { connection.models.forEach { model ->
                            DropdownMenuItem(text={Text(model.name)},onClick={models=false;actions.model(model.slug)})
                        } }
                    } }
                    item { TextButton(onClick=actions.reload,enabled=enabled) { Text(ai("Refresh available models","Kullanılabilen modelleri yenile")) } }
                }
                item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick={actions.connect(null,false)},enabled=enabled) { Text(ai("Add another account","Başka hesap ekle")) }
                    TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://chatgpt.com/#settings/Usage")))}) { Text(ai("Manage ChatGPT usage","ChatGPT kullanımını yönet")) }
                } }
                if(connection.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth());Text(ai("Completing your connection…","Bağlantı tamamlanıyor…"))
                    TextButton(onClick=actions.cancel) { Text(ai("Cancel","İptal")) } }
                connection.error?.let { code -> item { Text(chatGptError(context,code),color=MaterialTheme.colorScheme.error) } }
            }
        }
        if(state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth());Text(state.status) }
        state.error?.let { message -> item { Text(message,color=MaterialTheme.colorScheme.error) } }
    }
}
