package org.eightbit.bite.ai

import android.app.Application
import org.eightbit.bite.core.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class ChatHistoryStoreTest {
    private lateinit var store:ChatHistoryStore
    private val ids=mutableListOf<String>()
    @Before fun setup() { store=ChatHistoryStore(RuntimeEnvironment.getApplication()) }
    @After fun cleanup() { ids.forEach { store.delete(it) } }
    private fun session(messages:List<AiChatMessage>)=ChatSession(UUID.randomUUID().toString().also { ids.add(it) },1000,2000,AiProvider.CHATGPT,
        accountId="account-a",model="selected-model",messages=messages)
    @Test fun aNewStoreRestoresMessagesDraftEditsAndTheAccountBinding() {
        val food=AiFoodMention("Cheese",150.0,true,"",Nutrients(300.0,20.0))
        val wire="""{"reply":"Estimated meal","foods":[${AiChat.foodJson(food)}]}"""
        val record=session(listOf(AiChatMessage(id="user",user=true,text="my meal"),AiChatMessage(id="answer",user=false,text="Estimated meal",wireText=wire,foods=listOf(food))))
            .copy(draftMessageId="answer",reviews=listOf(ChatReviewState("answer:0","180",true,"OTHER",food)),input="make it 180 g")
        store.save(record)
        val restored=ChatHistoryStore(RuntimeEnvironment.getApplication()).load(record.id)
        assertEquals("account-a",restored.accountId);assertEquals("make it 180 g",restored.input)
        assertEquals(300.0,restored.messages.last().foods.single().nutrition!!.kcal!!,0.001)
        assertEquals("180",restored.reviews.single().grams);assertEquals("answer:0",restored.reviews.single().id)
    }
    @Test fun photosRemainAvailableWithoutLoadingThemAllAndDeleteRemovesTheFiles() {
        val bytes=byteArrayOf(1,2,3,4)
        val record=session(listOf(AiChatMessage(id="photo",user=true,text="",photo=bytes)))
        store.save(record)
        val restored=store.load(record.id).messages.single()
        assertNull(restored.photo);assertArrayEquals(bytes,File(restored.photoPath!!).readBytes())
        store.delete(record.id)
        assertFalse(File(restored.photoPath!!).exists());assertTrue(store.list().none { it.id==record.id })
        ids.remove(record.id)
    }
    @Test fun approvalMarkersSurviveRestartAndNewConversationsStaySeparate() {
        val first=session(listOf(AiChatMessage(user=true,text="Breakfast"))).copy(saved=setOf("approved-answer"))
        val second=session(listOf(AiChatMessage(user=true,text="Dinner"))).copy(updated=3000,accountId="account-b")
        store.save(first);store.save(second)
        assertEquals(second.id,store.list().first().id)
        assertEquals(setOf("approved-answer"),store.load(first.id).saved)
        assertEquals("account-a",store.load(first.id).accountId);assertEquals("account-b",store.load(second.id).accountId)
    }
    @Test fun aDeletedReviewItemDoesNotReturnAndUnsafePathsAreRejected() {
        val record=session(listOf(AiChatMessage(user=true,text="meal"))).copy(draftMessageId="answer",reviews=emptyList())
        store.save(record);assertTrue(store.load(record.id).reviews.isEmpty())
        try { store.load("../outside");fail("Unsafe path accepted") } catch(_:IllegalArgumentException) { }
        assertEquals(1,store.list().count { it.id==record.id })
    }
}
