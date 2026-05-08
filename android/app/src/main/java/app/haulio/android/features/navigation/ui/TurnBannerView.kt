package app.haulio.android.features.navigation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.haulio.shared.navigation.models.ManeuverType
import app.haulio.shared.navigation.models.NavigationStep

/**
 * Top banner showing the upcoming maneuver arrow, street name, and distance.
 *
 * Updates with a cross-fade + slide animation whenever [step] changes.
 *
 * @param step the current [NavigationStep], or null when no active route.
 * @param distanceMiles remaining distance to the maneuver.
 */
@Composable
fun TurnBannerView(
    step: NavigationStep?,
    distanceMiles: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInVertically { -it } + fadeIn()) togetherWith
                        (slideOutVertically { it } + fadeOut())
            },
            label = "turn_banner_transition",
        ) { currentStep ->
            if (currentStep == null) {
                // No active step — show blank row with minimum height
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                ) { /* intentionally empty */ }
            } else {
                TurnBannerContent(step = currentStep, distanceMiles = distanceMiles)
            }
        }
    }
}

@Composable
private fun TurnBannerContent(step: NavigationStep, distanceMiles: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Maneuver icon
        Icon(
            imageVector = step.maneuverType.toIcon(),
            contentDescription = step.maneuverType.name,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Street name + instruction
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
        ) {
            val streetLabel = step.streetName ?: step.instruction
            Text(
                text = streetLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (step.streetName != null && step.instruction != step.streetName) {
                Text(
                    text = step.instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Distance to maneuver
        val distanceLabel = formatDistance(distanceMiles)
        Text(
            text = distanceLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatDistance(miles: Double): String = when {
    miles >= 10.0  -> "%.0f mi".format(miles)
    miles >= 1.0   -> "%.1f mi".format(miles)
    else           -> "%.0f ft".format(miles * 5280)
}

private fun ManeuverType.toIcon(): ImageVector = when (this) {
    ManeuverType.LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT ->
        Icons.AutoMirrored.Filled.ArrowBack
    ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT ->
        Icons.AutoMirrored.Filled.ArrowForward
    ManeuverType.UTURN_LEFT ->
        Icons.Filled.Undo
    ManeuverType.UTURN_RIGHT ->
        Icons.Filled.Redo
    ManeuverType.MERGE ->
        Icons.Filled.CallMerge
    ManeuverType.RAMP_RIGHT, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_STRAIGHT,
    ManeuverType.EXIT_RIGHT, ManeuverType.EXIT_LEFT ->
        Icons.Filled.CallMade
    ManeuverType.ROUNDABOUT_ENTER, ManeuverType.ROUNDABOUT_EXIT ->
        Icons.Filled.PlayArrow
    ManeuverType.DESTINATION ->
        Icons.Filled.Flag
    ManeuverType.START ->
        Icons.Filled.PlayArrow
    else ->
        Icons.AutoMirrored.Filled.TrendingFlat
}
