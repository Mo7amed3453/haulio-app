package app.haulio.android.features.navigation

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.haulio.android.features.navigation.ui.ExitGuideView
import app.haulio.android.features.navigation.ui.RouteDeviationDialog
import app.haulio.android.features.navigation.ui.TurnBannerView
import app.haulio.android.services.map.MapStyleProvider
import app.haulio.android.services.map.TileConfiguration
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent.getKoin
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import kotlin.time.Duration

private const val ROUTE_SOURCE_ID = "navigation-route-source"
private const val ROUTE_LAYER_ID  = "navigation-route-layer"

/**
 * Full-screen navigation container.
 *
 * Layout (Z order, bottom to top):
 *  1. MapLibre map with route polyline
 *  2. [TurnBannerView] anchored to top (below status bar)
 *  3. [ExitGuideView] overlaid below the banner (conditional)
 *  4. Bottom HUD: current instruction, speed, ETA
 *  5. [RouteDeviationDialog] (modal, when deviation detected)
 */
@SuppressLint("MissingPermission")
@Composable
fun NavigationScreen(
    viewModel: NavigationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapStyleProvider = remember { getKoin().get<MapStyleProvider>() }

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    // Lifecycle wiring
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── 1. Map ────────────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.getMapAsync { map ->
                    // Style initialisation (idempotent — MapLibre ignores if already loaded)
                    if (map.style == null) {
                        map.setStyle(
                            Style.Builder().fromJson(mapStyleProvider.loadDarkStyleJson())
                        ) { style ->
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(
                                        TileConfiguration.DEFAULT_CENTER_LATITUDE,
                                        TileConfiguration.DEFAULT_CENTER_LONGITUDE,
                                    ),
                                    TileConfiguration.DEFAULT_ZOOM,
                                )
                            )
                            // Add route source + layer placeholders
                            if (style.getSource(ROUTE_SOURCE_ID) == null) {
                                style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
                                style.addLayer(
                                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
                                        withProperties(
                                            PropertyFactory.lineColor("#4D90FE"),
                                            PropertyFactory.lineWidth(5f),
                                            PropertyFactory.lineCap("round"),
                                            PropertyFactory.lineJoin("round"),
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Update route polyline
                    val style = map.style
                    if (style != null && uiState.routePoints.isNotEmpty()) {
                        val source = style.getSource(ROUTE_SOURCE_ID) as? GeoJsonSource
                        val points = uiState.routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                        val line = LineString.fromLngLats(points)
                        source?.setGeoJson(
                            FeatureCollection.fromFeature(Feature.fromGeometry(line))
                        )
                    }

                    // Track user location
                    val loc = uiState.userLocation
                    if (loc != null) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude),
                                15.0,
                            )
                        )
                    }
                }
            },
        )

        // ── 2 & 3. Top overlays ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .align(Alignment.TopCenter),
        ) {
            TurnBannerView(
                step = uiState.currentStep,
                distanceMiles = uiState.distanceToStepMiles,
            )
            val exit = uiState.activeExit
            if (exit != null) {
                ExitGuideView(
                    exitInfo = exit,
                    visible = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ── 4. Bottom HUD ────────────────────────────────────────────────
        NavigationHud(
            step = uiState.currentStep,
            distanceMiles = uiState.distanceToStepMiles,
            speedMph = uiState.currentSpeedMph,
            eta = uiState.remainingEta,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // ── 5. Route deviation dialog ─────────────────────────────────────
        if (uiState.showDeviationDialog) {
            RouteDeviationDialog(
                onReasonSelected = viewModel::onDeviationReasonSelected,
                onDismiss = viewModel::onDeviationDismissed,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom HUD
// ---------------------------------------------------------------------------

@Composable
private fun NavigationHud(
    step: NavigationStep?,
    distanceMiles: Double,
    speedMph: Float,
    eta: Duration,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Current instruction text
            val instruction = step?.instruction ?: "Calculating route…"
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            // Distance to next turn
            val distLabel = when {
                distanceMiles >= 1.0 -> "%.1f mi".format(distanceMiles)
                else -> "%.0f ft".format(distanceMiles * 5280)
            }
            Text(
                text = distLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            // Speed + ETA row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedChip(speedMph = speedMph)
                EtaChip(eta = eta)
            }
        }
    }
}

@Composable
private fun SpeedChip(speedMph: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "%.0f".format(speedMph),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "mph",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EtaChip(eta: Duration) {
    val etaText = formatEta(eta)
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "ETA",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = etaText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatEta(eta: Duration): String {
    val totalMinutes = eta.inWholeMinutes.toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
}
