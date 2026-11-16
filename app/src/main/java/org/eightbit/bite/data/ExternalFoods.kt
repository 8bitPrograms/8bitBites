package org.eightbit.bite.data

import org.eightbit.bite.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

enum class FoodProvider { OPEN_FOOD_FACTS, USDA }

class ExternalFoods {
    private var lastOffRequest=0L
    private fun request(url: String, key: String? = null): JSONObject {
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout=15000; connection.readTimeout=20000; connection.instanceFollowRedirects=false
        connection.setRequestProperty("User-Agent", "8bitBites/${BuildConfig.VERSION_NAME} (https://github.com/${BuildConfig.UPDATE_REPOSITORY})")
        if(key!=null) connection.setRequestProperty("X-Api-Key",key)
        try {
            require(connection.responseCode==200) { "Food provider request failed" }
            return JSONObject(connection.inputStream.use { it.readBytesBounded(3*1024*1024) }.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    suspend fun search(query: String, provider: FoodProvider, key: String = ""): List<Food> = withContext(Dispatchers.IO) {
        require(query.trim().length>=2) { "Enter at least two characters" }
        fun number(row: JSONObject, field: String): Double? = if(!row.has(field)||row.isNull(field)) null
            else row.optDouble(field).takeIf { it.isFinite() && it>=0 }
        if(provider==FoodProvider.OPEN_FOOD_FACTS) {
            synchronized(this@ExternalFoods) {
                require(System.currentTimeMillis()-lastOffRequest>=6500) { "Please wait a few seconds before searching again" }
                lastOffRequest=System.currentTimeMillis()
            }
            val barcode=query.matches(Regex("[0-9]{8,14}"))
            val data=if(barcode) request("https://world.openfoodfacts.org/api/v2/product/$query.json")
                else request("https://world.openfoodfacts.org/api/v2/search?search_terms=${URLEncoder.encode(query,"UTF-8")}&page_size=20&json=1")
            val products=if(barcode) listOfNotNull(data.optJSONObject("product")) else data.optJSONArray("products")?.let { a -> (0 until a.length()).map(a::getJSONObject) }.orEmpty()
            products.mapNotNull { product ->
                val categories=product.optString("categories_tags").lowercase()
                // This gram-based development build deliberately excludes volume-based labels.
                if(product.optString("nutrition_data_per")!="100g" || listOf("beverages","milks","oils").any { it in categories } ||
                    product.optString("serving_size").contains(Regex("\\bml\\b",RegexOption.IGNORE_CASE))) return@mapNotNull null
                val n=product.optJSONObject("nutriments") ?: return@mapNotNull null
                val code=product.optString("code"); val name=product.optString("product_name").trim()
                if(code.isBlank()||name.isBlank()) return@mapNotNull null
                Food(id="off:$code",name=name,brand=product.optString("brands"),source="Open Food Facts · ODbL",
                    sourceUrl="https://world.openfoodfacts.org/product/$code",kcal=number(n,"energy-kcal_100g"),
                    protein=number(n,"proteins_100g"),carbs=number(n,"carbohydrates_100g"),fat=number(n,"fat_100g"),
                    fiber=number(n,"fiber_100g"),sugar=number(n,"sugars_100g"),sodium=number(n,"sodium_100g"))
            }
        } else {
            require(key.isNotBlank()) { "Enter your USDA API key" }
            val data=request("https://api.nal.usda.gov/fdc/v1/foods/search?query=${URLEncoder.encode(query,"UTF-8")}&pageSize=20&dataType=Foundation,SR%20Legacy",key)
            val foods=data.optJSONArray("foods")
            (0 until (foods?.length()?:0)).map { index ->
                val food=foods!!.getJSONObject(index); val nutrients=food.optJSONArray("foodNutrients")
                fun nutrient(id: Int): Double? = (0 until (nutrients?.length()?:0)).map { nutrients!!.getJSONObject(it) }
                    .firstOrNull { it.optInt("nutrientId")==id }?.let { number(it,"value") }
                val id=food.getLong("fdcId")
                Food(id="usda:$id",name=food.getString("description"),source="USDA FoodData Central · CC0",
                    sourceUrl="https://fdc.nal.usda.gov/food-details/$id/nutrients",kcal=nutrient(1008),protein=nutrient(1003),
                    carbs=nutrient(1005),fat=nutrient(1004),fiber=nutrient(1079),sugar=nutrient(2000),sodium=nutrient(1093)?.div(1000))
            }
        }
    }
}
