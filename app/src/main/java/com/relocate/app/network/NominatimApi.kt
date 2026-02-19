// [Relocate] [NominatimApi.kt] - Nominatim Geocoding API
// Forward search (autocomplete) and reverse geocoding via OpenStreetMap Nominatim.

package com.relocate.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SearchResult(
    val lat: Double,
    val lng: Double,
    val name: String,
    val fullName: String
)

object NominatimApi {
    private const val TAG = "NominatimApi"
    private const val BASE_URL = "https://nominatim.openstreetmap.org"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Forward search — returns up to [limit] results for a query string.
     * Used for autocomplete in the search bar.
     */
    suspend fun search(query: String, limit: Int = 6): List<SearchResult> {
        if (query.isBlank() || query.length < 2) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                        "&format=json&limit=$limit&addressdetails=0"

                val request = Request.Builder()
                    .url(url)
                    .header("Accept-Language", "en")
                    .header("User-Agent", "RelocateAndroid/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string() ?: return@withContext emptyList()

                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val data: List<Map<String, Any>> = gson.fromJson(body, type)

                data.mapNotNull { item ->
                    try {
                        val lat = (item["lat"] as? String)?.toDouble() ?: return@mapNotNull null
                        val lng = (item["lon"] as? String)?.toDouble() ?: return@mapNotNull null
                        val displayName = item["display_name"] as? String ?: return@mapNotNull null
                        val parts = displayName.split(", ")
                        val shortName = parts.take(2).joinToString(", ")

                        SearchResult(
                            lat = lat,
                            lng = lng,
                            name = shortName,
                            fullName = displayName
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[Search] [ERROR] ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Reverse geocode — returns a human-readable address for coordinates.
     */
    suspend fun reverse(lat: Double, lng: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/reverse?lat=$lat&lon=$lng&format=json&zoom=16"

                val request = Request.Builder()
                    .url(url)
                    .header("Accept-Language", "en")
                    .header("User-Agent", "RelocateAndroid/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val data: Map<String, Any> = gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)

                val displayName = data["display_name"] as? String ?: return@withContext null
                displayName.split(", ").take(2).joinToString(", ")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[Reverse] [ERROR] ${e.message}")
                null
            }
        }
    }
}
