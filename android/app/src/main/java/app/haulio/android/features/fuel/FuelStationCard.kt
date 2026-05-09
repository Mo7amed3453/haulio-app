package app.haulio.android.features.fuel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.haulio.android.services.fuel.FuelStation

/**
 * Card displaying a single fuel station entry in the bottom sheet.
 *
 * In collapsed mode shows: brand emoji + name + regular price + distance + last updated.
 * Tapping expands to show all available grades and a Submit Price button.
 *
 * @param station     The [FuelStation] to display.
 * @param onSubmit    Called when the driver taps "Submit Price".
 * @param modifier    Optional [Modifier].
 */
@Composable
fun FuelStationCard(
    station: FuelStation,
    onSubmit: (FuelStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick    = { expanded = !expanded },
        modifier   = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation  = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Header row ──────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text  = brandEmoji(station.brand),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = station.name ?: station.brand ?: "Fuel Station",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text  = "${station.distanceMiles} mi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val price = station.latestPrice
                    if (price != null) {
                        Text(
                            text  = "$${String.format("%.2f", price.regularUsd)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text  = "No price",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    station.lastReportedTs?.let { ts ->
                        Text(
                            text  = "Updated ${relativeTime(ts)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Expanded grades + submit ─────────────────────────────────
            if (expanded) {
                Spacer(Modifier.height(12.dp))

                val price = station.latestPrice
                if (price != null) {
                    FuelGradeRow("Regular",   price.regularUsd)
                    price.midGradeUsd?.let { FuelGradeRow("Mid-Grade", it) }
                    price.premiumUsd?.let  { FuelGradeRow("Premium",   it) }
                    price.dieselUsd?.let   { FuelGradeRow("Diesel",    it) }
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick  = { onSubmit(station) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Submit Price")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

@Composable
private fun FuelGradeRow(label: String, price: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = "$${String.format("%.3f", price)}/gal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun brandEmoji(brand: String?): String = when {
    brand == null                          -> "\u26FD"  // ⛽
    brand.contains("Shell", ignoreCase = true)   -> "\uD83D\uDEE2\uFE0F"  // 🛢️ Shell
    brand.contains("Chevron", ignoreCase = true) -> "\u26FD"
    brand.contains("Costco", ignoreCase = true)  -> "\uD83D\uDED2"  // 🛒 Costco
    brand.contains("Arco", ignoreCase = true)    -> "\u26FD"
    else                                         -> "\u26FD"
}

private fun relativeTime(tsMs: Long): String {
    val diffMs = System.currentTimeMillis() - tsMs
    return when {
        diffMs < 60_000L                              -> "just now"
        diffMs < 3_600_000L                           -> "${diffMs / 60_000L}m ago"
        diffMs < 86_400_000L                          -> "${diffMs / 3_600_000L}h ago"
        else                                          -> "${diffMs / 86_400_000L}d ago"
    }
}
