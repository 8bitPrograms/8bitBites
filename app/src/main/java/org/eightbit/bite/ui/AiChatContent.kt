// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.eightbit.bite.R
import org.eightbit.bite.core.AiProvider
import org.eightbit.bite.ai.AiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun AiPhoto(bytes:ByteArray,modifier:Modifier=Modifier) {
    val bitmap=remember(bytes) { BitmapFactory.decodeByteArray(bytes,0,bytes.size) }
    if(bitmap!=null) Image(bitmap.asImageBitmap(),ai("Meal photo","Öğün fotoğrafı"),modifier.heightIn(max=190.dp))
}

@Composable internal fun AiSavedPhoto(path:String,modifier:Modifier=Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,path) { value=withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) } }
    bitmap?.let { Image(it.asImageBitmap(),ai("Meal photo","Öğün fotoğrafı"),modifier.heightIn(max=190.dp)) }
}

@Composable internal fun AiChatBubble(message:AiChatMessage,review:Boolean,saved:Boolean,onReview:()->Unit) {
    Box(Modifier.fillMaxWidth(),contentAlignment=if(message.user) Alignment.CenterEnd else Alignment.CenterStart) {
        val body=Modifier.testTag(if(message.user) "chat-user" else "chat-assistant")
        Column(if(message.user) body.widthIn(max=300.dp).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(18.dp)).padding(horizontal=14.dp,vertical=10.dp)
            else body.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            message.photo?.let { AiPhoto(it,Modifier.fillMaxWidth()) }
            if(message.photo==null) message.photoPath?.let { AiSavedPhoto(it,Modifier.fillMaxWidth()) }
            if(message.text.isNotBlank()) Text(message.text,style=MaterialTheme.typography.bodyLarge)
            if(message.foods.isNotEmpty()) {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    message.foods.take(4).forEach { food ->
                        Text(food.name+food.grams?.let { " · ${it.display()} g" }.orEmpty()+
                            food.grams?.let { grams -> food.nutrition?.portion(grams)?.kcal?.let { " · ~${it.display()} kcal" } }.orEmpty(),
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(message.foods.size>4) Text("+${message.foods.size-4}",style=MaterialTheme.typography.bodySmall)
                }
            }
            if(review) OutlinedButton(onClick=onReview,contentPadding=PaddingValues(horizontal=14.dp,vertical=0.dp)) {
                Text(ai("Review meal","Öğünü incele"))
            }
            if(saved) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Default.Check,null,Modifier.size(16.dp))
                Text(ai("Saved to diary","Günlüğe kaydedildi"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AiChatContent(modifier:Modifier,state:AiState,providerDetail:String,ready:Boolean,
    edit:(String)->Unit,send:()->Unit,cancel:()->Unit,newChat:()->Unit,photo:()->Unit,camera:()->Unit,removePhoto:()->Unit,
    review:()->Unit,retry:()->Unit,settings:()->Unit,copyError:()->Unit,manageUsage:()->Unit={},history:()->Unit={}) {
    val list=rememberLazyListState()
    var attachments by remember { mutableStateOf(false) }
    val example=ai("100 g cheese and 2 slices of bread","100 g peynir ve 2 dilim ekmek")
    val messages=state.messages.filterNot { it.acknowledges.isNotBlank() && it.acknowledges in state.savedMessages }
    LaunchedEffect(state.sessionId,messages.size,state.busy,state.error) {
        if(messages.isNotEmpty() || state.busy || state.error!=null) list.animateScrollToItem(messages.size+4)
    }
    Column(modifier.imePadding()) {
        TopAppBar(title={
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AiProviderIcon(state.provider,Modifier.size(24.dp))
                Column {
                    Text(aiProviderName(state.provider),maxLines=1,style=MaterialTheme.typography.titleMedium)
                    Text(providerDetail,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },windowInsets=WindowInsets(0),actions={
            IconButton(onClick=history,enabled=!state.busy) { Icon(painterResource(R.drawable.ic_chat_history),ai("Chat history","Sohbet geçmişi")) }
            IconButton(onClick=newChat,enabled=!state.busy) { Icon(painterResource(R.drawable.ic_chat_new),ai("New chat","Yeni sohbet")) }
            IconButton(onClick=settings) { Icon(Icons.Default.Settings,ai("AI settings","AI ayarları")) }
        })
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ai-conversation"),state=list,
            contentPadding=PaddingValues(20.dp,12.dp,20.dp,20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            if(messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top=72.dp,bottom=24.dp),horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(ai("What did you eat today?","Bugün ne yedin?"),style=MaterialTheme.typography.headlineSmall)
                    Text(ai("Describe your meal or add a photo.","Öğününü yaz veya bir fotoğraf ekle."),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(ready) SuggestionChip(onClick={edit(example)},label={Text(ai("Try an example","Bir örnek dene"))})
                }
            }
            if(!ready) item {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(ai("Connect an AI provider in Settings to start.","Başlamak için Ayarlar'dan AI bağlantını tamamla."),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick=settings) { Text(ai("Open AI settings","AI ayarlarını aç")) }
                }
            }
            items(messages,key={it.id}) { message ->
                AiChatBubble(message,message.id==state.draftMessageId && state.draft.isNotEmpty(),message.id in state.savedMessages,review)
            }
            if(state.busy) item {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                    Text(state.status,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error?.let { error -> item { Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(error,color=MaterialTheme.colorScheme.error)
                if(state.provider==AiProvider.CHATGPT && state.errorCode=="subscription_sharing_usage_limit_exceeded")
                    Button(onClick=manageUsage) { Text(ai("Manage ChatGPT usage","ChatGPT kullanımını yönet")) }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(state.messages.lastOrNull()?.user==true && state.errorCode!="subscription_sharing_usage_limit_exceeded") TextButton(onClick=retry,enabled=!state.busy) { Text(ai("Retry","Yeniden dene")) }
                    if(state.provider==AiProvider.LOCAL) TextButton(onClick=copyError) { Text(ai("Copy error details","Hata bilgisini kopyala")) }
                }
            } } }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp).padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(6.dp)) {
                    state.jpeg?.let { bytes -> Row(Modifier.padding(start=10.dp,top=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        AiPhoto(bytes,Modifier.size(62.dp));Spacer(Modifier.weight(1f))
                        IconButton(onClick=removePhoto,enabled=!state.busy) { Icon(Icons.Default.Close,ai("Remove photo","Fotoğrafı kaldır")) }
                    } }
                    Row(verticalAlignment=Alignment.Bottom) {
                        Box {
                            IconButton(onClick={attachments=true},enabled=!state.busy) { Icon(Icons.Default.Add,ai("Add attachment","Ek ekle")) }
                            DropdownMenu(attachments,onDismissRequest={attachments=false}) {
                                DropdownMenuItem(text={Text(ai("Take photo","Fotoğraf çek"))},leadingIcon={Icon(painterResource(R.drawable.ic_chat_camera),null)},onClick={attachments=false;camera()})
                                DropdownMenuItem(text={Text(ai("Attach photo","Fotoğraf ekle"))},leadingIcon={Icon(painterResource(R.drawable.ic_chat_photo),null)},onClick={attachments=false;photo()})
                            }
                        }
                        BasicTextField(state.text,edit,enabled=!state.busy,minLines=1,maxLines=5,
                            textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),
                            cursorBrush=SolidColor(MaterialTheme.colorScheme.onSurface),
                            modifier=Modifier.weight(1f).padding(vertical=13.dp).testTag("chat-composer"),
                            decorationBox={ inner -> Box { if(state.text.isEmpty()) Text(ai("Message…","Mesaj yaz…"),color=MaterialTheme.colorScheme.onSurfaceVariant);inner() } })
                        FilledIconButton(onClick=if(state.busy) cancel else send,
                            enabled=state.busy || (ready && (state.text.isNotBlank() || state.jpeg!=null)),modifier=Modifier.size(44.dp)) {
                            Icon(painterResource(if(state.busy) R.drawable.ic_chat_stop else R.drawable.ic_chat_send),
                                if(state.busy) ai("Stop","Durdur") else ai("Send message","Mesaj gönder"),Modifier.size(20.dp))
                        }
                    }
                }
            }
            if(state.provider==AiProvider.CHATGPT && ready) Text(ai("Using your ChatGPT plan · Manage usage","ChatGPT planın kullanılıyor · Kullanımı yönet"),
                Modifier.align(Alignment.CenterHorizontally).clickable(onClick=manageUsage).padding(vertical=3.dp),
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
