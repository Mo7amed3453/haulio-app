import SwiftUI

/// Coordinator managing navigation state and dependency wiring.
@MainActor
@Observable
final class AppCoordinator {
    let container: DependencyContainer
    let mapViewModel: MapViewModel
    let navigationViewModel: NavigationViewModel

    init() {
        let container = DependencyContainer()
        self.container = container
        self.mapViewModel = MapViewModel(
            locationService: container.locationService,
            styleProvider: container.mapStyleProvider
        )
        self.navigationViewModel = NavigationViewModel(
            navigationManager: container.navigationManager,
            routeTracker: container.routeTracker,
            etaCalculator: container.etaCalculator
        )
    }
}
