package app.haulio.android.features.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.haulio.android.services.traffic.IncidentType

/**
 * Bottom sheet triggered by a long-press on the map.
 *
 * Shows 5 incident type buttons. On selection, calls [onReport] with the
 * chosen type and the [lat]/[lon] of the long-press location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentReportMenu(
    lat: Double,
    lon: Double,
    onReport: (IncidentType) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Icon mapping at file scope to avoid re-allocation on every recomposition
    val buttons: List<Pair<IncidentType, ImageVector>> = listOf(
        IncidentType.CONSTRUCTION to Icons.Default.Construction,
        IncidentType.ACCIDENT     to Icons.Default.Warning,
        IncidentType.ROAD_CLOSED  to Icons.Default.Block,
        IncidentType.POLICE       to Icons.Default.LocalPolice,
        IncidentType.POTHOLE      to Icons.Default.ReportProblem,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text     = "Report an Incident",
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            buttons.forEach { (type, icon) ->
                TextButton(
                    onClick  = { onReport(type) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Icon(
                            imageVector        = icon,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text     = type.label(),
                            style    = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
