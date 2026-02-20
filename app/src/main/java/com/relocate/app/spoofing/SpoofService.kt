// [Relocate] [SpoofService.kt] - Foreground Service for Continuous Spoofing
// Keeps location spoofing active even when the app is in the background.
// Features: live coordinate notification, Stop action button, volatile coordinates.

package com.relocate.app.spoofing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.relocate.app.logging.AppLogger
import android.location.LocationListener
import android.location.LocationManager
import com.relocate.app.MainActivity
import com.relocate.app.data.SpoofMode
import com.relocate.app.SpoofConstants
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import java.util.Locale

class SpoofService : Service() {

    companion object {
        private const val TAG = "SpoofService"
        private const val CHANNEL_ID = "relocate_spoof_channel"
        private const val NOTIFICATION_ID = 1001

        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"
        private const val EXTRA_ACC = "accuracy"
        private const val EXTRA_MODE = "mode"
        private const val ACTION_START = "com.relocate.START_SPOOF"
        private const val ACTION_UPDATE = "com.relocate.UPDATE_SPOOF"
        private const val ACTION_STOP = "com.relocate.STOP_SPOOF"
        const val ACTION_STOP_FROM_NOTIFICATION = "com.relocate.STOP_SPOOF_NOTIFICATION"

        fun startSpoof(context: Context, lat: Double, lng: Double, accuracy: Float, mode: SpoofMode) {
            val intent = Intent(context, SpoofService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_ACC, accuracy)
                putExtra(EXTRA_MODE, mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateSpoof(context: Context, lat: Double, lng: Double, accuracy: Float) {
            val intent = Intent(context, SpoofService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_ACC, accuracy)
            }
            context.startService(intent)
        }

        fun stopSpoof(context: Context) {
            val intent = Intent(context, SpoofService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var engine: SpoofEngine? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    // Mutable coordinates — updated by updateSpoof(), read by the continuous loop
    @Volatile private var currentLat = 0.0
    @Volatile private var currentLng = 0.0
    @Volatile private var currentAcc = 10f

    // Keeps track of current mode for notification
    private var currentModeLabel = "Standard"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                val acc = intent.getFloatExtra(EXTRA_ACC, 10f)
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: SpoofMode.MOCK.name
                val mode = try { SpoofMode.valueOf(modeName) } catch (e: Exception) { SpoofMode.MOCK }

                startSpoofing(lat, lng, acc, mode)
            }
            ACTION_UPDATE -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                val acc = intent.getFloatExtra(EXTRA_ACC, 10f)

                // Update the volatile coordinates — the loop will pick them up
                currentLat = lat
                currentLng = lng
                currentAcc = acc

                // Write to SharedPrefs so UberLocationHook can read cross-process
                writeSpoofCoords(lat, lng)

                // Also inject immediately for responsiveness
                try {
                    engine?.update(lat, lng, acc)
                    Log.d(TAG, "[Update] [SUCCESS] Position updated to $lat, $lng")
                } catch (e: Exception) {
                    Log.e(TAG, "[Update] [ERROR] ${e.message}")
                }

                // Update notification with new coordinates
                updateNotification(lat, lng)
            }
            ACTION_STOP, ACTION_STOP_FROM_NOTIFICATION -> {
                stopSpoofing()
            }
        }
        return START_STICKY
    }

