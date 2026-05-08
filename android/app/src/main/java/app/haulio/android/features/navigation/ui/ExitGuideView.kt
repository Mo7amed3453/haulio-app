package app.haulio.android.features.navigation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.haulio.android.services.navigation.ExitInfo
import app.haulio.android.services.navigation.ExitService

/**
 * MUTCD-compliant highway exit guide sign rendered in Jetpack Compose.
 *
 * Conforms to US Manual on Uniform Traffic Control Devices (MUTCD) visual
 * standards:
 *  - Green background (#006B3C)
 *  - White text in Highway Gothic style (Roboto Mono as fallback)
 *  - Exit number top-left, destination center, service icons bottom row
 *  - 8dp corner radius, 2dp white border
 *  - Animated slide-in from top; caller controls [visible] to trigger dismiss
 *
 * @param exitInfo data model with exit number, destination, and services.
 * @param visible drives the enter/exit animation.
 */
@Composable
fun ExitGuideView(
    exitInfo: ExitInfo,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 400),
            initialOffsetY = { -it },
        ),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 300),
            targetOffsetY = { -it },
        ),
        modifier = modifier,
    ) {
        ExitSignCard(exitInfo = exitInfo)
    }
}

@Composable
private fun ExitSignCard(exitInfo: ExitInfo) {
    val highwayGreen = Color(0xFF006B3C)
    val signWhite = Color.White
    val monoFont = FontFamily.Monospace // Roboto Mono / Highway Gothic fallback

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(width = 2.dp, color = signWhite, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = highwayGreen,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── Row 1: exit number badge (top-left) ────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExitNumberBadge(exitNumber = exitInfo.exitNumber, fontFamily = monoFont)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Row 2: destination name (center) ──────────────────────────
            Text(
                text = exitInfo.destination,
                modifier = Modifier.fillMaxWidth(),
                color = signWhite,
                fontFamily = monoFont,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Row 3: distance hint ───────────────────────────────────────
            if (exitInfo.distanceMiles > 0.0) {
                val distanceText = when {
                    exitInfo.distanceMiles >= 1.0 ->
                        "%.1f mi".format(exitInfo.distanceMiles)
                    else ->
                        "%.0f ft".format(exitInfo.distanceMiles * 5280)
                }
                Text(
                    text = distanceText,
                    modifier = Modifier.fillMaxWidth(),
                    color = signWhite.copy(alpha = 0.85f),
                    fontFamily = monoFont,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Row 4: service icons (bottom) ─────────────────────────────
            if (exitInfo.services.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    exitInfo.services.forEach { service ->
                        ServiceIcon(
                            service = service,
                            tint = signWhite,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitNumberBadge(exitNumber: String, fontFamily: FontFamily) {
    Box(
        modifier = Modifier
            .border(
                width = 1.5.dp,
                color = Color.White,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "EXIT",
                color = Color.White,
                fontFamily = fontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp,
            )
            Text(
                text = exitNumber,
                color = Color.White,
                fontFamily = fontFamily,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ServiceIcon(
    service: ExitService,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val (icon, description) = service.toIconData()
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = modifier,
    )
}

private fun ExitService.toIconData(): Pair<ImageVector, String> = when (this) {
    ExitService.GAS -> Icons.Filled.LocalGasStation to "Gas station"
    ExitService.FOOD -> Icons.Filled.Restaurant to "Food"
    ExitService.LODGING -> Icons.Filled.Hotel to "Lodging"
}
