// [Relocate] [MapView.kt] - OSMDroid Map Composable
// Wraps OSMDroid MapView in Jetpack Compose via AndroidView.

package com.relocate.app.ui.components

import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    latitude: Double,
    longitude: Double,
    onMapClick: ((Double, Double) -> Unit)? = null,
    onMarkerDrag: ((Double, Double) -> Unit)? = null,
    zoomLevel: Double = 13.0
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var marker by remember { mutableStateOf<Marker?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(zoomLevel)
                controller.setCenter(GeoPoint(latitude, longitude))

                // Map click listener
                if (onMapClick != null) {
                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                onMapClick(p.latitude, p.longitude)
                                // Move marker
                                marker?.position = p
                                invalidate()
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                    overlays.add(eventsOverlay)
                }

                // Draggable marker
                val m = Marker(this).apply {
                    position = GeoPoint(latitude, longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
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
                marker = m
                mapView = this
            }
        },
        update = { view ->
            val currentPos = marker?.position
            val newPos = GeoPoint(latitude, longitude)

            // Only update if position actually changed (avoids infinite recomposition)
            if (currentPos == null ||
                Math.abs(currentPos.latitude - latitude) > 0.000001 ||
                Math.abs(currentPos.longitude - longitude) > 0.000001
            ) {
                marker?.position = newPos
                view.controller.animateTo(newPos)
                view.invalidate()
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDetach()
        }
    }
}
