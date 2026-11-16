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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.eightbit.bite.core.AiProvider
import java.time.LocalDate

@Composable internal fun ai(en:String,tr:String)=if(LocalConfiguration.current.locales[0].language=="tr") tr else t(en)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(date:LocalDate,onClose:()->Unit,vm:AiViewModel=viewModel(),cameraRequest:Long=0,embedded:Boolean=false,openSettings:()->Unit={}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connection by vm.chatGpt.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
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
        AiProvider.CHATGPT -> connection.sharing && connection.model.isNotBlank() && !connection.busy
    }
    val detail=when(state.provider) {
        AiProvider.LOCAL -> state.modelName.ifBlank { ai("Choose a model in Settings","Ayarlar'dan model seç") }
        AiProvider.REMOTE -> state.model.ifBlank { ai("Configure in Settings","Ayarlar'dan yapılandır") }
        AiProvider.CHATGPT -> connection.models.firstOrNull { it.slug==connection.model }?.name ?: ai("Connect in Settings","Ayarlar'dan bağlan")
    }
    val content:@Composable ()->Unit={
        AiChatContent(Modifier.fillMaxSize(),state,detail,ready,edit={text -> vm.edit { it.copy(text=text.take(4000),error=null) }},
            send=vm::recognize,cancel=vm::cancel,newChat=vm::newChat,
            photo={withImages { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }},
            camera={withImages { showCamera=true }},removePhoto={vm.edit { it.copy(jpeg=null) }},
            review={showReview=true},retry=vm::retry,settings=openSettings,manageUsage={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://chatgpt.com/#settings/Usage")))},copyError={
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("8bitBites AI",vm.diagnosticDetails()))
                android.widget.Toast.makeText(context,copiedMessage,android.widget.Toast.LENGTH_SHORT).show()
            })
    }
    if(embedded) content() else Dialog(onDismissRequest={vm.cancel();onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) { content() }
    if(showCamera) MealCamera(onClose={showCamera=false},onCaptured={file -> showCamera=false;vm.photo(Uri.fromFile(file),file)})
    ChatGptWelcome(connection,vm.chatGpt::acknowledgeWelcome)
    if(showReview && state.draft.isNotEmpty()) ModalBottomSheet(onDismissRequest={showReview=false}) {
        AiMealReview(date,state,vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AiMealReview(date:LocalDate,state:AiState,vm:AiViewModel) {
    var meal by remember { mutableIntStateOf(vm.lastMeal) }
    var createFor by remember { mutableStateOf<AiReview?>(null) }
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text(ai("Review before saving","Kaydetmeden önce kontrol et"),style=MaterialTheme.typography.headlineSmall)
            Text(ai("Choose the matching foods and check the amounts. Nutrition comes from the selected food labels.",
                "Eşleşen besinleri seçip miktarları kontrol et. Besin değerleri seçtiğin ürün etiketinden gelir."),style=MaterialTheme.typography.bodyMedium) }
        items(state.draft,key={it.id}) { row -> OutlinedCard {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(row.mention.name,style=MaterialTheme.typography.titleMedium)
                if(row.mention.note.isNotBlank()) Text(row.mention.note,style=MaterialTheme.typography.bodySmall)
                if(row.mention.estimated && row.mention.grams!=null) Text(ai("Estimated amount","Tahmini miktar"),style=MaterialTheme.typography.labelMedium)
                OutlinedTextField(row.grams,{value -> vm.review(row.id) { it.copy(grams=value) }},label={Text(ai("Weight in grams","Gram cinsinden miktar"))},
                    singleLine=true,enabled=!state.busy,isError=row.weight()==null,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(row.search,{vm.search(row.id,it)},label={Text(ai("Find the matching food","Eşleşen besini bul"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                if(row.selected!=null) {
                    Text(row.selected.name,style=MaterialTheme.typography.titleSmall)
                    Text(row.selected.source,style=MaterialTheme.typography.bodySmall)
                    row.weight()?.let { weight -> val n=row.selected.nutrients().portion(weight)
                        Text(n.kcal?.let { it.display()+" kcal" } ?: ai("Energy unknown","Enerji bilinmiyor")) }
                    TextButton(onClick={vm.review(row.id) { it.copy(selected=null) }},enabled=!state.busy) { Text(ai("Change food","Besini değiştir")) }
                } else {
                    if(row.options.isEmpty()) Text(ai("No match. Try a simpler name or create a food from its label.","Eşleşme yok. Daha kısa bir ad ara veya etiketten besin oluştur."))
                    Column { row.options.take(6).forEach { food ->
                        TextButton(onClick={vm.review(row.id) { it.copy(selected=food) }},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text(food.name);Text(food.brand+" · "+food.source,style=MaterialTheme.typography.bodySmall) }
                        }
                    } }
                    TextButton(onClick={createFor=row},enabled=!state.busy) { Text(ai("Create food from label","Etiketten besin oluştur")) }
                }
                SupplementChoice(row.isSupplement,row.supplementKind) { checked,kind -> vm.review(row.id) { it.copy(isSupplement=checked,supplementKind=kind) } }
                TextButton(onClick={vm.drop(row.id)},enabled=!state.busy) { Text(ai("Remove item","Kaydı kaldır")) }
            }
        } }
        item { Text(date.toString());FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            meals.forEach { (id,label) -> FilterChip(meal==id,{meal=id;vm.selectMeal(id)},enabled=!state.busy,label={BiteText(label)}) }
        } }
        item { Button(onClick={vm.save(date,meal)},enabled=!state.busy && state.draft.isNotEmpty() && state.draft.all { it.selected!=null && it.weight()!=null },modifier=Modifier.fillMaxWidth()) {
            Text(ai("Confirm and save meal","Onayla ve öğünü kaydet"))
        } }
        state.error?.let { message -> item { Text(message,color=MaterialTheme.colorScheme.error) } }
    }
    createFor?.let { row -> FoodEditor(initialName=row.mention.name,onDismiss={createFor=null},save={food -> vm.review(row.id) { it.copy(selected=food) };createFor=null}) }
}
