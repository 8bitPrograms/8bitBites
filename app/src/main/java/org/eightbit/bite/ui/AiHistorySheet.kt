// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.eightbit.bite.ai.ChatSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AiHistorySheet(sessions:List<ChatSummary>,loading:Boolean,busy:Boolean,error:String?,active:String,
    close:()->Unit,open:(String)->Unit,delete:(String)->Unit) {
    var removing by remember { mutableStateOf<ChatSummary?>(null) }
    ModalBottomSheet(onDismissRequest=close) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.88f).testTag("chat-history"),contentPadding=PaddingValues(20.dp,8.dp,20.dp,40.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(ai("Conversations","Sohbetler"),style=MaterialTheme.typography.headlineSmall)
                    Text(ai("Stored on this phone","Bu telefonda saklanır"),style=MaterialTheme.typography.bodySmall) }
                IconButton(onClick=close) { Icon(Icons.Default.Close,ai("Close","Kapat")) }
            } }
            if(loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(!loading && sessions.isEmpty()) item { Text(ai("Your sent messages will appear here.","Gönderdiğin mesajlar burada görünecek.")) }
            error?.let { text -> item { Text(text,color=MaterialTheme.colorScheme.error) } }
            items(sessions,key={it.id}) { session -> OutlinedCard(onClick={open(session.id)},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    AiProviderIcon(session.provider,Modifier.size(24.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(session.title,maxLines=2,style=MaterialTheme.typography.titleSmall)
                        Text(aiProviderName(session.provider)+" · "+session.count,style=MaterialTheme.typography.bodySmall)
                        Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(session.updated).atZone(ZoneId.systemDefault())),
                            style=MaterialTheme.typography.labelSmall)
                        if(session.id==active) Text(ai("Current conversation","Açık sohbet"),style=MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick={removing=session},enabled=!busy) { Icon(Icons.Default.Delete,ai("Delete conversation","Sohbeti sil")) }
                }
            } }
        }
    }
    removing?.let { session -> AlertDialog(onDismissRequest={removing=null},title={Text(ai("Delete conversation?","Sohbet silinsin mi?"))},
        text={Text(ai("This removes the chat and its photos from this phone. Diary entries remain.","Sohbet ve fotoğrafları bu telefondan silinir. Günlük kayıtları korunur."))},
        confirmButton={TextButton(onClick={delete(session.id);removing=null}) { Text(ai("Delete","Sil")) }},
        dismissButton={TextButton(onClick={removing=null}) { Text(ai("Cancel","İptal")) }}) }
}
