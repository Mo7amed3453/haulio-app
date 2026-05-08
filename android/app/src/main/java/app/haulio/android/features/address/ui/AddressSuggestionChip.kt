package app.haulio.android.features.address.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.ConfidenceLevel
import app.haulio.android.services.address.ParsedAddress
import app.haulio.shared.navigation.models.GeoPoint

/**
 * A single row in the address suggestion dropdown.
 *
 * Displays:
 * - Location pin icon
 * - Formatted address (primary line)
 * - ZIP+4 if available (secondary line)
 * - [ConfidenceBadge] on the trailing edge
 * - Optional "Verified by N drivers" badge when [suggestion.driverCount] >= 1
 *
 * @param suggestion  The [AddressSuggestion] to render.
 * @param onSelect    Called when the user taps this chip.
 */
@Composable
fun AddressSuggestionChip(
    suggestion: AddressSuggestion,
    onSelect: (AddressSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSelect(suggestion) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Default.LocationOn,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = suggestion.parsed.formatted,
                    style    = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val zip4Line = buildZip4Line(suggestion.parsed)
                if (zip4Line != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text  = zip4Line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (suggestion.driverCount >= 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Person,
                            contentDescription = null,
                            tint               = Color(0xFF1565C0),
                            modifier           = Modifier.size(12.dp),
                        )
                        Text(
                            text  = "Verified by ${suggestion.driverCount} driver${if (suggestion.driverCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            ConfidenceBadge(level = suggestion.confidence)
        }
    }
}

private fun buildZip4Line(parsed: ParsedAddress): String? {
    val zip  = parsed.zip.takeIf { it.isNotEmpty() } ?: return null
    val zip4 = parsed.zip4
    return if (zip4 != null) "ZIP+4: $zip-$zip4" else "ZIP: $zip"
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun SuggestionChipVerifiedPreview() {
    MaterialTheme {
        AddressSuggestionChip(
            suggestion = AddressSuggestion(
                parsed = ParsedAddress(
                    formatted    = "1600 Pennsylvania Avenue, Washington, DC 20500-0001",
                    zip          = "20500",
                    zip4         = "0001",
                ),
                coordinates = GeoPoint(38.897, -77.036),
                confidence  = ConfidenceLevel.VERIFIED_ZIP4,
                driverCount = 3,
            ),
            onSelect = {},
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SuggestionChipApproxPreview() {
    MaterialTheme {
        AddressSuggestionChip(
            suggestion = AddressSuggestion(
                parsed = ParsedAddress(
                    formatted = "160 Pennsylvania Street, San Francisco, CA 94107",
                    zip       = "94107",
                ),
                coordinates = GeoPoint(37.759, -122.397),
                confidence  = ConfidenceLevel.APPROXIMATE,
            ),
            onSelect = {},
            modifier = Modifier.padding(8.dp),
        )
    }
}
