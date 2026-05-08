import Foundation

/// Simple dependency injection container providing shared services.
@MainActor
final class DependencyContainer: Sendable {
    let locationService: LocationService
    let mapStyleProvider: MapStyleProvider
    let tileConfiguration: TileConfiguration

    // Navigation services – backed by Mock* until KMM shared module is linked.
    // To connect KMM: replace Mock* instantiation with your KMM wrapper adapters.
    let navigationManager: any NavigationManagerProtocol
    let routeTracker: any RouteTrackerProtocol
    let etaCalculator: any ETACalculatorProtocol

    init() {
        self.tileConfiguration = TileConfiguration()
        self.mapStyleProvider = MapStyleProvider()
        self.locationService = LocationService()
        self.navigationManager = MockNavigationManager()
        self.routeTracker = MockRouteTracker()
        self.etaCalculator = MockETACalculator()
    }
}
