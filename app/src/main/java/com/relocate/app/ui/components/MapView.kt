// [Relocate] [MapView.kt] - OSMDroid Map Composable
// Wraps OSMDroid MapView in Jetpack Compose via AndroidView.
// Supports: route polyline, directional arrow marker, GPS blue dot, overview zoom.

package com.relocate.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    latitude: Double,
    longitude: Double,
    onMapClick: ((Double, Double) -> Unit)? = null,
    onMarkerDrag: ((Double, Double) -> Unit)? = null,
    zoomLevel: Double = 13.0,
    showMyLocation: Boolean = true,
    routePath: List<GeoPoint>? = null,
    simulationBearing: Float = 0f,
    isSimulating: Boolean = false
) {
    val context = LocalContext.current

    // Pre-create bitmaps (only once)
    val arrowBitmap = remember { createNavigationArrow(context) }
    val blueDotBitmap = remember { createBlueDot(context) }

    // Track overlay references to avoid duplicates
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }
    var polylineRef by remember { mutableStateOf<Polyline?>(null) }
    var lastRouteSize by remember { mutableIntStateOf(0) }
    var hasShownOverview by remember { mutableStateOf(false) }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                // ── FIX: Allow single-finger map control ──
                // The map is inside a verticalScroll Column. Without this,
                // the parent scroll steals single-finger drag gestures,
                // forcing the user to use 2 fingers to pan the map.
                setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false // Return false so the map still processes the event
                }

                controller.setZoom(zoomLevel)
                controller.setCenter(GeoPoint(latitude, longitude))

                // ── GPS Blue Dot Overlay ──
                if (showMyLocation && hasLocationPermission) {
                    try {
                        val myLocOverlay = MyLocationNewOverlay(
                            GpsMyLocationProvider(ctx), this
                        )
                        myLocOverlay.setPersonIcon(blueDotBitmap)
                        myLocOverlay.setDirectionIcon(blueDotBitmap)
                        myLocOverlay.enableMyLocation()
                        // Don't follow — we control map center ourselves
                        overlays.add(myLocOverlay)
                    } catch (_: Exception) { /* GPS unavailable */ }
                }

                // ── Map Click Events ──
                if (onMapClick != null) {
                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                onMapClick(p.latitude, p.longitude)
                            }
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                    overlays.add(eventsOverlay)
                }

                // ── Marker (pin initially) ──
                val m = Marker(this).apply {
                    position = GeoPoint(latitude, longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    isDraggable = onMarkerDrag != null
                    title = "Spoofed Location"

                    if (onMarkerDrag != null) {
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(m: Marker?) {}
                            override fun onMarkerDragStart(m: Marker?) {}
                            override fun onMarkerDragEnd(m: Marker?) {
                                m?.position?.let { pos ->
                                    onMarkerDrag(pos.latitude, pos.longitude)
                                }
                            }
                        })
                    }
                }
                overlays.add(m)
                markerRef = m
                mapViewRef = this
            }
        },
        update = { view ->
            val newPos = GeoPoint(latitude, longitude)
            val currentRouteSize = routePath?.size ?: 0

            // ── Update marker position ──
            markerRef?.position = newPos

            // ── Switch marker icon: arrow during sim, default pin otherwise ──
            if (isSimulating) {
                markerRef?.icon = android.graphics.drawable.BitmapDrawable(
                    context.resources, arrowBitmap
                )
                markerRef?.rotation = -simulationBearing // negative for OSMDroid
            } else {
                markerRef?.icon = null // revert to default pin
                markerRef?.rotation = 0f
            }

            // ── Route polyline management ──
            if (routePath != null && routePath.isNotEmpty()) {
                // Only recreate polyline if route changed
                if (currentRouteSize != lastRouteSize) {
                    // Remove old polyline
                    polylineRef?.let { view.overlays.remove(it) }

                    // Create new polyline
                    val pl = Polyline().apply {
                        setPoints(routePath)
                        outlinePaint.color = Color.parseColor("#FF9800") // Amber
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    }
                    // Insert behind marker (index 0 or after GPS overlay)
                    val insertIdx = if (view.overlays.size > 1) 1 else 0
                    view.overlays.add(insertIdx, pl)
                    polylineRef = pl
                    lastRouteSize = currentRouteSize

                    // ── SHOW FULL ROUTE OVERVIEW FIRST ──
                    try {
                        val minLat = routePath.minOf { it.latitude }
                        val maxLat = routePath.maxOf { it.latitude }
                        val minLng = routePath.minOf { it.longitude }
                        val maxLng = routePath.maxOf { it.longitude }
                        val bbox = BoundingBox(maxLat, maxLng, minLat, minLng)
                        view.zoomToBoundingBox(bbox, true, 100)
                    } catch (_: Exception) { /* bbox calculation failed */ }
                    hasShownOverview = true
                }
            } else {
                // Route cleared — remove polyline
                if (polylineRef != null) {
                    view.overlays.remove(polylineRef)
                    polylineRef = null
                    lastRouteSize = 0
                    hasShownOverview = false
                }
            }

            // ── Camera follow during simulation (after overview) ──
            if (isSimulating && hasShownOverview) {
                view.controller.animateTo(newPos)
                view.controller.setZoom(16.0) // zoom in during sim
            } else if (!isSimulating) {
                val center = view.mapCenter
                if (Math.abs(center.latitude - latitude) > 0.000001 ||
                    Math.abs(center.longitude - longitude) > 0.000001
                ) {
                    view.controller.animateTo(newPos)
                }
            }

            view.invalidate()
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onDetach()
        }
    }
}

// ── Creates a navigation arrow bitmap (blue triangle pointing UP) ──
private fun createNavigationArrow(context: android.content.Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (48 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Arrow fill (Google Maps blue)
    val fillPaint = Paint().apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val path = Path().apply {
        moveTo(size / 2f, size * 0.08f)       // top tip
        lineTo(size * 0.18f, size * 0.88f)     // bottom-left
        lineTo(size / 2f, size * 0.65f)        // center notch
        lineTo(size * 0.82f, size * 0.88f)     // bottom-right
        close()
    }
    canvas.drawPath(path, fillPaint)

    // White border
    val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        isAntiAlias = true
    }
    canvas.drawPath(path, borderPaint)

    return bitmap
}

// ── Creates a Google Maps-style blue dot with pulse ring ──
private fun createBlueDot(context: android.content.Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    // Outer pulse ring (semi-transparent blue)
    val pulsePaint = Paint().apply {
        color = Color.parseColor("#404285F4") // 25% opacity
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(center, center, center * 0.95f, pulsePaint)

    // White border ring
    val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(center, center, center * 0.55f, borderPaint)

    // Inner solid blue dot
    val dotPaint = Paint().apply {
        color = Color.parseColor("#4285F4")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(center, center, center * 0.42f, dotPaint)

    return bitmap
}
