// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.health.connect.client.records.MealType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate

@Composable
internal fun ai(en:String,tr:String)=if(LocalConfiguration.current.locales[0].language=="tr") tr else t(en)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(date:LocalDate,onClose:()->Unit,vm:AiViewModel=viewModel(),cameraRequest:Long=0,modelsRequest:Long=0,embedded:Boolean=false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val copiedMessage=ai("Error details copied","Hata bilgisi kopyalandı")
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if(uri!=null) vm.photo(uri) }
    val model=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.importModel(uri) }
    var meal by remember { mutableIntStateOf(vm.lastMeal) }
    var createFor by remember { mutableStateOf<AiReview?>(null) }
    var showCamera by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    LaunchedEffect(modelsRequest) { if(modelsRequest>0) showModels=true }
    LaunchedEffect(cameraRequest) { if(cameraRequest>0) showCamera=true }
    val screen: @Composable () -> Unit = {
        Scaffold(modifier=Modifier.fillMaxSize(),topBar={if(!embedded) TopAppBar(title={Text(ai("AI meal entry","AI ile öğün ekle"))},
            navigationIcon={IconButton(onClick={vm.cancel();onClose()}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,ai("Back","Geri")) }})}) { insets ->
            LazyColumn(Modifier.fillMaxSize().padding(insets).imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(state.draft.isEmpty() && !state.saved) {
                    item { Text(ai("Describe your meal or choose a photo.","Öğününü yaz veya bir fotoğraf seç."),style=MaterialTheme.typography.titleLarge) }
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected=!state.api,onClick={vm.configure(false)},enabled=!state.busy,label={Text(ai("On device","Cihazda"))})
                        FilterChip(selected=state.api,onClick={vm.configure(true)},enabled=!state.busy,label={Text(ai("API key","API anahtarı"))})
                    } }
                    if(state.api) {
                        item { Text(ai("Only this description and selected photo are sent to the service below. Your key stays in memory until the app closes.",
                            "Yalnızca bu açıklama ve seçtiğin fotoğraf aşağıdaki servise gönderilir. Anahtarın uygulama kapanana kadar bellekte tutulur."),style=MaterialTheme.typography.bodySmall) }
                        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={vm.configure(true,"https://api.openai.com/v1","")},enabled=!state.busy) { Text("OpenAI") }
                            OutlinedButton(onClick={vm.configure(true,"https://generativelanguage.googleapis.com/v1beta/openai","")},enabled=!state.busy) { Text("Gemini") }
                        } }
                        item { OutlinedTextField(state.base,{vm.configure(true,it)},label={Text(ai("API base URL (HTTPS)","API adresi (HTTPS)"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(state.model,{vm.configure(true,model=it)},label={Text(ai("Model ID","Model kimliği"))},supportingText={Text(ai("Choose a vision model for photos.","Fotoğraflar için görsel destekli bir model seç."))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(state.key,{key->vm.edit { it.copy(key=key) }},label={Text(ai("Your API key · session only","API anahtarın · yalnızca bu oturum"))},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) }
                    } else {
                        item { Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(state.modelName.ifBlank { ai("Set up a local model","Yerel model kur") },style=MaterialTheme.typography.titleMedium)
                            Text(ai("Download a supported model from Hugging Face, or import a .litertlm file. Choose a text-and-photo model for pictures. Processing stays on your device.",
                                "Hugging Face üzerinden desteklenen bir model indir veya .litertlm dosyası içe aktar. Fotoğraflar için metin ve fotoğraf destekli model seç. İşleme cihazında yapılır."),style=MaterialTheme.typography.bodySmall)
                            Button(onClick={showModels=true}) { Text(ai("Browse & download models","Modellere göz at ve indir")) }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={model.launch(arrayOf("*/*"))},enabled=!state.busy) { Text(ai("Import model","Model içe aktar")) }
                                if(state.modelName.isNotBlank()) TextButton(onClick=vm::removeModel,enabled=!state.busy) { Text(ai("Remove","Kaldır")) }
                            }
                            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/google-ai-edge/LiteRT-LM")))}) { Text(ai("Compatible models","Uyumlu modeller")) }
                        } } }
                    }
                    item { OutlinedTextField(state.text,{text->vm.edit { it.copy(text=text.take(4000),error=null) }},label={Text(ai("What did you eat?","Ne yedin?"))},placeholder={Text(ai("100 g cheese and 2 slices of bread","100 g peynir ve 2 dilim ekmek"))},minLines=3,enabled=!state.busy,modifier=Modifier.fillMaxWidth()) }
                    item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={showCamera=true},enabled=!state.busy) { Text(ai("Take photo","Fotoğraf çek")) }
                        OutlinedButton(onClick={photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!state.busy) { Text(ai("Choose photo","Fotoğraf seç")) }
                    } }
                    if(state.jpeg!=null) item { TextButton(onClick={vm.edit { it.copy(jpeg=null) }},enabled=!state.busy) { Text(ai("Remove photo","Fotoğrafı kaldır")) } }
                    state.jpeg?.let { jpeg -> item {
                        val bitmap=remember(jpeg) { BitmapFactory.decodeByteArray(jpeg,0,jpeg.size) }
                        Image(bitmap.asImageBitmap(),ai("Selected meal photo","Seçilen öğün fotoğrafı"),modifier=Modifier.fillMaxWidth().heightIn(max=200.dp))
                    } }
                    item { Button(onClick=vm::recognize,enabled=!state.busy && (state.text.isNotBlank() || state.jpeg!=null) &&
                        (if(state.api) state.key.isNotBlank() && state.model.isNotBlank() else state.modelName.isNotBlank()),modifier=Modifier.fillMaxWidth()) { Text(ai("Recognize meal","Öğünü tanı")) } }
                }
                if(state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth());Text(state.status);TextButton(onClick=vm::cancel) { Text(ai("Cancel","İptal")) } }
                state.error?.let { error -> item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(error,color=MaterialTheme.colorScheme.error)
                    if(!state.api) TextButton(onClick={
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("8bitBites AI",vm.diagnosticDetails()))
                        android.widget.Toast.makeText(context,copiedMessage,android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text(ai("Copy error details","Hata bilgisini kopyala")) }
                } } }
                if(state.saved) item { Card { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(ai("Meal added to your diary","Öğün günlüğüne eklendi"),style=MaterialTheme.typography.titleLarge)
                    Button(onClick=onClose) { Text(ai("Back to diary","Günlüğe dön")) }
                    TextButton(onClick=vm::clear) { Text(ai("Add another meal","Başka öğün ekle")) }
                } } }
                if(state.draft.isNotEmpty()) {
                    item { Text(ai("Review before saving","Kaydetmeden önce kontrol et"),style=MaterialTheme.typography.headlineSmall)
                        Text(ai("Select the correct database food and check each weight. AI suggestions can be inaccurate. Nutrition is calculated from your selection.",
                            "Doğru veritabanı yiyeceğini seç ve her miktarı kontrol et. AI tahminleri hatalı olabilir. Besin değerleri seçtiğin kayıttan hesaplanır.")) }
                    items(state.draft,key={it.id}) { row -> Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(row.mention.name,style=MaterialTheme.typography.titleMedium)
                        if(row.mention.note.isNotBlank()) Text(row.mention.note,style=MaterialTheme.typography.bodySmall)
                        if(row.mention.estimated && row.mention.grams!=null) Text(ai("Estimated weight · please verify","Tahmini miktar · lütfen kontrol et"),color=MaterialTheme.colorScheme.primary)
                        OutlinedTextField(row.grams,{value->vm.review(row.id) { it.copy(grams=value) }},label={Text(ai("Weight in grams","Gram cinsinden miktar"))},singleLine=true,enabled=!state.busy,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=row.weight()==null,modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(row.search,{vm.search(row.id,it)},label={Text(ai("Find the matching food","Eşleşen yiyeceği bul"))},singleLine=true,enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                        if(row.selected!=null) {
                            Text("${row.selected.name} · ${row.selected.brand}",style=MaterialTheme.typography.titleSmall)
                            Text(row.selected.source,style=MaterialTheme.typography.bodySmall)
                            row.weight()?.let { weight -> val n=row.selected.nutrients().portion(weight)
                                Text("${n.kcal?.let { "%.0f kcal".format(it) } ?: ai("Energy unknown","Enerji bilinmiyor")} · ${n.protein?.let { "%.1f g".format(it) } ?: "?"} ${ai("protein","protein")}") }
                            TextButton(onClick={vm.review(row.id) { it.copy(selected=null) }},enabled=!state.busy) { Text(ai("Change food","Yiyeceği değiştir")) }
                        } else {
                            if(row.options.isEmpty()) Text(ai("No match. Try a simpler name or create a food from its label.","Eşleşme yok. Daha kısa bir ad ara veya ambalaj etiketinden yiyecek oluştur."))
                            Column(Modifier.heightIn(max=240.dp).then(Modifier)) {
                                androidx.compose.foundation.lazy.LazyColumn { items(row.options,key={it.id}) { food ->
                                    TextButton(onClick={vm.review(row.id) { it.copy(selected=food) }},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) {
                                        Text(food.name);Text("${food.brand} · ${food.source}",style=MaterialTheme.typography.bodySmall)
                                    } }
                                } }
                            }
                            TextButton(onClick={createFor=row},enabled=!state.busy) { Text(ai("Create food from label","Etiketten yiyecek oluştur")) }
                        }
                        SupplementChoice(row.isSupplement,row.supplementKind) { checked,kind -> vm.review(row.id) { it.copy(isSupplement=checked,supplementKind=kind) } }
                        TextButton(onClick={vm.drop(row.id)},enabled=!state.busy) { Text(ai("Remove item","Kaydı kaldır")) }
                    } } }
                    item { Text(date.toString());Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf(MealType.MEAL_TYPE_BREAKFAST to ai("Breakfast","Kahvaltı"),MealType.MEAL_TYPE_LUNCH to ai("Lunch","Öğle"),MealType.MEAL_TYPE_DINNER to ai("Dinner","Akşam"),MealType.MEAL_TYPE_SNACK to ai("Snack","Ara öğün")).forEach { (id,label) ->
                            FilterChip(selected=meal==id,onClick={meal=id;vm.selectMeal(id)},enabled=!state.busy,label={Text(label)})
                        }
                    } }
                    item { Button(onClick={vm.save(date,meal)},enabled=!state.busy && state.draft.all { it.selected!=null && it.weight()!=null },modifier=Modifier.fillMaxWidth()) { Text(ai("Confirm and save meal","Onayla ve öğünü kaydet")) }
                        TextButton(onClick=vm::clear,enabled=!state.busy) { Text(ai("Start again","Yeniden başla")) } }
                }
            }
        }
        createFor?.let { row -> FoodEditor(initialName=row.mention.name,onDismiss={createFor=null},save={food->vm.review(row.id) { it.copy(selected=food) };createFor=null}) }
        if(showCamera) MealCamera(onClose={showCamera=false},onCaptured={file->showCamera=false;vm.photo(Uri.fromFile(file),file)})
        if(showModels) ModelBrowser(state.busy,onClose={showModels=false})
    }
    if(embedded) screen() else Dialog(onDismissRequest={vm.cancel();onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) { screen() }
}
