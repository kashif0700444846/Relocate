// [Relocate] [RecentStore.kt] - Recent Locations History
// Stores the last N applied locations with deduplication.

package com.relocate.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecentLocation(
    val lat: Double,
    val lng: Double,
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)

class RecentStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "relocate_recent"
        private const val KEY_ALL = "recent_locations"
        private const val MAX_RECENT = 8
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _locations = MutableStateFlow(loadFromDisk())
    val locations: StateFlow<List<RecentLocation>> = _locations.asStateFlow()

    private fun loadFromDisk(): List<RecentLocation> {
        val json = prefs.getString(KEY_ALL, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<RecentLocation>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToDisk(list: List<RecentLocation>) {
        prefs.edit().putString(KEY_ALL, gson.toJson(list)).apply()
    }

    fun add(location: RecentLocation) {
        val newKey = "%.4f,%.4f".format(location.lat, location.lng)
        val updated = _locations.value
            .filter { "%.4f,%.4f".format(it.lat, it.lng) != newKey }
            .toMutableList()
            .apply { add(0, location) }
            .take(MAX_RECENT)

        saveToDisk(updated)
        _locations.value = updated
    }

    fun remove(index: Int) {
        val updated = _locations.value.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            saveToDisk(updated)
            _locations.value = updated
        }
    }

    fun clear() {
        saveToDisk(emptyList())
        _locations.value = emptyList()
    }
}
