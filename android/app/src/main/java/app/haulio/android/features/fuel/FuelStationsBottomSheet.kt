package app.haulio.android.features.fuel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.haulio.android.services.fuel.FuelPrice
import app.haulio.android.services.fuel.FuelStation

/**
 * Modal bottom sheet listing nearby fuel stations.
 *
 * Layout:
 *  ┌────────────────────────────────────────┐
 *  │  Regional Average card (EIA)           │
 *  │  ──────────────────────────────────── │
 *  │  Station 1 (sorted by distance)        │
 *  │  Station 2                             │
 *  │  ...                                   │
 *  └────────────────────────────────────────┘
 *
 * @param stations        Stations to display, sorted by [FuelStation.distanceMiles].
 * @param regionalAverage EIA weekly average for the driver's PADD district.
 * @param onSubmitPrice   Called with the selected station when "Submit Price" is tapped.
 * @param onDismiss       Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelStationsBottomSheet(
    stations: List<FuelStation>,
    regionalAverage: FuelPrice?,
    onSubmitPrice: (FuelStation) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            // ── Regional average header ────────────────────────────────────────
            item {
                Text(
                    text     = "Nearby Fuel Stations",
                    style    = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }

            regionalAverage?.let { avg ->
                item {
                    RegionalAverageCard(avg = avg, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Station list ────────────────────────────────────────────────────
            val sorted = stations.sortedBy { it.distanceMiles }
            if (sorted.isEmpty()) {
                item {
                    Text(
                        text     = "No stations found in this area.",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            } else {
                items(sorted, key = { it.id }) { station ->
                    FuelStationCard(
                        station  = station,
                        onSubmit = onSubmitPrice,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Regional average card
// ---------------------------------------------------------------------------

@Composable
private fun RegionalAverageCard(avg: FuelPrice, modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = "Regional Average (EIA)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Row {
                PriceChip("Regular", avg.regularUsd)
                avg.midGradeUsd?.let { Spacer(Modifier.width(8.dp)); PriceChip("Mid", it) }
                avg.premiumUsd?.let  { Spacer(Modifier.width(8.dp)); PriceChip("Prem", it) }
                avg.dieselUsd?.let   { Spacer(Modifier.width(8.dp)); PriceChip("Diesel", it) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Week of ${avg.weekOf}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PriceChip(label: String, price: Double) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text  = "$${String.format("%.2f", price)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
