// [Relocate] [PreferencesManager.kt] - SharedPreferences / DataStore Wrapper
// Stores all user preferences: spoofing state, coordinates, theme, mode.

package com.relocate.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "relocate_prefs")

enum class SpoofMode { MOCK, ROOT }

class PreferencesManager(private val context: Context) {

    companion object {
        private val SPOOF_ENABLED = booleanPreferencesKey("spoof_enabled")
        private val LATITUDE = doublePreferencesKey("latitude")
        private val LONGITUDE = doublePreferencesKey("longitude")
        private val ACCURACY = floatPreferencesKey("accuracy")
        private val SPOOF_MODE = stringPreferencesKey("spoof_mode")
        private val DARK_THEME = booleanPreferencesKey("dark_theme")
        private val PRESET_NAME = stringPreferencesKey("preset_name")
        private val SHOW_COORDS = booleanPreferencesKey("show_coords")
        private val SHOW_PRESETS = booleanPreferencesKey("show_presets")
        private val SHOW_RECENT = booleanPreferencesKey("show_recent")
        private val SHOW_ROUTE_SIM = booleanPreferencesKey("show_route_sim")
    }

    // ── Flows for reactive UI ──

    val isSpoofEnabled: Flow<Boolean> = context.dataStore.data.map { it[SPOOF_ENABLED] ?: false }
    val latitude: Flow<Double> = context.dataStore.data.map { it[LATITUDE] ?: 48.8566 }
    val longitude: Flow<Double> = context.dataStore.data.map { it[LONGITUDE] ?: 2.3522 }
    val accuracy: Flow<Float> = context.dataStore.data.map { it[ACCURACY] ?: 10f }
    val spoofMode: Flow<SpoofMode> = context.dataStore.data.map {
        try { SpoofMode.valueOf(it[SPOOF_MODE] ?: "MOCK") } catch (e: Exception) { SpoofMode.MOCK }
    }
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[DARK_THEME] ?: true }
    val presetName: Flow<String> = context.dataStore.data.map { it[PRESET_NAME] ?: "" }
    val showCoords: Flow<Boolean> = context.dataStore.data.map { it[SHOW_COORDS] ?: true }
    val showPresets: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PRESETS] ?: true }
    val showRecent: Flow<Boolean> = context.dataStore.data.map { it[SHOW_RECENT] ?: true }
    val showRouteSim: Flow<Boolean> = context.dataStore.data.map { it[SHOW_ROUTE_SIM] ?: false }

    // ── Setters ──

    suspend fun setSpoofEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SPOOF_ENABLED] = enabled }
    }

    suspend fun setLocation(lat: Double, lng: Double, acc: Float = 10f, name: String = "") {
        context.dataStore.edit {
            it[LATITUDE] = lat
            it[LONGITUDE] = lng
            it[ACCURACY] = acc
            it[PRESET_NAME] = name
        }
    }

    suspend fun setAccuracy(acc: Float) {
        context.dataStore.edit { it[ACCURACY] = acc }
    }

    suspend fun setSpoofMode(mode: SpoofMode) {
        context.dataStore.edit { it[SPOOF_MODE] = mode.name }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = isDark }
    }

    suspend fun setDisplaySettings(showCoords: Boolean, showPresets: Boolean, showRecent: Boolean) {
        context.dataStore.edit {
            it[SHOW_COORDS] = showCoords
            it[SHOW_PRESETS] = showPresets
            it[SHOW_RECENT] = showRecent
        }
    }

    suspend fun setShowCoords(show: Boolean) { context.dataStore.edit { it[SHOW_COORDS] = show } }
    suspend fun setShowPresets(show: Boolean) { context.dataStore.edit { it[SHOW_PRESETS] = show } }
    suspend fun setShowRecent(show: Boolean) { context.dataStore.edit { it[SHOW_RECENT] = show } }
    suspend fun setShowRouteSim(show: Boolean) { context.dataStore.edit { it[SHOW_ROUTE_SIM] = show } }
}
