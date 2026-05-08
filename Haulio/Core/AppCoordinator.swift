import SwiftUI

/// Coordinator managing navigation state and dependency wiring.
@MainActor
@Observable
final class AppCoordinator {
    let container: DependencyContainer
    let mapViewModel: MapViewModel

    init() {
        let container = DependencyContainer()
        self.container = container
        self.mapViewModel = MapViewModel(
            locationService: container.locationService,
            styleProvider: container.mapStyleProvider
        )
    }
}
