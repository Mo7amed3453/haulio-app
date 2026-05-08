import SwiftUI

/// Main entry point for the Haulio delivery driver navigation app.
@MainActor
@main
struct HaulioApp: App {
    @State private var coordinator = AppCoordinator()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(coordinator)
        }
    }
}

/// Root content view managed by the coordinator.
@MainActor
struct ContentView: View {
    @Environment(AppCoordinator.self) private var coordinator

    var body: some View {
        NavigationStack {
            MapView(viewModel: coordinator.mapViewModel)
        }
    }
}
