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
import app.haulio.android.features.route.RouteCompareSheet
import app.haulio.android.features.scanner.BarcodeScannerScreen
import app.haulio.android.services.address.AddressSuggestion
import app.haulio.android.services.address.ConfidenceLevel
import app.haulio.android.services.address.ParsedAddress
import app.haulio.shared.navigation.models.GeoPoint

private const val MAP_ROUTE            = "map"
private const val NAVIGATION_ROUTE     = "navigation"
private const val ADDRESS_SEARCH_ROUTE = "address_search"
private const val SCANNER_ROUTE        = "scanner"
private const val ARRIVAL_ROUTE        = "arrival"
private const val ROUTE_COMPARE_ROUTE  = "route_compare"

/** Parses "lat,lon" string into a [GeoPoint], falling back to (0,0) on any error. */
private fun String.toGeoPoint(): GeoPoint {
    val parts = split(",")
    val lat   = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0
    val lon   = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0
    return GeoPoint(lat, lon)
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
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
            route     = "$ADDRESS_SEARCH_ROUTE?prefill={prefill}",
            arguments = listOf(
                navArgument("prefill") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            val prefill = backStack.arguments?.getString("prefill")
            AddressSearchScreen(
                onNavigateToCoordinates = { geo ->
                    // Show route alternatives before committing to the arrival screen.
                    // Mock origin: SF city centre — in production use real driver location.
                    val origin = "37.7749,-122.4194"
                    val dest   = "${geo.lat},${geo.lon}"
                    navController.navigate("$ROUTE_COMPARE_ROUTE?origin=$origin&dest=$dest")
                },
                onOpenScanner = { navController.navigate(SCANNER_ROUTE) },
                prefillRaw    = prefill,
            )
        }

        // Route comparison sheet — driver picks a route before navigation starts.
        composable(
            route     = "$ROUTE_COMPARE_ROUTE?origin={origin}&dest={dest}",
            arguments = listOf(
                navArgument("origin") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = "37.7749,-122.4194"
                },
                navArgument("dest") {
                    type         = NavType.StringType
                    nullable     = true
                    defaultValue = "37.7831,-122.4090"
                },
            ),
        ) { backStack ->
            val originStr = backStack.arguments?.getString("origin") ?: "37.7749,-122.4194"
            val destStr   = backStack.arguments?.getString("dest")   ?: "37.7831,-122.4090"
            val origin    = originStr.toGeoPoint()
            val dest      = destStr.toGeoPoint()

            RouteCompareSheet(
                origin        = origin,
                destination   = dest,
                onRoutePicked = { _ ->
                    // Pop route_compare + address_search, then go to arrival.
                    navController.navigate("$ARRIVAL_ROUTE/${dest.lat}/${dest.lon}") {
                        popUpTo(MAP_ROUTE) { inclusive = false }
                    }
                },
                onDismiss = { navController.popBackStack() },
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
