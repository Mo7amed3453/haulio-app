package app.haulio.android.features.crime

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * FAB that toggles the crime heatmap overlay on/off.
 *
 * Uses [Icons.Default.Shield] (requires material-icons-extended).
 * If the project does not include extended icons, replace with
 * [Icons.Default.Warning] or [Icons.Default.Info] from the core set.
 */
@Composable
fun CrimeToggleButton(
    isVisible: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick        = onToggle,
        modifier       = modifier,
        containerColor = if (isVisible)
            MaterialTheme.colorScheme.error
        else
            MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Icon(
            imageVector        = Icons.Default.Shield,
            contentDescription = if (isVisible) "Hide crime heatmap" else "Show crime heatmap",
            tint               = if (isVisible)
                MaterialTheme.colorScheme.onError
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
