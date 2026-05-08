package app.haulio.android.features.address.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.ConfidenceLevel
import app.haulio.android.services.address.ParsedAddress
import app.haulio.shared.navigation.models.GeoPoint

/**
 * Smart address search bar with a live suggestions dropdown.
 *
 * Behaviour:
 * 1. User types → parent ViewModel debounces 300 ms → calls AddressParser + GeocodingManager.
 * 2. [suggestions] list (≤ 5) animates in below the text field.
 * 3. Each row is an [AddressSuggestionChip]; tapping one calls [onSuggestionSelected].
 * 4. While [isLoading] is true a small spinner appears in the trailing slot.
 * 5. Clear button appears when [query] is non-empty.
 *
 * The composable is stateless — the owning screen/ViewModel drives [query] via [onQueryChange].
 *
 * @param query              Current text field value.
 * @param onQueryChange      Called on every keystroke.
 * @param suggestions        Up to 5 geocoded suggestions to show in the dropdown.
 * @param onSuggestionSelected  Called when the user taps a suggestion chip.
 * @param isLoading          Show spinner in trailing icon slot.
 * @param modifier           External modifier.
 */
@Composable
fun AddressSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<AddressSuggestion>,
    onSuggestionSelected: (AddressSuggestion) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value         = query,
            onValueChange = onQueryChange,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = { Text("Search address or paste shipping label…") },
            leadingIcon   = {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = "Search",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon  = {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier  = Modifier.padding(12.dp),
                        strokeWidth = 2.dp,
                    )
                    query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector        = Icons.Default.Close,
                            contentDescription = "Clear",
                        )
                    }
                    else -> Unit
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                keyboardType   = KeyboardType.Text,
                imeAction      = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    // Trigger an immediate geocode with current query (no wait for debounce)
                    if (query.isNotEmpty()) {
                        suggestions.firstOrNull()?.let { onSuggestionSelected(it) }
                    }
                },
            ),
            singleLine = true,
            shape      = RoundedCornerShape(12.dp),
        )

        AnimatedVisibility(
            visible = suggestions.isNotEmpty(),
            enter   = expandVertically(),
            exit    = shrinkVertically(),
        ) {
            Surface(
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                shadowElevation = 4.dp,
                color         = MaterialTheme.colorScheme.surface,
            ) {
                LazyColumn {
                    items(suggestions) { suggestion ->
                        AddressSuggestionChip(
                            suggestion = suggestion,
                            onSelect   = onSuggestionSelected,
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val previewSuggestions = listOf(
    AddressSuggestion(
        parsed = ParsedAddress(
            formatted = "1600 Pennsylvania Avenue, Washington, DC 20500-0001",
            zip = "20500", zip4 = "0001",
        ),
        coordinates = GeoPoint(38.897, -77.036),
        confidence  = ConfidenceLevel.VERIFIED_ZIP4,
        driverCount = 4,
    ),
    AddressSuggestion(
        parsed = ParsedAddress(
            formatted = "1600 Pennsylvania Avenue NW, Washington, DC 20006",
            zip = "20006",
        ),
        coordinates = GeoPoint(38.898, -77.037),
        confidence  = ConfidenceLevel.APPROXIMATE,
    ),
    AddressSuggestion(
        parsed = ParsedAddress(
            formatted = "1620 Pennsylvania Avenue NW, Washington, DC 20006",
            zip = "20006",
        ),
        coordinates = GeoPoint(38.899, -77.038),
        confidence  = ConfidenceLevel.NOT_FOUND,
    ),
)

@Preview(showBackground = true)
@Composable
private fun AddressSearchBarPreview() {
    MaterialTheme {
        AddressSearchBar(
            query               = "1600 Pennsylvania",
            onQueryChange       = {},
            suggestions         = previewSuggestions,
            onSuggestionSelected = {},
            isLoading           = false,
            modifier            = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddressSearchBarLoadingPreview() {
    MaterialTheme {
        AddressSearchBar(
            query               = "1600 Penn",
            onQueryChange       = {},
            suggestions         = emptyList(),
            onSuggestionSelected = {},
            isLoading           = true,
            modifier            = Modifier.padding(16.dp),
        )
    }
}
