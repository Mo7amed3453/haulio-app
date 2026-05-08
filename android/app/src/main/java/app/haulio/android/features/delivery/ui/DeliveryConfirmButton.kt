package app.haulio.android.features.delivery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Floating "Mark as Delivered" button with an optional photo-capture mini-FAB.
 *
 * States:
 * - [isNearDestination] = false → button not visible (handled by parent).
 * - [isNearDestination] = true  → main FAB appears with a spring animation.
 * - [showPhotoOption]   = true  → a smaller camera FAB stacks above it.
 * - [isDelivered]       = true  → button is disabled and shows a success colour.
 *
 * @param isNearDestination  Driver is within ~50 m of the destination.
 * @param isDelivered        Delivery has been marked; disable the button.
 * @param showPhotoOption    Show the optional "Take Photo" mini FAB.
 * @param onMarkDelivered    Called when the driver taps the main FAB.
 * @param onTakePhoto        Called when the driver taps the camera mini-FAB.
 */
@Composable
fun DeliveryConfirmButton(
    isNearDestination: Boolean,
    isDelivered: Boolean,
    showPhotoOption: Boolean,
    onMarkDelivered: () -> Unit,
    onTakePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue  = if (isNearDestination) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow,
        ),
        label = "fab_scale",
    )

    Column(
        modifier            = modifier.scale(scale),
        horizontalAlignment = Alignment.End,
    ) {
        // Camera mini-FAB
        AnimatedVisibility(
            visible = showPhotoOption && !isDelivered,
            enter   = scaleIn(),
            exit    = scaleOut(),
        ) {
            SmallFloatingActionButton(
                onClick            = onTakePhoto,
                containerColor     = MaterialTheme.colorScheme.secondaryContainer,
                contentColor       = MaterialTheme.colorScheme.onSecondaryContainer,
                shape              = CircleShape,
                modifier           = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.CameraAlt,
                    contentDescription = "Take proof-of-delivery photo",
                    modifier           = Modifier.size(20.dp),
                )
            }
        }

        // Label above main FAB
        if (!isDelivered) {
            Text(
                text     = "Tap to mark delivered",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.End).padding(bottom = 4.dp),
            )
        }

        // Main FAB
        FloatingActionButton(
            onClick        = { if (!isDelivered) onMarkDelivered() },
            containerColor = if (isDelivered) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primaryContainer,
            contentColor   = if (isDelivered) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector        = Icons.Default.LocalShipping,
                contentDescription = if (isDelivered) "Delivered" else "Mark as Delivered",
            )
        }

        if (isDelivered) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = "Delivered!",
                style    = MaterialTheme.typography.labelSmall,
                color    = Color(0xFF2E7D32),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun DeliveryConfirmButtonNearPreview() {
    MaterialTheme {
        DeliveryConfirmButton(
            isNearDestination = true,
            isDelivered       = false,
            showPhotoOption   = true,
            onMarkDelivered   = {},
            onTakePhoto       = {},
            modifier          = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DeliveryConfirmButtonDeliveredPreview() {
    MaterialTheme {
        DeliveryConfirmButton(
            isNearDestination = true,
            isDelivered       = true,
            showPhotoOption   = false,
            onMarkDelivered   = {},
            onTakePhoto       = {},
            modifier          = Modifier.padding(16.dp),
        )
    }
}
