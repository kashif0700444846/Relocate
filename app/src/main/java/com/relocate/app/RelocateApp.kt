// [Relocate] [RelocateApp.kt] - Application Class
// Initializes OSMDroid configuration on app start.

package com.relocate.app

import android.app.Application
import org.osmdroid.config.Configuration

class RelocateApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure OSMDroid
        Configuration.getInstance().apply {
            userAgentValue = "Relocate/${packageName}"
            osmdroidTileCache = cacheDir.resolve("osmdroid")
        }
    }
}
