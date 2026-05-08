package app.haulio.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.haulio.android.features.map.MapScreen
import app.haulio.android.features.navigation.NavigationScreen

private const val MAP_ROUTE        = "map"
private const val NAVIGATION_ROUTE = "navigation"

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = MAP_ROUTE,
    ) {
        composable(MAP_ROUTE) {
            MapScreen()
        }
        composable(NAVIGATION_ROUTE) {
            NavigationScreen()
        }
    }
}
