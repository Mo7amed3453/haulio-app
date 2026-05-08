package app.haulio.android.features.scanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Semi-transparent viewfinder overlay drawn on top of the CameraX preview.
 *
 * Renders a darkened scrim with a rectangular cut-out (the "scan window")
 * and corner brackets to guide the user to align the barcode.
 *
 * @param hint  Instructional text rendered below the scan window.
 */
@Composable
fun ScanOverlay(
    hint: String = "Align barcode within the frame",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier          = modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center,
    ) {
        // Scrim + cut-out
        Canvas(modifier = Modifier.fillMaxSize()) {
            val windowW = size.width * 0.75f
            val windowH = windowW * 0.55f
            val left    = (size.width  - windowW) / 2f
            val top     = (size.height - windowH) / 2f

            // Draw dark overlay
            drawRect(color = Color(0x99000000))

            // Punch a transparent hole for the scan window
            drawRoundRect(
                color       = Color.Transparent,
                topLeft     = Offset(left, top),
                size        = Size(windowW, windowH),
                cornerRadius = CornerRadius(12f, 12f),
                blendMode   = BlendMode.Clear,
            )

            // Corner bracket strokes
            val bracketLen  = 40f
            val strokeWidth = 6f
            val cornerR     = 12f
            val strokeColor = Color.White

            // Top-left
            drawLine(strokeColor, Offset(left, top + cornerR),           Offset(left, top + bracketLen),           strokeWidth)
            drawLine(strokeColor, Offset(left + cornerR, top),           Offset(left + bracketLen, top),           strokeWidth)
            // Top-right
            val right = left + windowW
            drawLine(strokeColor, Offset(right, top + cornerR),          Offset(right, top + bracketLen),          strokeWidth)
            drawLine(strokeColor, Offset(right - cornerR, top),          Offset(right - bracketLen, top),          strokeWidth)
            // Bottom-left
            val bottom = top + windowH
            drawLine(strokeColor, Offset(left, bottom - cornerR),        Offset(left, bottom - bracketLen),        strokeWidth)
            drawLine(strokeColor, Offset(left + cornerR, bottom),        Offset(left + bracketLen, bottom),        strokeWidth)
            // Bottom-right
            drawLine(strokeColor, Offset(right, bottom - cornerR),       Offset(right, bottom - bracketLen),       strokeWidth)
            drawLine(strokeColor, Offset(right - cornerR, bottom),       Offset(right - bracketLen, bottom),       strokeWidth)

            // Thin window border
            drawRoundRect(
                color        = Color.White.copy(alpha = 0.4f),
                topLeft      = Offset(left, top),
                size         = Size(windowW, windowH),
                cornerRadius = CornerRadius(12f, 12f),
                style        = Stroke(width = 1.5f),
            )
        }

        // Hint text below the scan window
        Text(
            text      = hint,
            color     = Color.White,
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .padding(horizontal = 24.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScanOverlayPreview() {
    MaterialTheme {
        ScanOverlay(
            hint = "Align barcode within the frame",
            modifier = Modifier.size(360.dp, 640.dp),
        )
    }
}
