package app.haulio.android.features.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.haulio.android.services.traffic.RouteOption
import app.haulio.shared.navigation.models.GeoPoint
import org.koin.androidx.compose.koinViewModel

/**
 * Modal bottom sheet presenting 3 route alternatives.
 *
 * Auto-selects the fastest route after an 8-second countdown (visible
 * progress bar). The driver can tap any card to override the selection.
 * Persists the "always show alternatives" toggle in DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteCompareSheet(
    origin: GeoPoint,
    destination: GeoPoint,
    onRoutePicked: (RouteOption) -> Unit,
    onDismiss: () -> Unit,
    viewModel: RouteCompareViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(origin, destination) {
        viewModel.fetchAlternatives(origin, destination)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier       = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = "Choose Route",
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text  = "Always show",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked         = uiState.alwaysShowAlternatives,
                        onCheckedChange = viewModel::toggleAlwaysShow,
                    )
                }
            }

            // Countdown progress bar
            if (uiState.isCountingDown) {
                Column {
                    LinearProgressIndicator(
                        progress    = { uiState.countdownSeconds / 8f },
                        modifier    = Modifier.fillMaxWidth(),
                        trackColor  = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text  = "Auto-selecting fastest in ${uiState.countdownSeconds}s…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Loading state
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text  = "Finding best routes…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Error state
            uiState.errorMessage?.let { err ->
                Text(
                    text  = err,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Route cards
            uiState.routes.forEach { route ->
                RouteCard(
                    route      = route,
                    isSelected = route.id == uiState.selectedRouteId,
                    onClick    = {
                        viewModel.selectRoute(route.id)
                        onRoutePicked(route)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
