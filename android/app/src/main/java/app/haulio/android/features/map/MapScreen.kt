package app.haulio.android.features.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.haulio.android.features.search.SearchBar
import org.koin.androidx.compose.koinViewModel

@Composable
fun MapScreen(
    onNavigateToAddressSearch: () -> Unit = {},
    viewModel: MapViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        MapLibreView(
            modifier = Modifier.fillMaxSize(),
            userLocation = uiState.userLocation,
        )
        SearchBar(
            modifier = Modifier.padding(16.dp),
            onSearchChanged = viewModel::onSearchChanged,
            onFuelTap = viewModel::onFuelTap,
        )
        FloatingActionButton(
            onClick = onNavigateToAddressSearch,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Address",
            )
        }
    }
}
