// [Relocate] [RootSpoofEngine.kt] - Root Mode (Undetectable)
// Uses su commands to inject GPS coordinates at the system level.
// This bypasses mock location detection checks used by ride-hailing apps.

package com.relocate.app.spoofing

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.relocate.app.data.SpoofMode

class RootSpoofEngine(private val context: Context) : SpoofEngine {

    companion object {
        private const val TAG = "RootSpoofEngine"
        private const val PROVIDER_NAME = LocationManager.GPS_PROVIDER
    }

    override val mode = SpoofMode.ROOT
    override val isDetectable = false

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private var isRunning = false
    private var hasRoot = false

    /**
     * Check if the device has root (su) access.
     */
    override fun isAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val exitCode = process.waitFor()
            hasRoot = exitCode == 0
            if (hasRoot) {
                Log.i(TAG, "[IsAvailable] [SUCCESS] Root access confirmed")
            } else {
                Log.w(TAG, "[IsAvailable] [WARN] Root access denied (exit code: $exitCode)")
            }
            hasRoot
        } catch (e: Exception) {
            Log.w(TAG, "[IsAvailable] [WARN] No root: ${e.message}")
            hasRoot = false
            false
        }
    }

    override fun start(lat: Double, lng: Double, accuracy: Float) {
        if (!hasRoot && !isAvailable()) {
            Log.e(TAG, "[Start] [ERROR] No root access. Cannot start root spoofing.")
            return
        }

        try {
            // Step 1: Use root to enable mock location setting system-wide
            // This hides it from app-level detection
            execRoot("appops set ${context.packageName} android:mock_location allow")

            // Step 2: Remove existing test provider
            try {
                locationManager.removeTestProvider(PROVIDER_NAME)
            } catch (e: Exception) { /* ignore */ }

            // Step 3: Add test provider with root-level permissions
            locationManager.addTestProvider(
                PROVIDER_NAME,
                false, false, false, false, false,
                true, true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(PROVIDER_NAME, true)

            // Step 4: Use root to hide mock location indicators
            // Disable the mock location flag in the Location object
            execRoot("settings put secure mock_location 0")

            isRunning = true
            Log.i(TAG, "[Start] [SUCCESS] Root spoofing started (undetectable mode)")

            // Set initial location
            update(lat, lng, accuracy)

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
                altitude = 30.0 + (Math.random() * 5) // Realistic altitude variation
                bearing = (Math.random() * 360).toFloat()
                speed = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 10f
                    verticalAccuracyMeters = accuracy * 1.5f
                    speedAccuracyMetersPerSecond = 0.5f
                }
            }

            // Use reflection to remove isFromMockProvider flag
            // This is the key to making it undetectable
            try {
                val extrasField = Location::class.java.getMethod("setIsFromMockProvider", Boolean::class.javaPrimitiveType)
                extrasField.invoke(location, false)
            } catch (e: Exception) {
                // On newer APIs, try the extras bundle approach
                try {
                    location.extras?.remove("mockProvider")
                    // Use reflection to set the mIsFromMockProvider field directly
                    val field = Location::class.java.getDeclaredField("mIsFromMockProvider")
                    field.isAccessible = true
                    field.setBoolean(location, false)
                } catch (e2: Exception) {
                    Log.w(TAG, "[Update] [WARN] Could not remove mock flag: ${e2.message}")
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

                // Restore mock location detection
                execRoot("settings put secure mock_location 1")

                isRunning = false
                Log.i(TAG, "[Stop] [SUCCESS] Root spoofing stopped")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Stop] [WARN] ${e.message}")
            isRunning = false
        }
    }

    /**
     * Execute a command as root via su.
     */
    private fun execRoot(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0 && error.isNotBlank()) {
                Log.w(TAG, "[ExecRoot] [WARN] Command failed: $command -> $error")
            }

            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "[ExecRoot] [ERROR] $command: ${e.message}")
            ""
        }
    }
}
