// [Relocate] [RootSpoofEngine.kt] - Root Mode (Undetectable)
// Uses su commands + FusedLocationProvider + dual-provider spoofing
// to inject GPS coordinates at the system level.
// Strips isFromMockProvider flags and denies mock detection for ride-hailing apps.

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

class RootSpoofEngine(private val context: Context) : SpoofEngine {

    companion object {
        private const val TAG = "RootSpoofEngine"

        // Ride-hailing packages that check mock location detection
        private val RIDE_APP_PACKAGES = listOf(
            "com.ubercab",
            "com.ubercab.driver",
            "com.ubercab.eats",
            "me.lyft.android",
            "se.cabonline.driverapp",
            "se.cabonline.cabodriver",
            "bolt.driver",
            "ee.mtakso.driver"
        )
    }

    override val mode = SpoofMode.ROOT
    override val isDetectable = false

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private var fusedClient: FusedLocationProviderClient? = null
    private var isRunning = false
    private var hasRoot = false
    private var gpsProviderAdded = false
    private var networkProviderAdded = false

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
            // ── Step 1: Root-level mock location permission ──
            execRoot("appops set ${context.packageName} android:mock_location allow")

            // ── Step 2: Disable mock location detection globally ──
            execRoot("settings put secure mock_location 0")

            // ── Step 3: Deny mock location detection for ride-hailing apps ──
            // This prevents Uber/Lyft etc from using AppOps to detect our mock
            for (pkg in RIDE_APP_PACKAGES) {
                execRoot("appops set $pkg android:mock_location deny 2>/dev/null")
            }

            // ── Step 4: GPS Provider ──
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

            // ── Step 5: Network Provider ──
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

            // ── Step 6: FusedLocationProviderClient (Google Play Services) ──
            try {
                fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient?.setMockMode(true)
                Log.i(TAG, "[Start] [SUCCESS] FusedLocation mock mode enabled")
            } catch (e: Exception) {
                Log.w(TAG, "[Start] [WARN] FusedLocation not available: ${e.message}")
                fusedClient = null
            }

            isRunning = true
            Log.i(TAG, "[Start] [SUCCESS] Root spoofing started (GPS + Network + Fused, undetectable)")

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
            val now = System.currentTimeMillis()
            val elapsedNanos = SystemClock.elapsedRealtimeNanos()

            // ── GPS Location (with mock flag stripped) ──
            val gpsLocation = buildLocation(LocationManager.GPS_PROVIDER, lat, lng, accuracy, now, elapsedNanos)
            stripMockFlag(gpsLocation)
            if (gpsProviderAdded) {
                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLocation)
            }

            // ── Network Location (with mock flag stripped) ──
            if (networkProviderAdded) {
                val netLocation = buildLocation(LocationManager.NETWORK_PROVIDER, lat, lng, accuracy * 2f, now, elapsedNanos)
                stripMockFlag(netLocation)
                locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, netLocation)
            }

            // ── FusedLocation (Google Play Services — what Uber reads) ──
            try {
                fusedClient?.setMockLocation(gpsLocation)
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

                // Restore mock location detection
                execRoot("settings put secure mock_location 1")

                // Restore ride-hailing apps' mock detection permission
                for (pkg in RIDE_APP_PACKAGES) {
                    execRoot("appops set $pkg android:mock_location allow 2>/dev/null")
                }

                isRunning = false
                Log.i(TAG, "[Stop] [SUCCESS] Root spoofing stopped, detection restored")
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Stop] [WARN] ${e.message}")
            isRunning = false
        }
    }

    /**
     * Strips the isFromMockProvider flag from a Location object using reflection.
     * This is what makes root mode "undetectable" — the Location object won't
     * report itself as coming from a mock provider.
     */
    private fun stripMockFlag(location: Location) {
        // Method 1: setIsFromMockProvider(false) — works on API 18-30
        try {
            val method = Location::class.java.getMethod(
                "setIsFromMockProvider", Boolean::class.javaPrimitiveType
            )
            method.invoke(location, false)
            return
        } catch (_: Exception) {}

        // Method 2: Direct field access — mIsFromMockProvider (API 18-28)
        try {
            val field = Location::class.java.getDeclaredField("mIsFromMockProvider")
            field.isAccessible = true
            field.setBoolean(location, false)
            return
        } catch (_: Exception) {}

        // Method 3: mIsMock field (API 31+)
        try {
            val field = Location::class.java.getDeclaredField("mIsMock")
            field.isAccessible = true
            field.setBoolean(location, false)
            return
        } catch (_: Exception) {}

        // Method 4: Remove from extras bundle
        try {
            location.extras?.remove("mockProvider")
            location.extras?.remove("isMock")
        } catch (_: Exception) {}

        Log.w(TAG, "[StripMock] [WARN] Could not remove mock flag via any method")
    }

    /**
     * Builds a realistic Location object with natural-looking values.
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
            altitude = 30.0 + (Math.random() * 5) // Realistic altitude variation
            bearing = (Math.random() * 360).toFloat()
            speed = 0f
            time = timeMs
            elapsedRealtimeNanos = elapsedNanos

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 10f
                verticalAccuracyMeters = accuracy * 1.5f
                speedAccuracyMetersPerSecond = 0.5f
            }
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
