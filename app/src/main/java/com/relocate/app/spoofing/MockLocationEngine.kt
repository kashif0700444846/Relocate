// [Relocate] [MockLocationEngine.kt] - Standard Mode (No Root)
// Uses Android's built-in TestProvider API to provide mock locations.
// DETECTABLE: Apps can check Location.isFromMockProvider() or
//             Settings.Secure.ALLOW_MOCK_LOCATION.

package com.relocate.app.spoofing

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.relocate.app.data.SpoofMode

class MockLocationEngine(private val context: Context) : SpoofEngine {

    companion object {
        private const val TAG = "MockLocationEngine"
        private const val PROVIDER_NAME = LocationManager.GPS_PROVIDER
    }

    override val mode = SpoofMode.MOCK
    override val isDetectable = true

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private var isRunning = false

    /**
     * Checks if mock locations are enabled in Developer Options.
     */
    override fun isAvailable(): Boolean {
        return try {
            // On older APIs, check the global setting
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                android.provider.Settings.Secure.getInt(
                    context.contentResolver,
                    android.provider.Settings.Secure.ALLOW_MOCK_LOCATION, 0
                ) != 0
            } else {
                // On newer APIs, we try to add a test provider and catch SecurityException
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "[IsAvailable] [WARN] ${e.message}")
            false
        }
    }

    override fun start(lat: Double, lng: Double, accuracy: Float) {
        try {
            // Remove existing test provider if any
            try {
                locationManager.removeTestProvider(PROVIDER_NAME)
            } catch (e: Exception) {
                // Ignore — provider might not exist yet
            }

            // Add GPS as a test provider
            locationManager.addTestProvider(
                PROVIDER_NAME,
                false, false, false, false, false,
                true, true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE
            )

            locationManager.setTestProviderEnabled(PROVIDER_NAME, true)
            isRunning = true
            Log.i(TAG, "[Start] [SUCCESS] Mock provider started")

            // Set initial location
            update(lat, lng, accuracy)
        } catch (e: SecurityException) {
            Log.e(TAG, "[Start] [ERROR] Mock location not allowed. Enable in Developer Options: ${e.message}")
            isRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "[Start] [ERROR] ${e.message}")
            isRunning = false
        }
    }

    override fun update(lat: Double, lng: Double, accuracy: Float) {
        if (!isRunning) return

        try {
            val location = Location(PROVIDER_NAME).apply {
                latitude = lat
                longitude = lng
                this.accuracy = accuracy
                altitude = 0.0
                bearing = 0f
                speed = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                // Required on newer Android versions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 0.1f
                    verticalAccuracyMeters = accuracy
                    speedAccuracyMetersPerSecond = 0.01f
                }
            }

            locationManager.setTestProviderLocation(PROVIDER_NAME, location)
        } catch (e: Exception) {
            Log.e(TAG, "[Update] [ERROR] ${e.message}")
        }
    }

    override fun stop() {
        try {
            if (isRunning) {
                locationManager.setTestProviderEnabled(PROVIDER_NAME, false)
                locationManager.removeTestProvider(PROVIDER_NAME)
                isRunning = false
                Log.i(TAG, "[Stop] [SUCCESS] Mock provider removed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Stop] [WARN] ${e.message}")
            isRunning = false
        }
    }
}
