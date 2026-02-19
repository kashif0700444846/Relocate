// [Relocate] [MockLocationEngine.kt] - Standard Mode (No Root)
// Uses Android TestProvider + Google Play Services FusedLocationProvider
// for Uber-compatible mock location injection.
// DETECTABLE: Apps can check Location.isFromMockProvider() or
//             Settings.Secure.ALLOW_MOCK_LOCATION.

package com.relocate.app.spoofing

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.relocate.app.data.SpoofMode

class MockLocationEngine(private val context: Context) : SpoofEngine {

    companion object {
        private const val TAG = "MockLocationEngine"
    }

    override val mode = SpoofMode.MOCK
    override val isDetectable = true

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    // FusedLocationProviderClient for Google Play Services (Uber reads this)
    private var fusedClient: FusedLocationProviderClient? = null

    private var isRunning = false
    private var gpsProviderAdded = false
    private var networkProviderAdded = false

    /**
     * Checks if mock locations are enabled in Developer Options.
     */
    override fun isAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                android.provider.Settings.Secure.getInt(
                    context.contentResolver,
                    android.provider.Settings.Secure.ALLOW_MOCK_LOCATION, 0
                ) != 0
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "[IsAvailable] [WARN] ${e.message}")
            false
        }
    }

    override fun start(lat: Double, lng: Double, accuracy: Float) {
        try {
            // ── GPS Provider ──
            try { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}

            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, false,
                true, true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            gpsProviderAdded = true
            Log.i(TAG, "[Start] [SUCCESS] GPS test provider started")

            // ── Network Provider ──
            // Spoofing network provider ensures FusedLocationProvider sees our location
            try { locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) {}

            try {
                locationManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER,
                    false, false, false, false, false,
                    true, true,
                    android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                    android.location.provider.ProviderProperties.ACCURACY_COARSE
                )
                locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
                networkProviderAdded = true
                Log.i(TAG, "[Start] [SUCCESS] Network test provider started")
            } catch (e: Exception) {
                Log.w(TAG, "[Start] [WARN] Network provider failed (non-critical): ${e.message}")
                networkProviderAdded = false
            }

            // ── FusedLocationProviderClient (Google Play Services) ──
            // This is what Uber/Lyft/Bolt etc. read for location
            try {
                fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient?.setMockMode(true)
                    ?.addOnSuccessListener {
                        Log.i(TAG, "[Start] [SUCCESS] FusedLocation setMockMode(true) confirmed by Play Services")
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "[Start] [ERROR] FusedLocation setMockMode FAILED: ${e.message}")
                        Log.e(TAG, "[Start] [ERROR] *** Is Relocate set as Mock Location App in Developer Options? ***")
                        fusedClient = null
                    }
            } catch (e: Exception) {
                Log.w(TAG, "[Start] [WARN] FusedLocation not available: ${e.message}")
                fusedClient = null
            }

            isRunning = true

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
            val now = System.currentTimeMillis()
            val elapsedNanos = SystemClock.elapsedRealtimeNanos()

            // ── GPS Location ──
            val gpsLocation = buildLocation(LocationManager.GPS_PROVIDER, lat, lng, accuracy, now, elapsedNanos)
            if (gpsProviderAdded) {
                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLocation)
            }

            // ── Network Location ──
            if (networkProviderAdded) {
                val netLocation = buildLocation(LocationManager.NETWORK_PROVIDER, lat, lng, accuracy * 2f, now, elapsedNanos)
                locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, netLocation)
            }

            // ── FusedLocation (Google Play Services) ──
            // This feeds directly into FusedLocationProviderClient which Uber reads
            try {
                fusedClient?.setMockLocation(gpsLocation)
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "[Update] [ERROR] setMockLocation FAILED: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.w(TAG, "[Update] [WARN] FusedLocation mock failed: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[Update] [ERROR] ${e.message}")
        }
    }

    override fun stop() {
        try {
            if (isRunning) {
                // Remove GPS provider
                if (gpsProviderAdded) {
                    try {
                        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                        locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
                    } catch (_: Exception) {}
                    gpsProviderAdded = false
                }

                // Remove Network provider
                if (networkProviderAdded) {
                    try {
                        locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
                        locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
                    } catch (_: Exception) {}
                    networkProviderAdded = false
                }

                // Disable FusedLocation mock
                try {
                    fusedClient?.setMockMode(false)
                } catch (_: Exception) {}
                fusedClient = null

                isRunning = false
                Log.i(TAG, "[Stop] [SUCCESS] All mock providers removed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Stop] [WARN] ${e.message}")
            isRunning = false
        }
    }

    /**
     * Builds a realistic Location object for the given provider.
     */
    private fun buildLocation(
        provider: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        timeMs: Long,
        elapsedNanos: Long
    ): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lng
            this.accuracy = accuracy
            altitude = 0.0
            bearing = 0f
            speed = 0f
            time = timeMs
            elapsedRealtimeNanos = elapsedNanos

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 0.1f
                verticalAccuracyMeters = accuracy
                speedAccuracyMetersPerSecond = 0.01f
            }
        }
    }
}
