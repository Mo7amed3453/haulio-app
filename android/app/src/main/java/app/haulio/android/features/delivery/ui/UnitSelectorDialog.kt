package app.haulio.android.features.delivery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Modal dialog shown when navigation reaches within 50 m of a destination
 * that has a unit / apartment number.
 *
 * The driver can:
 * - Select a unit from a pre-populated [units] list, OR
 * - Type a custom unit number in the text field below.
 *
 * @param units       Known unit / apt labels parsed from the parsed address.
 * @param onConfirm   Called with the chosen unit string when the driver taps Confirm.
 * @param onDismiss   Called when the driver closes the dialog without selecting.
 */
@Composable
fun UnitSelectorDialog(
    units: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var customUnit by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text  = "Select Unit / Apt",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (units.isNotEmpty()) {
                    Text(
                        text  = "Known units at this address:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(units) { unit ->
                            val isChosen = selected == unit
                            if (isChosen) {
                                Button(
                                    onClick  = { selected = unit; customUnit = "" },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                ) { Text(unit) }
                            } else {
                                OutlinedButton(
                                    onClick  = { selected = unit; customUnit = "" },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                ) { Text(unit) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                androidx.compose.material3.OutlinedTextField(
                    value         = customUnit,
                    onValueChange = { customUnit = it; selected = null },
                    label         = { Text("Or type a unit number") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Button(
                        onClick  = {
                            val choice = selected ?: customUnit.trim()
                            if (choice.isNotEmpty()) onConfirm(choice)
                        },
                        enabled = selected != null || customUnit.trim().isNotEmpty(),
                    ) { Text("Confirm") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun UnitSelectorDialogPreview() {
    MaterialTheme {
        UnitSelectorDialog(
            units = listOf("Apt 1A", "Apt 1B", "Apt 2A", "Apt 2B", "Suite 100"),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
