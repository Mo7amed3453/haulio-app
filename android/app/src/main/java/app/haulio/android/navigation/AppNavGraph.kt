package app.haulio.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.haulio.android.features.address.AddressSearchScreen
import app.haulio.android.features.delivery.ArrivalScreen
import app.haulio.android.features.map.MapScreen
import app.haulio.android.features.navigation.NavigationScreen
import app.haulio.android.features.scanner.BarcodeScannerScreen
import app.haulio.android.services.address.ConfidenceLevel
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.ParsedAddress
import app.haulio.shared.navigation.models.GeoPoint

private const val MAP_ROUTE           = "map"
private const val NAVIGATION_ROUTE    = "navigation"
private const val ADDRESS_SEARCH_ROUTE = "address_search"
private const val SCANNER_ROUTE       = "scanner"
private const val ARRIVAL_ROUTE       = "arrival"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController  = navController,
        startDestination = MAP_ROUTE,
    ) {
        composable(MAP_ROUTE) {
            MapScreen(
                onNavigateToAddressSearch = { navController.navigate(ADDRESS_SEARCH_ROUTE) },
            )
        }

        composable(NAVIGATION_ROUTE) {
            NavigationScreen()
        }

        composable(
            route = "$ADDRESS_SEARCH_ROUTE?prefill={prefill}",
            arguments = listOf(
                navArgument("prefill") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val prefill = it.arguments?.getString("prefill")
            AddressSearchScreen(
                onNavigateToCoordinates = { geo ->
                    // Pass lat/lon as route args to the arrival screen
                    navController.navigate("$ARRIVAL_ROUTE/${geo.lat}/${geo.lon}")
                },
                onOpenScanner = { navController.navigate(SCANNER_ROUTE) },
                prefillRaw    = prefill,
            )
        }

        composable(SCANNER_ROUTE) {
            BarcodeScannerScreen(
                onAddressExtracted = { address ->
                    navController.navigate("$ADDRESS_SEARCH_ROUTE?prefill=$address") {
                        popUpTo(SCANNER_ROUTE) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route     = "$ARRIVAL_ROUTE/{lat}/{lon}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType },
                navArgument("lon") { type = NavType.StringType },
            ),
        ) { backStack ->
            val lat  = backStack.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
            val lon  = backStack.arguments?.getString("lon")?.toDoubleOrNull() ?: 0.0
            val stub = AddressSuggestion(
                parsed      = ParsedAddress(formatted = "Destination (${"%.4f".format(lat)}, ${"%.4f".format(lon)})"),
                coordinates = GeoPoint(lat, lon),
                confidence  = ConfidenceLevel.APPROXIMATE,
            )
            ArrivalScreen(
                suggestion         = stub,
                onDeliveryComplete = {
                    navController.popBackStack(MAP_ROUTE, inclusive = false)
                },
            )
        }
    }
}
