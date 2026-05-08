import Foundation

/// Simple dependency injection container providing shared services.
@MainActor
final class DependencyContainer: Sendable {
    let locationService: LocationService
    let mapStyleProvider: MapStyleProvider
    let tileConfiguration: TileConfiguration

    // Navigation services (mock until KMM shared module is linked)
    let navigationManager: MockNavigationManager
    let routeTracker: MockRouteTracker
    let etaCalculator: MockETACalculator

    init() {
        self.tileConfiguration = TileConfiguration()
        self.mapStyleProvider = MapStyleProvider()
        self.locationService = LocationService()
        self.navigationManager = MockNavigationManager()
        self.routeTracker = MockRouteTracker()
        self.etaCalculator = MockETACalculator()
    }
}
