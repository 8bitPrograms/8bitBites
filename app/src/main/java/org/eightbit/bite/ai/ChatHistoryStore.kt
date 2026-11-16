// SPDX-License-Identifier: GPL-3.0-or-later
package org.eightbit.bite.ai

import android.content.Context
import android.util.AtomicFile
import org.eightbit.bite.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

data class AiChatMessage(val id:String=UUID.randomUUID().toString(),val user:Boolean,val text:String,val photo:ByteArray?=null,
    val wireText:String=text,val foods:List<AiFoodMention> = emptyList(),val photoPath:String?=null,val acknowledges:String="")
data class ChatReviewState(val id:String,val grams:String,val supplement:Boolean=false,val kind:String="OTHER",val food:AiFoodMention?=null)
data class ChatSession(val id:String,val created:Long,val updated:Long,val provider:AiProvider,val accountId:String="",
    val endpoint:String="",val model:String="",val messages:List<AiChatMessage> = emptyList(),val draftMessageId:String="",
    val reviews:List<ChatReviewState> = emptyList(),val saved:Set<String> = emptySet(),val input:String="") {
    val title get()=messages.firstOrNull { it.user && it.text.isNotBlank() }?.text?.replace('\n',' ')?.take(72) ?: "Photo conversation"
}
data class ChatSummary(val id:String,val title:String,val provider:AiProvider,val model:String,val updated:Long,val count:Int)

/** Private local history. Photos are separate files so opening history does not load all images into memory. */
class ChatHistoryStore(context:Context) {
    private val root=File(context.filesDir,"ai-chats").apply { mkdirs() }
    private fun safe(id:String):String=id.also { require(Regex("[A-Za-z0-9_-]{1,100}").matches(it)) }
    private fun record(id:String)=AtomicFile(File(root,safe(id)+".json"))
    private fun photo(session:String,message:String)=File(File(root,safe(session)),safe(message)+".jpg")
    private fun write(file:AtomicFile,bytes:ByteArray) {
        file.baseFile.parentFile?.mkdirs()
        val stream=file.startWrite()
        try { stream.write(bytes);file.finishWrite(stream) }
        catch(error:Exception) { file.failWrite(stream);throw error }
    }
    private fun document(id:String):JSONObject {
        val file=record(id)
        require(file.baseFile.length()<=24*1024*1024) { "Conversation is too large" }
        return JSONObject(file.openRead().bufferedReader().use { it.readText() }).also {
            require(it.getInt("format")==1 && it.getString("id")==id)
        }
    }
    @Synchronized fun save(session:ChatSession):ChatSession {
        safe(session.id);require(session.messages.size<=600 && session.input.length<=4000)
        val messages=session.messages.map { message ->
            safe(message.id);require(message.text.length<=12000 && message.wireText.length<=32000)
            val file=photo(session.id,message.id)
            message.photo?.let { bytes -> require(bytes.size<=2*1024*1024);write(AtomicFile(file),bytes) }
            message.copy(photo=null,photoPath=file.takeIf { it.isFile }?.absolutePath)
        }
        val json=JSONObject().put("format",1).put("id",session.id).put("created",session.created).put("updated",session.updated)
            .put("provider",session.provider.name).put("accountId",session.accountId).put("endpoint",session.endpoint)
            .put("model",session.model).put("title",session.title).put("input",session.input).put("draftMessageId",session.draftMessageId)
            .put("saved",JSONArray(session.saved.toList())).put("reviews",JSONArray().apply { session.reviews.forEach {
                put(JSONObject().put("id",it.id).put("grams",it.grams).put("supplement",it.supplement).put("kind",it.kind)
                    .put("food",it.food?.let { food -> JSONObject(AiChat.foodJson(food).toString()) } ?: JSONObject.NULL))
            } }).put("messages",JSONArray().apply { messages.forEach { message ->
                put(JSONObject().put("id",message.id).put("user",message.user).put("text",message.text).put("wire",message.wireText)
                    .put("photo",message.photoPath!=null).put("acknowledges",message.acknowledges))
            } })
        val bytes=json.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=24*1024*1024)
        write(record(session.id),bytes)
        return session.copy(messages=messages)
    }
    @Synchronized fun load(id:String):ChatSession {
        val doc=document(safe(id));val rows=doc.getJSONArray("messages");require(rows.length()<=600)
        val saved=doc.optJSONArray("saved")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty()
        val messages=(0 until rows.length()).map { index -> val row=rows.getJSONObject(index)
            val messageId=safe(row.getString("id"));val user=row.getBoolean("user")
            val text=row.getString("text");val wire=row.optString("wire",text)
            require(text.length<=12000 && wire.length<=32000)
            val foods=if(user) emptyList() else runCatching { AiChat.parse(wire).foods }.getOrDefault(emptyList())
            // Older app-generated receipts had no link. Only recognize them after a locally approved proposal.
            val previous=if(index>0) rows.getJSONObject(index-1).optString("id") else ""
            val legacyReceipt=!user && previous in saved && text in setOf("Meal added to your diary.","Öğün günlüğüne eklendi.") &&
                runCatching { JSONObject(wire).optBoolean("saved") }.getOrDefault(false)
            val acknowledges=row.optString("acknowledges").ifBlank { if(legacyReceipt) previous else "" }
            AiChatMessage(messageId,user,text,wireText=wire,foods=foods,acknowledges=acknowledges,
                photoPath=photo(id,messageId).takeIf { row.optBoolean("photo") && it.isFile && it.length()<=2*1024*1024 }?.absolutePath)
        }
        val reviews=doc.optJSONArray("reviews")?.let { a -> require(a.length()<=20);(0 until a.length()).map {
            a.getJSONObject(it).let { row ->
                val kind=row.optString("kind","OTHER");SupplementKind.valueOf(kind)
                val food=row.optJSONObject("food")?.let { value -> AiChat.parse(JSONObject().put("foods",JSONArray().put(value)).toString()).foods.single() }
                ChatReviewState(row.getString("id"),row.optString("grams").take(30),row.optBoolean("supplement"),kind,food)
            }
        } }.orEmpty()
        return ChatSession(id,doc.getLong("created"),doc.getLong("updated"),AiProvider.valueOf(doc.getString("provider")),
            doc.optString("accountId"),doc.optString("endpoint"),doc.optString("model"),messages,doc.optString("draftMessageId"),reviews,saved,doc.optString("input").take(4000))
    }
    @Synchronized fun list():List<ChatSummary> = root.listFiles().orEmpty().filter { it.extension=="json" }.mapNotNull { file ->
        try { val doc=document(file.nameWithoutExtension)
            ChatSummary(doc.getString("id"),doc.optString("title","Conversation").take(72),AiProvider.valueOf(doc.getString("provider")),
                doc.optString("model"),doc.getLong("updated"),doc.getJSONArray("messages").length())
        } catch(_:Exception) { null }
    }.sortedByDescending { it.updated }
    @Synchronized fun delete(id:String) {
        safe(id);record(id).delete()
        val folder=File(root,id)
        check(folder.canonicalFile.parentFile==root.canonicalFile)
        folder.listFiles().orEmpty().filter { it.isFile }.forEach { if(!it.delete()) throw IOException("Could not remove conversation photo") }
        if(folder.exists() && !folder.delete()) throw IOException("Could not remove conversation")
    }
}
