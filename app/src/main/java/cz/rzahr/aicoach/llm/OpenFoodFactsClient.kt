package cz.rzahr.aicoach.llm

import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class OffNutrition(
    val productName: String,
    val caloriesPer100g: Int?,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?
)

@Serializable
private data class OffSearchResponse(
    val products: List<OffProduct> = emptyList()
)

@Serializable
private data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_cs") val productNameCs: String? = null,
    val nutriments: OffNutriments? = null
)

@Serializable
private data class OffNutriments(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null
)

@Singleton
class OpenFoodFactsClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json
) {

    suspend fun search(query: String): OffNutrition? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url =
            "https://world.openfoodfacts.org/cgi/search.pl" +
                "?search_terms=$encoded&search_simple=1&action=process&json=1" +
                "&fields=product_name,product_name_cs,nutriments&page_size=10"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "AiCoach/1.0 (android)")
            .get()
            .build()

        try {
            val response = http.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString(OffSearchResponse.serializer(), body)
                parsed.products
                    .filter { it.nutriments?.energyKcal100g != null && it.nutriments.energyKcal100g > 0 }
                    .firstNotNullOfOrNull { product ->
                        val nutriments = product.nutriments ?: return@firstNotNullOfOrNull null
                        OffNutrition(
                            productName = (product.productNameCs ?: product.productName ?: "").trim(),
                            caloriesPer100g = nutriments.energyKcal100g?.toInt(),
                            proteinPer100g = nutriments.proteins100g,
                            carbsPer100g = nutriments.carbohydrates100g,
                            fatPer100g = nutriments.fat100g
                        )
                    }
            }
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
