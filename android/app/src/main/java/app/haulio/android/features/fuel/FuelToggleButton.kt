package app.haulio.android.features.fuel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * FAB that opens the fuel stations bottom sheet.
 * Uses [Icons.Default.LocalGasStation] (⛽) as its icon.
 * Container color highlights when [isActive] is true.
 */
@Composable
fun FuelToggleButton(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick        = onToggle,
        modifier       = modifier,
        containerColor = if (isActive)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Icon(
            imageVector        = Icons.Default.LocalGasStation,
            contentDescription = if (isActive) "Hide fuel stations" else "Show fuel stations",
            tint               = if (isActive)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
