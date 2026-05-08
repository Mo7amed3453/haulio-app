package app.haulio.android.features.map

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.haulio.android.services.location.LocationPoint
import app.haulio.android.services.map.MapStyleProvider
import app.haulio.android.services.map.TileConfiguration
import org.koin.java.KoinJavaComponent.getKoin
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@SuppressLint("MissingPermission")
@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    userLocation: LocationPoint?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapStyleProvider = remember { getKoin().get<MapStyleProvider>() }
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            view.getMapAsync { map ->
                configureMap(map, mapStyleProvider)
                if (userLocation != null) {
                    map.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            org.maplibre.android.geometry.LatLng(userLocation.latitude, userLocation.longitude),
                            13.0
                        )
                    )
                }
            }
        }
    )
}

private fun configureMap(
    map: MapLibreMap,
    mapStyleProvider: MapStyleProvider
) {
    map.setStyle(Style.Builder().fromJson(mapStyleProvider.loadDarkStyleJson())) { style ->
        map.moveCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                org.maplibre.android.geometry.LatLng(
                    TileConfiguration.DEFAULT_CENTER_LATITUDE,
                    TileConfiguration.DEFAULT_CENTER_LONGITUDE
                ),
                TileConfiguration.DEFAULT_ZOOM
            )
        )
        val locationComponent = map.locationComponent
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(
                mapStyleProvider.context,
                style
            ).build()
        )
        locationComponent.isLocationComponentEnabled = true
    }
}
