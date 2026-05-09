package app.haulio.android.features.route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.haulio.android.services.traffic.CongestionLevel
import app.haulio.android.services.traffic.RouteOption

private val CongestionGreen  = Color(0xFF00E676)
private val CongestionYellow = Color(0xFFFFD740)
private val CongestionRed    = Color(0xFFFF5252)

fun CongestionLevel.toColor(): Color = when (this) {
    CongestionLevel.CLEAR    -> CongestionGreen
    CongestionLevel.MODERATE -> CongestionYellow
    CongestionLevel.HEAVY    -> CongestionRed
}

fun CongestionLevel.toLabel(): String = when (this) {
    CongestionLevel.CLEAR    -> "Clear"
    CongestionLevel.MODERATE -> "Moderate"
    CongestionLevel.HEAVY    -> "Heavy"
}

fun CongestionLevel.toFraction(): Float = when (this) {
    CongestionLevel.CLEAR    -> 0.2f
    CongestionLevel.MODERATE -> 0.55f
    CongestionLevel.HEAVY    -> 0.9f
}

@Composable
fun RouteCard(
    route: RouteOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val congestionColor = route.congestionLevel.toColor()
    val borderColor     = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val cardElevation   = if (isSelected) 4.dp else 1.dp

    Card(
        modifier  = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Label row
            Row(
                modifier       = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = route.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                // Delay badge
                if (route.delayMinutes > 0) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = congestionColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text  = "+${route.delayMinutes} min traffic",
                            style = MaterialTheme.typography.labelSmall,
                            color = congestionColor,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ETA + Distance
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text       = "${route.etaMinutes} min",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = "${"%.1f".format(route.distanceMiles)} mi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Congestion bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress       = { route.congestionLevel.toFraction() },
                    modifier       = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color          = congestionColor,
                    trackColor     = congestionColor.copy(alpha = 0.2f),
                )
                Text(
                    text  = route.congestionLevel.toLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = congestionColor,
                )
            }
        }
    }
}
