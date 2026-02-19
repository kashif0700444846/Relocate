// [Relocate] [PresetStore.kt] - Quick Presets CRUD
// Stores preset locations (name + lat/lng) in SharedPreferences as JSON.

package com.relocate.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Preset(
    val name: String,
    val lat: Double,
    val lng: Double
)

class PresetStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "relocate_presets"
        private const val KEY_ALL = "all_presets"

        private val DEFAULT_PRESETS = listOf(
            Preset("New York 🗽", 40.7128, -74.0060),
            Preset("London 🎡", 51.5074, -0.1278),
            Preset("Tokyo 🗼", 35.6762, 139.6503),
            Preset("Paris 🗼", 48.8566, 2.3522)
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _presets = MutableStateFlow(loadFromDisk())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    private fun loadFromDisk(): List<Preset> {
        val json = prefs.getString(KEY_ALL, null)
        if (json == null) {
            // First launch: seed with defaults
            saveToDisk(DEFAULT_PRESETS)
            return DEFAULT_PRESETS
        }
        return try {
            val type = object : TypeToken<List<Preset>>() {}.type
            gson.fromJson(json, type) ?: DEFAULT_PRESETS
        } catch (e: Exception) {
            DEFAULT_PRESETS
        }
    }

    private fun saveToDisk(list: List<Preset>) {
        prefs.edit().putString(KEY_ALL, gson.toJson(list)).apply()
    }

    fun add(preset: Preset) {
        val updated = _presets.value.toMutableList().apply { add(preset) }
        saveToDisk(updated)
        _presets.value = updated
    }

    fun delete(index: Int) {
        val updated = _presets.value.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            saveToDisk(updated)
            _presets.value = updated
        }
    }

    fun getAll(): List<Preset> = _presets.value
}
