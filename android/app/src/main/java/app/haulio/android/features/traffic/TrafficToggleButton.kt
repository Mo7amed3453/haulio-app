package app.haulio.android.features.traffic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * FAB that toggles the traffic congestion overlay on/off.
 * State is persisted via DataStore in [TrafficViewModel].
 */
@Composable
fun TrafficToggleButton(
    isVisible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick           = onToggle,
        modifier          = modifier,
        containerColor    = if (isVisible)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Icon(
            imageVector        = Icons.Default.Traffic,
            contentDescription = if (isVisible) "Hide traffic overlay" else "Show traffic overlay",
            tint               = if (isVisible)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
