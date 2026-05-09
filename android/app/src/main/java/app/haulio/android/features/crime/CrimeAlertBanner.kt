package app.haulio.android.features.crime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * Slide-down red banner displayed when the driver enters a high-risk crime cell.
 *
 * - Slides in from the top.
 * - Auto-hides after 30 seconds if the user does not dismiss.
 * - Dismiss button clears the alert via [CrimeViewModel.dismissAlert].
 */
@Composable
fun CrimeAlertBanner(
    modifier: Modifier = Modifier,
    viewModel: CrimeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val alert = uiState.pendingAlert

    // Auto-dismiss after 30 seconds
    LaunchedEffect(alert?.cellId) {
        if (alert != null) {
            delay(30_000L)
            viewModel.dismissAlert()
        }
    }

    AnimatedVisibility(
        visible  = alert != null,
        enter    = slideInVertically(initialOffsetY = { -it }),
        exit     = slideOutVertically(targetOffsetY  = { -it }),
        modifier = modifier,
    ) {
        Surface(
            shape          = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            color          = Color(0xFFD32F2F),   // Material Red 700
            tonalElevation = 8.dp,
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector        = Icons.Default.Warning,
                    contentDescription = null,
                    tint               = Color.White,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text     = buildAlertText(alert?.topCrimeType),
                    style    = MaterialTheme.typography.titleSmall,
                    color    = Color.White,
                    modifier = Modifier.weight(1f),
                )

                IconButton(onClick = viewModel::dismissAlert) {
                    Icon(
                        imageVector        = Icons.Default.Close,
                        contentDescription = "Dismiss crime alert",
                        tint               = Color.White,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

private fun buildAlertText(topCrimeType: String?): String {
    val crimeLabel = topCrimeType
        ?.lowercase()
        ?.replaceFirstChar { it.uppercase() }
        ?: "Crime"
    return "\u26a0\ufe0f High-risk area \u2014 Stay alert ($crimeLabel reported nearby)"
}
