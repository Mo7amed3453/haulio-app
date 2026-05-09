package app.haulio.android.features.radar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.haulio.shared.radar.models.RadarAlertEvent
import app.haulio.shared.radar.models.RadarAlertLevel
import kotlinx.coroutines.delay

/**
 * Composable overlay that reacts to [RadarAlertEvent] and renders the appropriate UI:
 *
 * - [RadarAlertLevel.VISUAL_FAR]  — small top-centre toast.
 * - [RadarAlertLevel.AUDIO_MID]   — same toast with a louder message.
 * - [RadarAlertLevel.URGENT_CLOSE] — full-width red banner with pulsing scale animation.
 *
 * Audio chimes are triggered by [onChime] called from MapScreen via LaunchedEffect.
 * ToneGenerator does NOT require RECORD_AUDIO permission — it only needs
 * MODIFY_AUDIO_SETTINGS (normal protection level, granted automatically).
 *
 * @param alert     Current [RadarAlertEvent] to display; null = nothing shown.
 * @param onDismiss Called when the alert auto-expires or is dismissed.
 */
@Composable
fun RadarAlertOverlay(
    alert: RadarAlertEvent?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    // Auto-dismiss
    LaunchedEffect(alert) {
        if (alert != null) {
            val durationMs = when (alert.level) {
                RadarAlertLevel.URGENT_CLOSE -> 6_000L
                RadarAlertLevel.AUDIO_MID    -> 4_000L
                RadarAlertLevel.VISUAL_FAR   -> 3_000L
            }
            delay(durationMs)
            onDismiss()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (alert?.level) {
            RadarAlertLevel.VISUAL_FAR,
            RadarAlertLevel.AUDIO_MID -> {
                AnimatedVisibility(
                    visible  = alert != null,
                    enter    = slideInVertically(initialOffsetY = { -it }),
                    exit     = slideOutVertically(targetOffsetY  = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    RadarToast(alert)
                }
            }
            RadarAlertLevel.URGENT_CLOSE -> {
                AnimatedVisibility(
                    visible  = true,
                    enter    = fadeIn(),
                    exit     = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    UrgentRadarBanner(alert)
                }
            }
            null -> Unit
        }
    }
}

// ---------------------------------------------------------------------------
// Private sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun RadarToast(alert: RadarAlertEvent) {
    val distanceMi  = "%.1f".format(alert.distanceMeters / 1609.344)
    val speedLabel  = if (alert.camera.postedSpeedMph != null) " (${alert.camera.postedSpeedMph} mph)" else ""
    val text = when (alert.level) {
        RadarAlertLevel.VISUAL_FAR -> "Speed camera ahead — $distanceMi mi$speedLabel"
        RadarAlertLevel.AUDIO_MID  -> "Speed camera $distanceMi mi ahead — slow down$speedLabel"
        else                       -> ""
    }

    Surface(
        modifier       = Modifier
            .padding(horizontal = 48.dp, vertical = 12.dp)
            .fillMaxWidth(),
        shape          = RoundedCornerShape(24.dp),
        color          = Color(0xFF37474F),        // Blue-grey 800
        tonalElevation = 6.dp,
    ) {
        Text(
            text      = text,
            modifier  = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style     = MaterialTheme.typography.bodyMedium,
            color     = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UrgentRadarBanner(alert: RadarAlertEvent) {
    // Pulsing scale animation — 1.0 → 1.05 → 1.0
    val infiniteTransition = rememberInfiniteTransition(label = "radar-pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 1.0f,
        targetValue    = 1.05f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radar-scale",
    )

    val distanceMi = "%.2f".format(alert.distanceMeters / 1609.344)
    val speedLabel = if (alert.camera.postedSpeedMph != null) " — ${alert.camera.postedSpeedMph} mph zone" else ""

    Surface(
        modifier       = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape          = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color          = Color(0xFFB71C1C),        // Material Red 900
        tonalElevation = 12.dp,
    ) {
        Text(
            text       = "SPEED CAMERA — $distanceMi mi$speedLabel",
            modifier   = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            style      = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 18.sp,
                letterSpacing = 1.sp,
            ),
            color      = Color.White,
            textAlign  = TextAlign.Center,
        )
    }
}
