package app.haulio.android.features.traffic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Slide-down banner that appears when a faster reroute is available.
 *
 * Shows:
 * - Route savings summary ("Faster route found – saves N min")
 * - [Switch] button to accept immediately
 * - [Dismiss] button to ignore
 * - Animated countdown bar; auto-switches after 10 s with no interaction
 */
@Composable
fun RerouteBanner(
    modifier: Modifier = Modifier,
    viewModel: TrafficViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = uiState.isRerouteActive && uiState.rerouteSuggestion != null,
        enter   = slideInVertically(initialOffsetY = { -it }),
        exit    = slideOutVertically(targetOffsetY  = { -it }),
        modifier = modifier,
    ) {
        val suggestion = uiState.rerouteSuggestion ?: return@AnimatedVisibility

        Surface(
            shape         = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            color         = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Countdown bar
                LinearProgressIndicator(
                    progress   = { uiState.rerouteCountdown / 10f },
                    modifier   = Modifier.fillMaxWidth(),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text  = "Faster route found — saves ${suggestion.savesMinutes} min",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Text(
                    text  = "Auto-switching in ${uiState.rerouteCountdown}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick  = viewModel::switchToFasterRoute,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Switch")
                    }
                    OutlinedButton(
                        onClick  = viewModel::dismissReroute,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}
