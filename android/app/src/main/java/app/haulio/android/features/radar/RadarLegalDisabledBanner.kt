package app.haulio.android.features.radar

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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Persistent banner shown at the bottom of the screen when the driver enters
 * Virginia or Washington DC — jurisdictions that ban radar/speed detectors.
 *
 * Slides in from the bottom. Cannot be dismissed by the user.
 *
 * @param jurisdiction Human-readable name of the banned jurisdiction ("VA" or "DC"),
 *                     or null when the driver is not in a banned area.
 */
@Composable
fun RadarLegalDisabledBanner(
    jurisdiction: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible  = jurisdiction != null,
        enter    = slideInVertically(initialOffsetY = { it }),
        exit     = slideOutVertically(targetOffsetY  = { it }),
        modifier = modifier,
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(),
            shape          = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color          = Color(0xFF37474F),   // Blue-grey 800
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector        = Icons.Default.Block,
                    contentDescription = null,
                    tint               = Color(0xFFFFB74D),  // Amber 300
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text  = "Radar features disabled in ${jurisdiction ?: "this jurisdiction"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
        }
    }
}
