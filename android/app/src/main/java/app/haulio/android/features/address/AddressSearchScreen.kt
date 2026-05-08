package app.haulio.android.features.address

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haulio.android.features.address.ui.AddressSearchBar
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.ConfidenceLevel
import app.haulio.android.services.address.MockAddressParser
import app.haulio.android.services.address.MockDeliveryLogger
import app.haulio.android.services.address.MockGeocodingManager
import app.haulio.android.services.address.ParsedAddress
import app.haulio.shared.navigation.models.GeoPoint
import org.koin.androidx.compose.koinViewModel

/**
 * Full-screen address search.
 *
 * Features:
 * - [AddressSearchBar] with live suggestions dropdown.
 * - Tapping a suggestion navigates to [onNavigateToCoordinates].
 * - FAB opens the barcode scanner screen.
 * - Snackbar surfaces geocoding errors.
 *
 * @param onNavigateToCoordinates  Callback when the user selects a suggestion and wants to navigate.
 * @param onOpenScanner            Opens the [BarcodeScannerScreen].
 * @param prefillRaw               Optional pre-filled address text from the barcode scanner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressSearchScreen(
    onNavigateToCoordinates: (GeoPoint) -> Unit,
    onOpenScanner: () -> Unit,
    prefillRaw: String? = null,
    viewModel: AddressSearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-fill from scanner result
    LaunchedEffect(prefillRaw) {
        prefillRaw?.let { viewModel.prefillQuery(it) }
    }

    // Navigate when the user selects a suggestion
    LaunchedEffect(uiState.selectedSuggestion) {
        uiState.selectedSuggestion?.let { onNavigateToCoordinates(it.coordinates) }
    }

    // Show geocoding errors in a snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Deliver to…") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenScanner) {
                Icon(
                    imageVector        = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan shipping label",
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AddressSearchBar(
                    query               = uiState.query,
                    onQueryChange       = viewModel::onQueryChanged,
                    suggestions         = uiState.suggestions,
                    onSuggestionSelected = viewModel::onSuggestionSelected,
                    isLoading           = uiState.isLoading,
                    modifier            = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.suggestions.isEmpty() && uiState.query.isEmpty() && !uiState.isLoading) {
                    Text(
                        text     = "Type an address or scan a package barcode.",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview (self-contained — no Koin required)
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun AddressSearchScreenPreview() {
    val previewViewModel = AddressSearchViewModel(
        addressParser  = MockAddressParser(),
        geocodingManager = MockGeocodingManager(),
        deliveryLogger = MockDeliveryLogger(),
    )
    MaterialTheme {
        AddressSearchBar(
            query = "1600 Pennsylvania",
            onQueryChange = {},
            suggestions = listOf(
                AddressSuggestion(
                    parsed = ParsedAddress(
                        formatted = "1600 Pennsylvania Avenue, Washington, DC 20500-0001",
                        zip = "20500", zip4 = "0001",
                    ),
                    coordinates = GeoPoint(38.897, -77.036),
                    confidence  = ConfidenceLevel.VERIFIED_ZIP4,
                    driverCount = 2,
                ),
            ),
            onSuggestionSelected = {},
            isLoading = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}
