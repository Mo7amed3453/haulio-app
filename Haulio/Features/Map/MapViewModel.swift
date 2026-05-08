import CoreLocation
import Foundation
import SwiftUI

/// View model managing map state and location tracking.
@MainActor
@Observable
final class MapViewModel {
    // MARK: - Published State

    var userLocation: CLLocationCoordinate2D?
    var mapCenter: CLLocationCoordinate2D = .usaCenter
    var zoomLevel: Double = TileConfiguration().defaultZoom
    var isTracking: Bool = false
    var searchText: String = ""

    // MARK: - Dependencies

    private let locationService: LocationService
    private let styleProvider: MapStyleProvider
    private var trackingTask: Task<Void, Never>?

    // MARK: - Computed Properties

    /// URL for the dark map style JSON bundled in the app.
    var styleURL: URL? {
        styleProvider.styleURL
    }

    // MARK: - Initialization

    init(locationService: LocationService, styleProvider: MapStyleProvider) {
        self.locationService = locationService
        self.styleProvider = styleProvider
    }

    // MARK: - Location Tracking

    /// Begin tracking user location. Updates flow via AsyncStream.
    func startLocationTracking() async {
        guard !isTracking else { return }
        isTracking = true

        let stream = locationService.startTracking()

        for await location in stream {
            userLocation = location.coordinate
        }

        isTracking = false
    }

    /// Stop tracking user location.
    func stopLocationTracking() {
        locationService.stopTracking()
        trackingTask?.cancel()
        trackingTask = nil
        isTracking = false
    }
}
