package app.haulio.android.features.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Dialog triggered by a long-press on the map.
 * Asks the driver if they want to report a speed camera at ([lat], [lng]).
 * Includes an optional speed limit picker (10–80 mph in 5 mph steps).
 *
 * @param lat       Latitude of the long-pressed location.
 * @param lng       Longitude of the long-pressed location.
 * @param onSubmit  Called with the speed limit in mph (or null if not specified).
 * @param onDismiss Called when the dialog is cancelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitCameraDialog(
    lat: Double,
    lng: Double,
    onSubmit: (speedMph: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeSpeed by remember { mutableStateOf(false) }
    var speedSlider  by remember { mutableFloatStateOf(35f) }

    // Speed in 5 mph increments: 10..80
    val speedMph = if (includeSpeed) {
        (speedSlider.roundToInt() / 5 * 5).coerceIn(10, 80)
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Report speed camera?")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text  = "Long-pressed at %.5f, %.5f".format(lat, lng),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Add speed limit?", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { includeSpeed = !includeSpeed }) {
                        Text(if (includeSpeed) "Remove" else "Add")
                    }
                }

                if (includeSpeed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text  = "Speed limit: $speedMph mph",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Slider(
                        value         = speedSlider,
                        onValueChange = { speedSlider = it },
                        valueRange    = 10f..80f,
                        steps         = 13,  // (80-10)/5 - 1 = 13 steps
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(speedMph) }) {
                Text("Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
