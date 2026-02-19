// [Relocate] [SpoofEngine.kt] - Spoofing Engine Interface
// Common interface for both Mock Location and Root-based spoofing.

package com.relocate.app.spoofing

import com.relocate.app.data.SpoofMode

interface SpoofEngine {
    /** Which mode this engine implements */
    val mode: SpoofMode

    /** Whether this mode is detectable by apps checking for mock locations */
    val isDetectable: Boolean

    /** Check if this engine can work on the current device */
    fun isAvailable(): Boolean

    /** Start spoofing at the given coordinates */
    fun start(lat: Double, lng: Double, accuracy: Float = 10f)

    /** Update the spoofed location (without restart) */
    fun update(lat: Double, lng: Double, accuracy: Float = 10f)

    /** Stop spoofing and restore real GPS */
    fun stop()
}
