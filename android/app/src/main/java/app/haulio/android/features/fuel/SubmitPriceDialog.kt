package app.haulio.android.features.fuel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.haulio.android.services.fuel.FuelGrade
import app.haulio.android.services.fuel.FuelStation

/**
 * Dialog that allows a driver to submit a fuel price for a specific station.
 *
 * The driver enters the pump price per gallon and selects the grade.
 * On confirmation [onSubmit] is called; on cancel or dismiss [onDismiss] is called.
 *
 * @param station   The station for which a price is being submitted.
 * @param onSubmit  Called with (price, grade) on confirmation.
 * @param onDismiss Called when the dialog should be closed without action.
 */
@Composable
fun SubmitPriceDialog(
    station: FuelStation,
    onSubmit: (price: Double, grade: FuelGrade) -> Unit,
    onDismiss: () -> Unit,
) {
    var priceText by remember { mutableStateOf("") }
    var selectedGrade by remember { mutableStateOf(FuelGrade.REGULAR) }
    val priceDouble = priceText.toDoubleOrNull()
    val isValid = priceDouble != null && priceDouble > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text  = "Submit Price",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text  = station.name ?: station.brand ?: "Fuel Station",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value          = priceText,
                    onValueChange  = { priceText = it },
                    label          = { Text("Price per gallon (USD)") },
                    prefix         = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine     = true,
                    isError        = priceText.isNotEmpty() && !isValid,
                    modifier       = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text  = "Fuel Grade",
                    style = MaterialTheme.typography.labelLarge,
                )

                FuelGrade.entries.forEach { grade ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = selectedGrade == grade,
                            onClick  = { selectedGrade = grade },
                        )
                        Text(
                            text     = grade.displayName(),
                            style    = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { priceDouble?.let { onSubmit(it, selectedGrade) } },
                enabled  = isValid,
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun FuelGrade.displayName(): String = when (this) {
    FuelGrade.REGULAR   -> "Regular (87)"
    FuelGrade.MID_GRADE -> "Mid-Grade (89)"
    FuelGrade.PREMIUM   -> "Premium (91+)"
    FuelGrade.DIESEL    -> "Diesel"
}
