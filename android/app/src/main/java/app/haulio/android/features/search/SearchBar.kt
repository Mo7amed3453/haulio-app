package app.haulio.android.features.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    onSearchChanged: (String) -> Unit,
    onFuelTap: () -> Unit
) {
    val query = remember { mutableStateOf("") }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dp(8))
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = query.value,
            onValueChange = {
                query.value = it
                onSearchChanged(it)
            },
            label = { Text("Search destination") },
            singleLine = true
        )
        Button(onClick = onFuelTap) {
            Text("Fuel")
        }
    }
}
