// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.eightbit.bite.core.AiProvider
import org.eightbit.bite.core.total
import java.time.LocalDate

@Composable internal fun ai(en:String,tr:String)=if(LocalConfiguration.current.locales[0].language=="tr") tr else t(en)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(date:LocalDate,onClose:()->Unit,vm:AiViewModel=viewModel(),cameraRequest:Long=0,embedded:Boolean=false,openSettings:()->Unit={},modifier:Modifier=Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connection by vm.chatGpt.state.collectAsStateWithLifecycle()
    val histories by vm.history.collectAsStateWithLifecycle()
    val historyLoading by vm.historyLoading.collectAsStateWithLifecycle()
    val historyError by vm.historyError.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val photoError=ai("Choose a photo-capable model in Settings to use images.","Fotoğraf için Ayarlar'dan görsel destekli bir model seç.")
    val copiedMessage=ai("Error details copied","Hata bilgisi kopyalandı")
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if(uri!=null) vm.photo(uri) }
    fun withImages(action:()->Unit) {
        if(state.provider==AiProvider.LOCAL && vm.models.active()?.vision==false) vm.edit { it.copy(error=photoError) } else action()
    }
    LaunchedEffect(cameraRequest) { if(cameraRequest>0) withImages { showCamera=true } }
    LaunchedEffect(state.draft.isEmpty()) { if(state.draft.isEmpty()) showReview=false }
    val ready=when(state.provider) {
        AiProvider.LOCAL -> state.modelName.isNotBlank()
        AiProvider.REMOTE -> state.model.isNotBlank() && runCatching { org.eightbit.bite.core.AiMeals.endpoint(state.base) }.isSuccess
        AiProvider.CHATGPT -> connection.sharing && connection.model.isNotBlank() && !connection.busy &&
            (state.accountId.isBlank() || state.accountId==connection.activeId)
    }
    val detail=when(state.provider) {
        AiProvider.LOCAL -> state.modelName.ifBlank { ai("Choose a model in Settings","Ayarlar'dan model seç") }
        AiProvider.REMOTE -> state.model.ifBlank { ai("Configure in Settings","Ayarlar'dan yapılandır") }
        AiProvider.CHATGPT -> connection.models.firstOrNull { it.slug==connection.model }?.name ?: ai("Connect in Settings","Ayarlar'dan bağlan")
    }
    val content:@Composable ()->Unit={
        AiChatContent(modifier.fillMaxSize(),state,detail,ready,edit={text -> vm.edit { it.copy(text=text.take(4000),error=null) }},
            send=vm::recognize,cancel=vm::cancel,newChat=vm::newChat,
            photo={withImages { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }},
            camera={withImages { showCamera=true }},removePhoto={vm.edit { it.copy(jpeg=null) }},
            review={showReview=true},retry=vm::retry,settings=openSettings,history={showHistory=true;vm.refreshHistory()},manageUsage={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://chatgpt.com/#settings/Usage")))},copyError={
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("8bitBites AI",vm.diagnosticDetails()))
                android.widget.Toast.makeText(context,copiedMessage,android.widget.Toast.LENGTH_SHORT).show()
            })
    }
    if(embedded) content() else Dialog(onDismissRequest={vm.cancel();onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) { content() }
    if(showCamera) MealCamera(onClose={showCamera=false},onCaptured={file -> showCamera=false;vm.photo(Uri.fromFile(file),file)})
    ChatGptWelcome(connection,vm.chatGpt::acknowledgeWelcome)
    if(showReview && state.draft.isNotEmpty()) ModalBottomSheet(onDismissRequest={showReview=false}) {
        AiEstimateReview(date,state,vm.lastMeal,vm::review,vm::drop,vm::selectMeal) { vm.save(date,it) }
    }
    if(showHistory) AiHistorySheet(histories,historyLoading,state.busy,historyError,state.sessionId,
        close={showHistory=false},open={id -> showHistory=false;showReview=false;vm.openConversation(id)},delete=vm::deleteConversation)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun AiEstimateReview(date:LocalDate,state:AiState,lastMeal:Int,
    change:(String,(AiReview)->AiReview)->Unit,drop:(String)->Unit,selectMeal:(Int)->Unit,confirm:(Int)->Unit) {
    var meal by remember { mutableIntStateOf(lastMeal) }
    val portions=state.draft.map { it.nutrients() ?: org.eightbit.bite.core.Nutrients() }
    val energy=portions.total { it.kcal }
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding(),contentPadding=PaddingValues(20.dp,12.dp,20.dp,40.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Text(ai("Confirm AI estimate","AI tahminini onayla"),style=MaterialTheme.typography.headlineSmall)
            Text(ai("Check the portions and estimated nutrition. Approval adds this meal directly to your diary.",
                "Porsiyonları ve tahmini besin değerlerini kontrol et. Onayınla öğün doğrudan günlüğüne eklenir."),
                style=MaterialTheme.typography.bodyMedium)
        }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("~"+energy.known.display()+(if(energy.complete) "" else " +?")+" kcal",style=MaterialTheme.typography.titleLarge)
            Text(ai("AI estimate","AI tahmini"),style=MaterialTheme.typography.labelMedium)
        } } }
        items(state.draft,key={it.id}) { row -> OutlinedCard {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(row.mention.name,style=MaterialTheme.typography.titleMedium)
                if(row.mention.note.isNotBlank()) Text(row.mention.note,style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(row.grams,{value -> change(row.id) { it.copy(grams=value) }},
                    label={Text(ai("Weight in grams","Gram cinsinden miktar"))},singleLine=true,enabled=!state.busy,
                    isError=row.weight()==null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                val nutrition=row.nutrients()
                Text(nutrition?.kcal.nutrient("kcal"),style=MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(ai("Protein","Protein")+": "+nutrition?.protein.nutrient("g"),style=MaterialTheme.typography.bodySmall)
                    Text(ai("Carbs","Karbonhidrat")+": "+nutrition?.carbs.nutrient("g"),style=MaterialTheme.typography.bodySmall)
                    Text(ai("Fat","Yağ")+": "+nutrition?.fat.nutrient("g"),style=MaterialTheme.typography.bodySmall)
                }
                if(nutrition?.kcal==null) Text(ai("Some values are unknown. You can clarify them in the chat.",
                    "Bazı değerler bilinmiyor. Sohbette ayrıntı verebilirsin."),style=MaterialTheme.typography.bodySmall)
                SupplementChoice(row.isSupplement,row.supplementKind) { checked,kind ->
                    change(row.id) { it.copy(isSupplement=checked,supplementKind=kind) }
                }
                TextButton(onClick={drop(row.id)},enabled=!state.busy) { Text(ai("Remove item","Kaydı kaldır")) }
            }
        } }
        item {
            Text(date.toString())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                meals.forEach { (id,label) -> FilterChip(meal==id,{meal=id;selectMeal(id)},enabled=!state.busy,label={BiteText(label)}) }
            }
        }
        item { Button(onClick={confirm(meal)},enabled=!state.busy && state.draft.isNotEmpty() && state.draft.all { it.weight()!=null },
            modifier=Modifier.fillMaxWidth().testTag("approve-ai-meal")) { Text(ai("Approve and add","Onayla ve ekle")) } }
        state.error?.let { message -> item { Text(message,color=MaterialTheme.colorScheme.error) } }
    }
}
