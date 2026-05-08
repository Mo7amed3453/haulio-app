import Foundation

/// Provides tile server configuration constants.
struct TileConfiguration: Sendable {
    /// Base URL for the Haulio tile server.
    let tileServerBaseURL: URL

    /// Default map center (geographic center of the contiguous USA).
    let defaultCenter: (latitude: Double, longitude: Double)

    /// Default zoom level.
    let defaultZoom: Double

    /// Map attribution text (ODbL requirement for OSM data).
    let attribution: String

    init() {
        // Safe URL construction — tiles.haulio.app is a known valid URL
        guard let url = URL(string: "https://tiles.haulio.app") else {
            fatalError("Invalid hardcoded tile server URL — this is a programming error")
        }
        self.tileServerBaseURL = url
        self.defaultCenter = (latitude: 39.8283, longitude: -98.5795)
        self.defaultZoom = 4.0
        self.attribution = "© OpenStreetMap contributors"
    }

    /// Tile URL template for MapLibre style spec.
    var tileURLTemplate: String {
        "\(tileServerBaseURL.absoluteString)/{z}/{x}/{y}.pbf"
    }
}
