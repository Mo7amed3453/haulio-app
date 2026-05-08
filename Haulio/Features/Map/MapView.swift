import SwiftUI
import CoreLocation

/// Primary map screen composing the MapLibre map with search overlay.
@MainActor
struct MapView: View {
    @Bindable var viewModel: MapViewModel

    var body: some View {
        ZStack(alignment: .top) {
            // MapLibre GL map (full screen)
            MapViewController(viewModel: viewModel)
                .ignoresSafeArea()

            // Search bar overlay at top
            SearchBarView(searchText: $viewModel.searchText)
                .padding(.horizontal, 16)
                .padding(.top, 8)
        }
        .task {
            await viewModel.startLocationTracking()
        }
    }
}
