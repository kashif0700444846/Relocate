// [Relocate] [SpoofService.kt] - Foreground Service for Continuous Spoofing
// Keeps location spoofing active even when the app is in the background.

package com.relocate.app.spoofing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.relocate.app.MainActivity
import com.relocate.app.data.SpoofMode
import kotlinx.coroutines.*

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
                engine?.update(lat, lng, acc)
            }
            ACTION_STOP -> {
                stopSpoofing()
            }
        }
        return START_STICKY
    }

    private fun startSpoofing(lat: Double, lng: Double, accuracy: Float, mode: SpoofMode) {
        // Stop existing engine if any
        engine?.stop()

        // Create appropriate engine
        engine = when (mode) {
            SpoofMode.ROOT -> RootSpoofEngine(this)
            SpoofMode.MOCK -> MockLocationEngine(this)
        }

        // Start foreground notification
        val modeLabel = if (mode == SpoofMode.ROOT) "Root (Undetectable)" else "Standard (Mock)"
        startForeground(NOTIFICATION_ID, buildNotification(
            "📍 Spoofing Active — $modeLabel",
            "Location: %.4f, %.4f".format(lat, lng)
        ))

        // Start engine
        engine?.start(lat, lng, accuracy)
        Log.i(TAG, "[Start] [SUCCESS] Spoofing started in $mode mode at $lat, $lng")

        // Continuous update loop (re-inject every 2 seconds to keep mock provider alive)
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (isActive) {
                delay(2000)
                engine?.update(lat, lng, accuracy)
            }
        }
    }

    private fun stopSpoofing() {
        updateJob?.cancel()
        engine?.stop()
        engine = null
        Log.i(TAG, "[Stop] [SUCCESS] Spoofing stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        updateJob?.cancel()
        engine?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }
}
