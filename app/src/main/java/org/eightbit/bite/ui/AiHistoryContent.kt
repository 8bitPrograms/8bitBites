// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.eightbit.bite.ai.ChatSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable internal fun AiHistoryContent(sessions:List<ChatSummary>,loading:Boolean,busy:Boolean,error:String?,active:String,
    close:()->Unit,open:(String)->Unit,delete:(String)->Unit) {
    var removing by remember { mutableStateOf<ChatSummary?>(null) }
    Column(Modifier.fillMaxSize().testTag("chat-history")) {
        Row(Modifier.padding(start=20.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(ai("Conversations","Sohbetler"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            IconButton(onClick=close) { Icon(Icons.Default.Close,ai("Close","Kapat")) }
        }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            if(!loading && sessions.isEmpty()) item {
                Text(ai("Your sent messages will appear here.","Gönderdiğin mesajlar burada görünecek."),
                    Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { text -> item { Text(text,Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error) } }
            items(sessions,key={it.id}) { session ->
                var options by remember { mutableStateOf(false) }
                Surface(onClick={open(session.id)},enabled=!busy,shape=MaterialTheme.shapes.medium,
                    color=if(session.id==active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                    modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start=12.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(session.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                            Text(aiProviderName(session.provider)+" · "+
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(Instant.ofEpochMilli(session.updated).atZone(ZoneId.systemDefault())),
                                maxLines=1,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick={options=true},enabled=!busy) { Icon(Icons.Default.MoreVert,ai("Conversation options","Sohbet seçenekleri")) }
                            DropdownMenu(options,onDismissRequest={options=false}) {
                                DropdownMenuItem(text={Text(ai("Delete conversation","Sohbeti sil"))},onClick={options=false;removing=session})
                            }
                        }
                    }
                }
            }
        }
        Text(ai("Stored on this phone","Bu telefonda saklanır"),Modifier.padding(start=24.dp,bottom=12.dp,top=8.dp),
            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    removing?.let { session -> AlertDialog(onDismissRequest={removing=null},title={Text(ai("Delete conversation?","Sohbet silinsin mi?"))},
        text={Text(ai("This removes the chat and its photos from this phone. Diary entries remain.","Sohbet ve fotoğrafları bu telefondan silinir. Günlük kayıtları korunur."))},
        confirmButton={TextButton(onClick={delete(session.id);removing=null}) { Text(ai("Delete","Sil")) }},
        dismissButton={TextButton(onClick={removing=null}) { Text(ai("Cancel","İptal")) }}) }
}
