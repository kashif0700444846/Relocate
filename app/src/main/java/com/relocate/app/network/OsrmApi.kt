// [Relocate] [OsrmApi.kt] - OSRM Route API
// Fetches multi-waypoint routes from the OSRM public API.

package com.relocate.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LatLng(
    val lat: Double,
    val lng: Double
)

object OsrmApi {
    private const val TAG = "OsrmApi"
    private const val BASE_URL = "https://router.project-osrm.org"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Fetch a route between multiple waypoints.
     * @param waypoints Ordered list of waypoints (min 2)
     * @param mode "driving" or "foot"
     * @return List of route points, or null on failure
     */
    suspend fun getRoute(waypoints: List<LatLng>, mode: String = "driving"): List<LatLng>? {
        if (waypoints.size < 2) return null

        return withContext(Dispatchers.IO) {
            try {
                val osrmMode = if (mode == "walking") "foot" else "driving"
                val coords = waypoints.joinToString(";") { "${it.lng},${it.lat}" }
                val url = "$BASE_URL/route/v1/$osrmMode/$coords?overview=full&geometries=geojson"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RelocateAndroid/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    android.util.Log.e(TAG, "[Route] [ERROR] OSRM returned ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null

                // Parse the GeoJSON response
                val data: Map<String, Any> = gson.fromJson(body, object : TypeToken<Map<String, Any>>() {}.type)
                val routes = data["routes"] as? List<*> ?: return@withContext null
                if (routes.isEmpty()) return@withContext null

                val route = routes[0] as? Map<*, *> ?: return@withContext null
                val geometry = route["geometry"] as? Map<*, *> ?: return@withContext null
                val coordinates = geometry["coordinates"] as? List<*> ?: return@withContext null

                coordinates.mapNotNull { coord ->
                    val pair = coord as? List<*> ?: return@mapNotNull null
                    val lng = (pair[0] as? Number)?.toDouble() ?: return@mapNotNull null
                    val lat = (pair[1] as? Number)?.toDouble() ?: return@mapNotNull null
                    LatLng(lat, lng)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "[Route] [ERROR] ${e.message}")
                null
            }
        }
    }
}
