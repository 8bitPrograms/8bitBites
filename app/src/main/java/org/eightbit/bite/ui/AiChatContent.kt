// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.eightbit.bite.R
import org.eightbit.bite.core.AiProvider

@Composable internal fun AiPhoto(bytes:ByteArray,modifier:Modifier=Modifier) {
    val bitmap=remember(bytes) { BitmapFactory.decodeByteArray(bytes,0,bytes.size) }
    if(bitmap!=null) Image(bitmap.asImageBitmap(),ai("Meal photo","Öğün fotoğrafı"),modifier.heightIn(max=190.dp))
}

@Composable internal fun AiChatBubble(message:AiChatMessage,provider:AiProvider,review:Boolean,saved:Boolean,onReview:()->Unit) {
    Box(Modifier.fillMaxWidth(),contentAlignment=if(message.user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(Modifier.widthIn(max=330.dp).testTag(if(message.user) "chat-user" else "chat-assistant"),
            shape=RoundedCornerShape(topStart=if(message.user) 20.dp else 4.dp,topEnd=if(message.user) 4.dp else 20.dp,bottomEnd=20.dp,bottomStart=20.dp),
            color=if(message.user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                if(!message.user) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    AiProviderIcon(provider,Modifier.size(18.dp));Text(aiProviderName(provider),style=MaterialTheme.typography.labelMedium)
                }
                message.photo?.let { AiPhoto(it,Modifier.fillMaxWidth()) }
                if(message.text.isNotBlank()) Text(message.text,style=MaterialTheme.typography.bodyLarge)
                if(message.foods.isNotEmpty()) {
                    message.foods.take(4).forEach { food -> Text(food.name+food.grams?.let { " · ${it.display()} g" }.orEmpty(),style=MaterialTheme.typography.bodySmall) }
                    if(message.foods.size>4) Text("+${message.foods.size-4}",style=MaterialTheme.typography.labelSmall)
                }
                if(review) FilledTonalButton(onClick=onReview) { Text(ai("Review meal","Öğünü incele")) }
                if(saved) Text(ai("Saved to diary","Günlüğe kaydedildi"),style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable internal fun AiChatContent(modifier:Modifier,state:AiState,providerDetail:String,ready:Boolean,
    edit:(String)->Unit,send:()->Unit,cancel:()->Unit,newChat:()->Unit,photo:()->Unit,camera:()->Unit,removePhoto:()->Unit,
    review:()->Unit,retry:()->Unit,settings:()->Unit,copyError:()->Unit,manageUsage:()->Unit={}) {
    val list=rememberLazyListState()
    val example=ai("100 g cheese and 2 slices of bread","100 g peynir ve 2 dilim ekmek")
    LaunchedEffect(state.messages.size,state.busy,state.error) { if(state.messages.isNotEmpty() || state.busy || state.error!=null) list.animateScrollToItem(state.messages.size+4) }
    Column(modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            AiProviderIcon(state.provider,Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(aiProviderName(state.provider),style=MaterialTheme.typography.titleMedium)
                Text(providerDetail,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=newChat,enabled=!state.busy) { Icon(Icons.Default.Add,ai("New chat","Yeni sohbet")) }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ai-conversation"),state=list,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(state.messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(ai("What did you eat today?","Bugün ne yedin?"),style=MaterialTheme.typography.headlineMedium)
                    Text(ai("Describe your meal or send a photo. You can clarify the amounts in the next message.",
                        "Öğününü yaz veya fotoğraf gönder. Sonraki mesajda miktarları düzeltebilirsin."),style=MaterialTheme.typography.bodyLarge)
                    if(ready) SuggestionChip(onClick={edit(example)},label={Text(ai("Try a meal description","Bir öğün tarifi dene"))})
                }
            }
            if(!ready) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(ai("Complete your AI connection in Settings to start chatting.","Sohbete başlamak için Ayarlar'dan AI bağlantını tamamla."))
                TextButton(onClick=settings) { Text(ai("Open AI settings","AI ayarlarını aç")) }
            } } }
            items(state.messages,key={it.id}) { message ->
                AiChatBubble(message,state.provider,message.id==state.draftMessageId && state.draft.isNotEmpty(),message.id in state.savedMessages,review)
            }
            if(state.busy) item { Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                    Text(state.status,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick=cancel) { Text(ai("Stop","Durdur")) }
                }
            } }
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
        Surface(tonalElevation=2.dp) { Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(state.provider==AiProvider.CHATGPT && ready) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(ai("Using ChatGPT plan","ChatGPT planın kullanılıyor"),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)
                TextButton(onClick=manageUsage) { Text(ai("Manage usage","Kullanımı yönet"),style=MaterialTheme.typography.labelSmall) }
            }
            state.jpeg?.let { bytes -> Row(verticalAlignment=Alignment.CenterVertically) {
                AiPhoto(bytes,Modifier.size(62.dp));Spacer(Modifier.weight(1f))
                IconButton(onClick=removePhoto,enabled=!state.busy) { Icon(Icons.Default.Close,ai("Remove photo","Fotoğrafı kaldır")) }
            } }
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.text,edit,placeholder={Text(ai("Message…","Mesaj yaz…"))},enabled=!state.busy,
                    minLines=1,maxLines=5,shape=RoundedCornerShape(24.dp),modifier=Modifier.weight(1f).testTag("chat-composer"))
                FilledIconButton(onClick=send,enabled=ready && !state.busy && (state.text.isNotBlank() || state.jpeg!=null),modifier=Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_chat_send),ai("Send message","Mesaj gönder"))
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=camera,enabled=!state.busy) { Icon(painterResource(R.drawable.ic_chat_camera),ai("Take photo","Fotoğraf çek")) }
                IconButton(onClick=photo,enabled=!state.busy) { Icon(painterResource(R.drawable.ic_chat_photo),ai("Attach photo","Fotoğraf ekle")) }
                Spacer(Modifier.weight(1f))
                Text(ai("Session-only chat","Sohbet bu oturumda tutulur"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
    }
}
