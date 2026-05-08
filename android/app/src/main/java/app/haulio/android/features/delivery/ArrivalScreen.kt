package app.haulio.android.features.delivery

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haulio.android.features.delivery.ui.DeliveryConfirmButton
import app.haulio.android.features.delivery.ui.UnitSelectorDialog
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.ConfidenceLevel
import app.haulio.android.services.address.MockDeliveryLogger
import app.haulio.android.services.address.ParsedAddress
import app.haulio.android.services.location.LocationPoint
import app.haulio.android.services.location.ObserveLocationUseCase
import app.haulio.shared.navigation.models.GeoPoint
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.flow
import org.koin.androidx.compose.koinViewModel

/**
 * Last-mile precision screen shown when navigation reaches within 50 m of the destination.
 *
 * Shows:
 * - Distance card (real-time metres to destination).
 * - [UnitSelectorDialog] when address has a unit number and driver just entered the radius.
 * - [DeliveryConfirmButton] FAB stack (Mark Delivered + optional photo capture).
 * - Loading indicator while Supabase write is in-flight.
 * - Snackbar for errors.
 *
 * @param suggestion        The geocoded delivery destination.
 * @param onDeliveryComplete  Called after successful delivery log → navigate back/home.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ArrivalScreen(
    suggestion: AddressSuggestion,
    onDeliveryComplete: () -> Unit,
    viewModel: ArrivalViewModel = koinViewModel(),
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarState  = remember { SnackbarHostState() }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Photo capture launcher (CameraX capture activity or TakePicture contract)
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            // photoUri already stored via createTempUri below
        }
    }

    // Initialise destination on first composition
    LaunchedEffect(suggestion) {
        viewModel.setDestination(suggestion)
    }

    // Navigate home once delivered
    LaunchedEffect(uiState.isDelivered) {
        if (uiState.isDelivered) onDeliveryComplete()
    }

    // Show errors
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Last-Mile Delivery") }) },
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            DeliveryConfirmButton(
                isNearDestination = uiState.isNearDestination,
                isDelivered       = uiState.isDelivered,
                showPhotoOption   = !uiState.isDelivered,
                onMarkDelivered   = viewModel::onMarkDelivered,
                onTakePhoto       = {
                    if (cameraPermission.status.isGranted) {
                        // Create temp URI and launch camera; URI stored in ViewModel after capture
                        val tempUri = Uri.parse("content://placeholder") // replace with FileProvider URI
                        viewModel.onPhotoCaptured(tempUri.toString())
                        photoLauncher.launch(tempUri)
                    } else {
                        cameraPermission.launchPermissionRequest()
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Destination card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text  = "Delivering to",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text       = suggestion.parsed.formatted,
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        uiState.selectedUnit?.let { unit ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = "Unit: $unit",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Distance indicator
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = "Distance to destination",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val dist = uiState.distanceMeters
                    Text(
                        text  = if (dist == Double.MAX_VALUE) "—"
                                else if (dist < 1000) "${"%.0f".format(dist)} m"
                                else "${"%.1f".format(dist / 1000)} km",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isNearDestination) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Loading overlay while Supabase write is in-flight
                if (uiState.isLoggingDelivery) {
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }

            // Unit selector dialog
            if (uiState.showUnitSelector) {
                UnitSelectorDialog(
                    units     = uiState.availableUnits,
                    onConfirm = viewModel::onUnitSelected,
                    onDismiss = viewModel::onUnitSelectorDismissed,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview (self-contained)
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun ArrivalScreenPreview() {
    val mockSuggestion = AddressSuggestion(
        parsed = ParsedAddress(
            formatted = "1600 Pennsylvania Avenue, Washington, DC 20500",
            zip = "20500", unitNumber = "Apt 4B",
        ),
        coordinates = GeoPoint(38.897, -77.036),
        confidence  = ConfidenceLevel.VERIFIED_ZIP4,
    )

    // Minimal preview using mocks (cannot use koinViewModel in Preview)
    MaterialTheme {
        // Show a simplified version for preview
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors   = CardDefaults.cardColors(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Delivering to", style = MaterialTheme.typography.labelMedium)
                Text(mockSuggestion.parsed.formatted, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
