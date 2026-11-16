package org.eightbit.bite.core

import kotlin.test.*

class AiMealsTest {
    @Test fun weightsAreNeverInventedForMissingPortions() {
        val meal = AiMeals.parse("""{"foods":[{"name":"cheese","grams":100,"estimated":false},{"name":"protein powder","grams":null,"note":"1 scoop"}]}""")
        assertEquals(100.0, meal[0].grams); assertFalse(meal[0].estimated)
        assertNull(meal[1].grams); assertEquals("1 scoop", meal[1].note)
    }
    @Test fun photoWeightsAlwaysRequireReviewAsEstimates() {
        assertTrue(AiMeals.parse("""{"foods":[{"name":"rice","grams":150,"estimated":false}]}""", photo=true).single().estimated)
    }
    @Test fun invalidAndExcessivePortionsAreRejected() {
        for (weight in listOf("-1", "0", "100001", "1e999", "\"100\""))
            assertFails { AiMeals.parse("""{"foods":[{"name":"rice","grams":$weight}]}""") }
        assertFails { AiMeals.parse("""{"foods":[{"name":"","grams":100}]}""") }
        assertFails { AiMeals.parse("""{"foods":[${List(21) { "{\"name\":\"rice\"}" }.joinToString(",")}]}""") }
    }
    @Test fun modelNutritionCannotEnterDiaryData() {
        val mention=AiMeals.parse("""{"foods":[{"name":"rice","grams":100,"kcal":99999,"protein":9999}]}""").single()
        assertEquals(AiFoodMention("rice",100.0,true,""), mention)
    }
    @Test fun endpointsCannotContainCredentialsQueriesOrPlainHttp() {
        assertEquals("https://api.example.com/v1/chat/completions",AiMeals.endpoint("https://api.example.com/v1/").toString())
        for (base in listOf("http://example.com", "https://secret@example.com", "https://example.com?key=abc", "https://example.com/#test"))
            assertFails { AiMeals.endpoint(base) }
    }
    @Test fun requestEscapesUserTextAndCarriesOnlyExplicitInput() {
        val request=AiMeals.request("my-model", "100 g cheese\n\"hello\"", byteArrayOf(1,2,3))
        assertContains(request, "data:image/jpeg;base64,AQID")
        val response="""{"choices":[{"message":{"content":"{\"foods\":[]}"}}]}"""
        assertTrue(AiMeals.parse(AiMeals.response(response)).isEmpty())
    }
}
