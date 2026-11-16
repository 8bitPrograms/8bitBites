package org.eightbit.bite.core

import com.google.gson.JsonParser
import kotlin.test.*

class AiChatTest {
    @Test fun estimatesCarryPerHundredGramNutritionAndKeepUnknownValuesUnknown() {
        val food=AiChat.parse("""{"reply":"Tahmini öğünün hazır","foods":[{"name":"ekmek","grams":80,"estimated":true,"note":"2 dilim","nutrition_per_100g":{"kcal":250,"protein":9,"carbs":45,"fat":3,"sugar":null}}]}""",photo=true).foods.single()
        assertTrue(food.estimated)
        assertEquals(200.0,food.nutrition!!.portion(food.grams!!).kcal)
        assertNull(food.nutrition!!.sugar)
        val roundTrip=AiChat.parse("""{"foods":[${AiChat.foodJson(food)}]}""").foods.single()
        assertEquals(food,roundTrip)
    }
    @Test fun malformedOrImpossibleEstimatesCannotReachDiaryApproval() {
        for(value in listOf("-1","1001","\"250\"","1e999")) {
            assertFailsWith<IllegalArgumentException> { AiChat.parse("""{"foods":[{"name":"food","grams":100,"nutrition_per_100g":{"kcal":$value}}]}""") }
        }
        assertFailsWith<IllegalArgumentException> { AiChat.parse("""{"foods":[{"name":"food","grams":100,"nutrition_per_100g":{"protein":101}}]}""") }
    }
    @Test fun repliesAndRevisedMealListsRemainSeparateFromNutritionFacts() {
        val reply=AiChat.parse("""{"reply":"Peyniri 150 g olarak düzelttim; kaydetmeden önce kontrol et.","foods":[{"name":"peynir","grams":150,"estimated":false,"note":""}]}""")
        assertEquals(150.0,reply.foods.single().grams)
        assertTrue(reply.reply.contains("150 g"))
        assertTrue(AiChat.parse("""{"reply":"Miktarı kaç gramdı?","foods":[]}""").foods.isEmpty())
        assertNull(AiChat.parse("""{"reply":"Ölçeğin ağırlığı ne?","foods":[{"name":"toz","grams":null,"estimated":false,"note":"1 ölçek"}]}""").foods.single().grams)
    }
    @Test fun responsesPayloadUsesTheDocumentedPlanSharingContract() {
        val body=JsonParser.parseString(AiChat.responsesRequest("selected-account-model",listOf(
            AiChatTurn("user","100 g cheese"),AiChatTurn("assistant","{\"reply\":\"OK\",\"foods\":[]}"),AiChatTurn("user","make it 150 g",byteArrayOf(1,2,3))))).asJsonObject
        assertFalse(body.get("store").asBoolean);assertTrue(body.get("stream").asBoolean)
        assertEquals("selected-account-model",body.get("model").asString)
        assertTrue(body.get("input").isJsonArray)
        assertTrue(body.getAsJsonArray("input").last().asJsonObject.getAsJsonArray("content").any { it.asJsonObject.get("type").asString=="input_image" })
        for(field in listOf("temperature","max_output_tokens","previous_response_id","conversation","background","metadata")) assertFalse(body.has(field))
        assertFalse(body.getAsJsonArray("input").any { it.asJsonObject.get("role").asString=="system" })
    }
    @Test fun interruptedOrUsageLimitedStreamsNeverBecomeSuccessfulReplies() {
        val partial=ChatGptStream()
        partial.event("""{"type":"response.output_text.delta","delta":"partial"}""")
        assertFailsWith<SiwcException> { partial.result() }
        val failure=assertFailsWith<SiwcException> { partial.event("""{"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}}""") }
        assertEquals("subscription_sharing_usage_limit_exceeded",failure.code)
        assertFalse(partial.completed)
    }
    @Test fun completedResponseDoesNotDuplicateDeltasAndCanContainPlainChat() {
        val stream=ChatGptStream()
        stream.event("""{"type":"response.output_text.delta","delta":"hello"}""")
        stream.event("""{"type":"response.completed","response":{"output":[{"content":[{"type":"output_text","text":"hello"}]}]}}""")
        assertEquals("hello",stream.result())
    }
    @Test fun historyKeepsTheLatestCorrectionAndDropsWholeOldTurns() {
        val turns=listOf(AiChatTurn("user","a".repeat(4000)),AiChatTurn("assistant","old answer"),AiChatTurn("user","150 g instead"))
        val context=AiChat.context(turns,30)
        assertEquals("150 g instead",context.last().text)
        assertEquals("user",context.first().role)
        assertFalse(context.any { it.text.length==4000 })
        assertTrue(AiChat.localPrompt(turns).endsWith("USER: 150 g instead"))
    }
}
