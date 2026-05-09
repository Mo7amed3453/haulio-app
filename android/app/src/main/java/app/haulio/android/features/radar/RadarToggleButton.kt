package app.haulio.android.features.radar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * FAB that toggles the radar detector overlay on/off.
 *
 * Uses [Icons.Default.Speed] (speedometer) as its icon.
 * Container color highlights when [isActive] is true.
 *
 * If the driver is in a banned jurisdiction, the button is visually neutral
 * (banned state is enforced by [RadarViewModel.toggleRadarOverlay], which no-ops).
 */
@Composable
fun RadarToggleButton(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick        = onToggle,
        modifier       = modifier,
        containerColor = if (isActive)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Icon(
            imageVector        = Icons.Default.Speed,
            contentDescription = if (isActive) "Hide speed cameras" else "Show speed cameras",
            tint               = if (isActive)
                MaterialTheme.colorScheme.onError
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
