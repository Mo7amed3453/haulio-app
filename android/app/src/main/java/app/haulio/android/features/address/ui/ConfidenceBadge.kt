package app.haulio.android.features.address.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.haulio.android.services.address.ConfidenceLevel

/**
 * Small coloured pill that communicates the geocoding confidence of an address suggestion.
 *
 * | Level          | Colour | Label       |
 * |----------------|--------|-------------|
 * | VERIFIED_ZIP4  | Green  | ✅ Verified  |
 * | APPROXIMATE    | Amber  | ⚠ Approximate |
 * | NOT_FOUND      | Red    | ✗ Not Found  |
 */
@Composable
fun ConfidenceBadge(
    level: ConfidenceLevel,
    modifier: Modifier = Modifier,
) {
    val (label, background, onBackground) = when (level) {
        ConfidenceLevel.VERIFIED_ZIP4 -> Triple(
            "✅ Verified",
            Color(0xFF2E7D32),
            Color.White,
        )
        ConfidenceLevel.APPROXIMATE -> Triple(
            "⚠ Approximate",
            Color(0xFFF57F17),
            Color.White,
        )
        ConfidenceLevel.NOT_FOUND -> Triple(
            "✗ Not Found",
            Color(0xFFC62828),
            Color.White,
        )
    }

    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(50),
        color     = background,
        tonalElevation = 0.dp,
    ) {
        Text(
            text  = label,
            color = onBackground,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun ConfidenceBadgeVerifiedPreview() {
    MaterialTheme {
        ConfidenceBadge(level = ConfidenceLevel.VERIFIED_ZIP4, modifier = Modifier.padding(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfidenceBadgeApproxPreview() {
    MaterialTheme {
        ConfidenceBadge(level = ConfidenceLevel.APPROXIMATE, modifier = Modifier.padding(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfidenceBadgeNotFoundPreview() {
    MaterialTheme {
        ConfidenceBadge(level = ConfidenceLevel.NOT_FOUND, modifier = Modifier.padding(8.dp))
    }
}
