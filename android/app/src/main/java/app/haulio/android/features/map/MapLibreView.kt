package app.haulio.android.features.map

import android.annotation.SuppressLint
import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.haulio.android.features.crime.CrimeHeatmapLayer
import app.haulio.android.features.fuel.FuelMapMarkersLayer
import app.haulio.android.features.traffic.IncidentPinsLayer
import app.haulio.android.features.traffic.TrafficOverlayLayer
import app.haulio.android.services.fuel.FuelStation
import app.haulio.android.services.location.LocationPoint
import app.haulio.android.services.map.MapStyleProvider
import app.haulio.android.services.map.TileConfiguration
import app.haulio.android.services.traffic.TrafficEvent
import app.haulio.shared.crime.models.CrimeGridCell
import org.koin.java.KoinJavaComponent.getKoin
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * MapLibre map embedded inside a Compose layout.
 *
 * @param userLocation         Current driver location; triggers camera pan when changed.
 * @param trafficEvents        Live traffic events — used to paint the overlay and incident pins.
 * @param isTrafficVisible     Whether the congestion overlay is shown.
 * @param fuelStations         Fuel stations to render as map pins.
 * @param isFuelVisible        Whether the fuel station layer is shown.
 * @param crimeGrid            Crime heatmap grid cells to render.
 * @param isCrimeVisible       Whether the crime heatmap layer is shown.
 * @param onMapLongClick       Called when the driver long-presses the map (lat, lon).
 * @param onIncidentTapped     Called with the incident ID when a pin is tapped.
 * @param onFuelStationTapped  Called with the station ID when a fuel pin is tapped.
 */
@SuppressLint("MissingPermission")
@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    userLocation: LocationPoint? = null,
    trafficEvents: List<TrafficEvent> = emptyList(),
    isTrafficVisible: Boolean = true,
    fuelStations: List<FuelStation> = emptyList(),
    isFuelVisible: Boolean = false,
    crimeGrid: List<CrimeGridCell> = emptyList(),
    isCrimeVisible: Boolean = false,
    onMapLongClick: ((Double, Double) -> Unit)? = null,
    onIncidentTapped: ((String) -> Unit)? = null,
    onFuelStationTapped: ((String) -> Unit)? = null,
) {
    val context          = LocalContext.current
    val lifecycleOwner   = LocalLifecycleOwner.current
    val mapStyleProvider = remember { getKoin().get<MapStyleProvider>() }
    val mapView          = remember { MapView(context).apply { onCreate(null) } }

    // Stable references updated on every recomposition via SideEffect.
    // The map callbacks capture these State objects, so they always call the latest lambdas.
    val onLongClickRef      = remember { mutableStateOf(onMapLongClick) }
    val onIncidentRef       = remember { mutableStateOf(onIncidentTapped) }
    val onFuelStationRef    = remember { mutableStateOf(onFuelStationTapped) }

    // Latest data refs; seeded into the style immediately after it loads.
    val trafficEventsRef    = remember { mutableStateOf(trafficEvents) }
    val trafficVisibleRef   = remember { mutableStateOf(isTrafficVisible) }
    val fuelStationsRef     = remember { mutableStateOf(fuelStations) }
    val fuelVisibleRef      = remember { mutableStateOf(isFuelVisible) }
    val crimeGridRef        = remember { mutableStateOf(crimeGrid) }
    val crimeVisibleRef     = remember { mutableStateOf(isCrimeVisible) }

    SideEffect {
        onLongClickRef.value    = onMapLongClick
        onIncidentRef.value     = onIncidentTapped
        onFuelStationRef.value  = onFuelStationTapped
        trafficEventsRef.value  = trafficEvents
        trafficVisibleRef.value = isTrafficVisible
        fuelStationsRef.value   = fuelStations
        fuelVisibleRef.value    = isFuelVisible
        crimeGridRef.value      = crimeGrid
        crimeVisibleRef.value   = isCrimeVisible
    }

    // ── Lifecycle wiring ──────────────────────────────────────────────────
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else                       -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── One-time map setup (listeners + style) ────────────────────────────
    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            // Long-press: driver reports an incident
            map.addOnMapLongClickListener { latLng ->
                onLongClickRef.value?.invoke(latLng.latitude, latLng.longitude)
                true
            }

            // Tap: show details if an incident pin or fuel station pin is hit
            map.addOnMapClickListener { latLng ->
                val projection = map.projection
                val screenPt   = projection.toScreenLocation(latLng)
                val incidentId = IncidentPinsLayer.queryAtPoint(map, PointF(screenPt.x, screenPt.y))
                val fuelId     = FuelMapMarkersLayer.queryAtPoint(map, PointF(screenPt.x, screenPt.y))
                when {
                    incidentId != null -> {
                        onIncidentRef.value?.invoke(incidentId)
                        true
                    }
                    fuelId != null -> {
                        onFuelStationRef.value?.invoke(fuelId)
                        true
                    }
                    else -> false
                }
            }

            configureMap(map, mapStyleProvider) { style ->
                // Seed initial data as soon as the style is ready
                TrafficOverlayLayer.update(style, trafficEventsRef.value, trafficVisibleRef.value)
                IncidentPinsLayer.update(style, trafficEventsRef.value)
                FuelMapMarkersLayer.update(style, fuelStationsRef.value, fuelVisibleRef.value)
                CrimeHeatmapLayer.update(style, crimeGridRef.value, crimeVisibleRef.value)
            }
        }
        onDispose { /* MapView lifecycle handled by the DisposableEffect above */ }
    }

    // ── Map view ─────────────────────────────────────────────────────────
    AndroidView(
        modifier = modifier,
        factory  = { mapView },
        update   = { view ->
            view.getMapAsync { map ->
                // Camera: follow driver
                userLocation?.let { loc ->
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(loc.latitude, loc.longitude), 13.0
                        )
                    )
                }

                // Data: refresh traffic, incident, fuel, and crime layers when style is ready
                val style = map.style ?: return@getMapAsync
                if (style.getSource(TrafficOverlayLayer.SOURCE_ID) != null) {
                    TrafficOverlayLayer.update(style, trafficEvents, isTrafficVisible)
                    IncidentPinsLayer.update(style, trafficEvents)
                    FuelMapMarkersLayer.update(style, fuelStations, isFuelVisible)
                    CrimeHeatmapLayer.update(style, crimeGrid, isCrimeVisible)
                }
            }
        },
    )
}

// ── Private helpers ────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
private fun configureMap(
    map: MapLibreMap,
    mapStyleProvider: MapStyleProvider,
    onStyleReady: (Style) -> Unit = {},
) {
    map.setStyle(Style.Builder().fromJson(mapStyleProvider.loadDarkStyleJson())) { style ->
        // Default camera position
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(TileConfiguration.DEFAULT_CENTER_LATITUDE, TileConfiguration.DEFAULT_CENTER_LONGITUDE),
                TileConfiguration.DEFAULT_ZOOM,
            )
        )

        // Blue driver-dot
        val locationComponent = map.locationComponent
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions
                .builder(mapStyleProvider.context, style)
                .build()
        )
        locationComponent.isLocationComponentEnabled = true

        // Traffic layers + fuel layer + crime heatmap layer
        TrafficOverlayLayer.setup(style)
        IncidentPinsLayer.setup(style)
        FuelMapMarkersLayer.setup(style)
        CrimeHeatmapLayer.setup(style)

        onStyleReady(style)
    }
}
