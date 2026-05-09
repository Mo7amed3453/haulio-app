package app.haulio.android.features.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haulio.android.features.crime.CrimeAlertBanner
import app.haulio.android.features.crime.CrimeToggleButton
import app.haulio.android.features.crime.CrimeViewModel
import app.haulio.android.features.fuel.FuelStationsBottomSheet
import app.haulio.android.features.fuel.FuelToggleButton
import app.haulio.android.features.fuel.FuelViewModel
import app.haulio.android.features.fuel.SubmitPriceDialog
import app.haulio.android.features.incident.IncidentReportMenu
import app.haulio.android.features.incident.IncidentReportViewModel
import app.haulio.android.features.search.SearchBar
import app.haulio.android.features.traffic.IncidentDetailsBottomSheet
import app.haulio.android.features.traffic.RerouteBanner
import app.haulio.android.features.traffic.TrafficToggleButton
import app.haulio.android.features.traffic.TrafficViewModel
import app.haulio.android.services.fuel.FuelStation
import app.haulio.android.services.traffic.TrafficEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun MapScreen(
    onNavigateToAddressSearch: () -> Unit = {},
    viewModel: MapViewModel               = koinViewModel(),
    trafficViewModel: TrafficViewModel    = koinViewModel(),
    incidentViewModel: IncidentReportViewModel = koinViewModel(),
    fuelViewModel: FuelViewModel          = koinViewModel(),
    crimeViewModel: CrimeViewModel        = koinViewModel(),
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val trafficState    by trafficViewModel.uiState.collectAsStateWithLifecycle()
    val incidentState   by incidentViewModel.uiState.collectAsStateWithLifecycle()
    val fuelState       by fuelViewModel.uiState.collectAsStateWithLifecycle()
    val crimeState      by crimeViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Long-press state: coordinates for incident reporting
    var longPressLatLon by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Tapped incident state: for showing details bottom sheet
    var tappedIncident by remember { mutableStateOf<TrafficEvent?>(null) }

    // Tapped fuel station state: for showing submit price dialog
    var tappedFuelStation by remember { mutableStateOf<FuelStation?>(null) }

    // Station selected from list for price submission
    var submitPriceStation by remember { mutableStateOf<FuelStation?>(null) }

    // Snackbar for incident report success
    LaunchedEffect(incidentState.successMessage) {
        incidentState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            incidentViewModel.clearSuccessMessage()
        }
    }

    // Snackbar for fuel submit feedback
    LaunchedEffect(fuelState.submitSuccess, fuelState.submitError) {
        (fuelState.submitSuccess ?: fuelState.submitError)?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            fuelViewModel.clearSubmitFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Base map
            MapLibreView(
                modifier          = Modifier.fillMaxSize(),
                userLocation      = uiState.userLocation,
                trafficEvents     = if (trafficState.isTrafficVisible) trafficState.events else emptyList(),
                isTrafficVisible  = trafficState.isTrafficVisible,
                fuelStations      = fuelState.stations,
                isFuelVisible     = fuelState.isFuelVisible,
                crimeGrid         = crimeState.cells,
                isCrimeVisible    = crimeState.isCrimeVisible,
                onMapLongClick    = { lat, lon -> longPressLatLon = Pair(lat, lon) },
                onIncidentTapped  = { incidentId ->
                    tappedIncident = trafficState.events.firstOrNull { it.id == incidentId }
                },
                onFuelStationTapped = { stationId ->
                    tappedFuelStation = fuelState.stations.firstOrNull { it.id == stationId }
                },
            )

            // Crime alert banner slides in from the top (above reroute banner)
            CrimeAlertBanner(
                modifier  = Modifier.align(Alignment.TopCenter),
                viewModel = crimeViewModel,
            )

            // Reroute banner slides in from the top
            RerouteBanner(
                modifier = Modifier.align(Alignment.TopCenter),
                viewModel = trafficViewModel,
            )

            // Address search bar
            SearchBar(
                modifier        = Modifier.padding(16.dp),
                onSearchChanged = viewModel::onSearchChanged,
                onFuelTap       = viewModel::onFuelTap,
            )

            // Traffic toggle FAB (bottom-start, third row)
            TrafficToggleButton(
                isVisible = trafficState.isTrafficVisible,
                onToggle  = trafficViewModel::toggleTrafficOverlay,
                modifier  = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 144.dp),
            )

            // Fuel toggle FAB (bottom-start, second row)
            FuelToggleButton(
                isActive = fuelState.isFuelVisible,
                onToggle = fuelViewModel::toggleFuelOverlay,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 80.dp),
            )

            // Crime toggle FAB (bottom-start, first row)
            CrimeToggleButton(
                isVisible = crimeState.isCrimeVisible,
                onToggle  = crimeViewModel::toggleCrimeOverlay,
                modifier  = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp),
            )

            // Address search FAB (bottom-end)
            FloatingActionButton(
                onClick  = onNavigateToAddressSearch,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = "Search Address",
                )
            }
        }
    }

    // Incident report sheet — appears after a map long-press
    longPressLatLon?.let { (lat, lon) ->
        IncidentReportMenu(
            lat      = lat,
            lon      = lon,
            onReport = { type ->
                incidentViewModel.reportIncident(type, lat, lon)
                longPressLatLon = null
            },
            onDismiss = { longPressLatLon = null },
        )
    }

    // Incident details sheet — appears after tapping a map pin
    tappedIncident?.let { event ->
        IncidentDetailsBottomSheet(
            event     = event,
            onDismiss = { tappedIncident = null },
        )
    }

    // Fuel stations sheet — opens when fuel overlay is active
    if (fuelState.isFuelVisible) {
        FuelStationsBottomSheet(
            stations        = fuelState.stations,
            regionalAverage = fuelState.regionalAverage,
            onSubmitPrice   = { station ->
                submitPriceStation = station
            },
            onDismiss = fuelViewModel::toggleFuelOverlay,
        )
    }

    // Fuel station map-tap popup → submit price dialog
    tappedFuelStation?.let { station ->
        SubmitPriceDialog(
            station   = station,
            onSubmit  = { price, grade ->
                fuelViewModel.submitPrice(station.id, price, grade)
                tappedFuelStation = null
            },
            onDismiss = { tappedFuelStation = null },
        )
    }

    // Submit price dialog opened from the bottom sheet list
    submitPriceStation?.let { station ->
        SubmitPriceDialog(
            station   = station,
            onSubmit  = { price, grade ->
                fuelViewModel.submitPrice(station.id, price, grade)
                submitPriceStation = null
            },
            onDismiss = { submitPriceStation = null },
        )
    }
}