    private fun startSpoofing(lat: Double, lng: Double, accuracy: Float, mode: SpoofMode) {
        try {
            // Stop existing engine if any
            engine?.stop()
            updateJob?.cancel()

            // Set initial coordinates
            currentLat = lat
            currentLng = lng
            currentAcc = accuracy

            // Create appropriate engine
            engine = when (mode) {
                SpoofMode.ROOT -> RootSpoofEngine(this)
                SpoofMode.MOCK -> MockLocationEngine(this)
            }

            // Track mode label for notifications
            currentModeLabel = if (mode == SpoofMode.ROOT) "Root" else "Standard"

            // Start foreground notification with Stop action
            startForeground(NOTIFICATION_ID, buildNotification(
                "📍 Spoofing Active — $currentModeLabel",
                String.format(Locale.US, "%.6f, %.6f", lat, lng)
            ))

            // Start engine
            engine?.start(lat, lng, accuracy)

            // Write to SharedPrefs so UberLocationHook (in Uber's process) can read
            writeSpoofCoords(lat, lng)

            Log.i(TAG, "[Start] [SUCCESS] Spoofing started in $mode mode at $lat, $lng")
            AppLogger.i(TAG, "📍 Spoofing STARTED in $currentModeLabel mode at ${String.format(Locale.US, "%.6f, %.6f", lat, lng)}")

            // Continuous update loop — reads volatile coordinates each tick
            updateJob = serviceScope.launch {
                while (isActive) {
                    delay(2000)
                    try {
                        engine?.update(currentLat, currentLng, currentAcc)
                    } catch (e: Exception) {
                        Log.e(TAG, "[Loop] [ERROR] ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Start] [ERROR] Failed to start spoofing: ${e.message}", e)
            stopSpoofing()
        }
    }

    /**
     * Updates the foreground notification with current coordinates.
     */
    private fun updateNotification(lat: Double, lng: Double) {
        try {
            val notification = buildNotification(
                "📍 Spoofing Active — $currentModeLabel",
                String.format(Locale.US, "%.6f, %.6f", lat, lng)
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "[Notification] [WARN] ${e.message}")
        }
    }

    private fun stopSpoofing() {
        updateJob?.cancel()
        engine?.stop()
        engine = null
        // Clear SharedPrefs so UberLocationHook stops injecting fake coords
        clearSpoofCoords()
        Log.i(TAG, "[Stop] [SUCCESS] Spoofing stopped")
        AppLogger.i(TAG, "⏹ Spoofing STOPPED")
        // Restore the real location immediately
        restoreRealLocation()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Called after stopping spoofing to restore the device's real GPS location.
     * 1. Disables FusedLocation mock mode (otherwise GPS stays stuck on last fake coords)
     * 2. Forces a one-shot real GPS fix and logs it so the system has fresh real data
     */
    private fun restoreRealLocation() {
        try {
            // 1. Disable FusedLocation mock mode — this un-freezes the fused provider
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.setMockMode(false)
                .addOnSuccessListener {
                    Log.i(TAG, "[Restore] [SUCCESS] FusedLocation mock mode disabled — real GPS active")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "[Restore] [WARN] setMockMode(false) failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "[Restore] [WARN] FusedClient restore failed: ${e.message}")
        }

        try {
            // 2. Request a fresh GPS fix from the system provider
            // This wakes up the GPS chipset immediately after mock mode ends
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = object : LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    Log.i(TAG, "[Restore] [SUCCESS] Real GPS fix obtained: ${location.latitude}, ${location.longitude}")
                    try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                }
            }
            @Suppress("MissingPermission")
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, mainLooper)
            Log.i(TAG, "[Restore] [INFO] Requested single real GPS update from system")
        } catch (e: Exception) {
            Log.w(TAG, "[Restore] [WARN] Single GPS request failed: ${e.message}")
        }
    }

    /**
     * Writes the current spoof coordinates to SharedPreferences.
     * UberLocationHook reads these from the Uber Driver process via XSharedPreferences.
     * Uses Double.toBits() to store full double precision (Float would lose GPS decimals).
     */
    @Suppress("CommitPrefEdits")
    private fun writeSpoofCoords(lat: Double, lng: Double) {
        try {
            getSharedPreferences(SpoofConstants.PREFS_NAME, MODE_PRIVATE).edit().apply {
                putBoolean(SpoofConstants.KEY_ACTIVE, true)
                putLong(SpoofConstants.KEY_LAT, lat.toBits())
                putLong(SpoofConstants.KEY_LNG, lng.toBits())
                apply()
            }
            Log.d(TAG, "[SpoofPrefs] [WRITE] lat=$lat lng=$lng written for XPosed hook")
            AppLogger.d(TAG, "XPosed prefs written: ${String.format(Locale.US, "%.6f, %.6f", lat, lng)}")
        } catch (e: Exception) {
            Log.e(TAG, "[SpoofPrefs] [ERROR] Failed to write spoof prefs: ${e.message}")
        }
    }

    /**
     * Clears the spoof prefs — the UberLocationHook will stop injecting fake coords.
     */
    @Suppress("CommitPrefEdits")
    private fun clearSpoofCoords() {
        try {
            getSharedPreferences(SpoofConstants.PREFS_NAME, MODE_PRIVATE).edit().apply {
                putBoolean(SpoofConstants.KEY_ACTIVE, false)
                remove(SpoofConstants.KEY_LAT)
                remove(SpoofConstants.KEY_LNG)
                apply()
            }
            Log.d(TAG, "[SpoofPrefs] [CLEAR] Spoof prefs cleared")
        } catch (e: Exception) {
            Log.e(TAG, "[SpoofPrefs] [ERROR] Failed to clear spoof prefs: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Spoofing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Relocate is actively spoofing your location"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        // Tap action — opens the app
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop action — stops spoofing from notification
        val stopIntent = Intent(this, SpoofService::class.java).apply {
            action = ACTION_STOP_FROM_NOTIFICATION
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openIntent)
            .setOngoing(true)

        // Add Stop action button
        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_media_pause,
            "Stop Spoofing",
            stopPendingIntent
        ).build()
        builder.addAction(stopAction)

        return builder.build()
    }

    override fun onDestroy() {
        updateJob?.cancel()
        engine?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }
}
