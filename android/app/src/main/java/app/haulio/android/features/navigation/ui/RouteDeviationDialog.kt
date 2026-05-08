package app.haulio.android.features.navigation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.haulio.android.services.navigation.DeviationReason
import kotlinx.coroutines.delay

/**
 * Material3 dialog shown when the KMM RouteTracker emits a [RouteDeviation].
 *
 * Prompts the driver for the reason the road was skipped so the data can be
 * stored locally for future crowdsource features.
 *
 * - Auto-dismisses after 30 s with no interaction ([onDismiss] is called).
 * - Four stacked action buttons: Road Closed, Accident, Other, Dismiss.
 * - Selecting a reason calls [onReasonSelected] then triggers reroute.
 * - Dismissing calls [onDismiss] and continues without rerouting.
 *
 * @param onReasonSelected callback with the driver-supplied reason; the caller
 *   should initiate rerouting when this is invoked.
 * @param onDismiss callback when the dialog is dismissed without a reason.
 */
@Composable
fun RouteDeviationDialog(
    onReasonSelected: (DeviationReason) -> Unit,
    onDismiss: () -> Unit,
) {
    // Auto-dismiss after 30 seconds of inactivity.
    LaunchedEffect(Unit) {
        delay(30_000)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Why did you skip this road?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                DeviationOptionButton(
                    label = "Road Closed",
                    onClick = { onReasonSelected(DeviationReason.ROAD_CLOSED) },
                )
                DeviationOptionButton(
                    label = "Accident",
                    onClick = { onReasonSelected(DeviationReason.ACCIDENT) },
                )
                DeviationOptionButton(
                    label = "Other",
                    onClick = { onReasonSelected(DeviationReason.OTHER) },
                )
            }
        },
        confirmButton = { /* handled inside [text] slot as stacked buttons */ },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
    )
}

@Composable
private fun DeviationOptionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(label)
    }
}
