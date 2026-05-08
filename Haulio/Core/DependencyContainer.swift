import Foundation

/// Simple dependency injection container providing shared services.
@MainActor
final class DependencyContainer: Sendable {
    let locationService: LocationService
    let mapStyleProvider: MapStyleProvider
    let tileConfiguration: TileConfiguration

    init() {
        self.tileConfiguration = TileConfiguration()
        self.mapStyleProvider = MapStyleProvider()
        self.locationService = LocationService()
    }
}
