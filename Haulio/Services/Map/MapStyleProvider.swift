import Foundation

/// Provides the dark map style JSON for MapLibre GL.
struct MapStyleProvider: Sendable {
    /// Returns the URL to the bundled dark-style.json resource.
    /// Returns nil if the resource is not found in the bundle.
    var styleURL: URL? {
        Bundle.main.url(forResource: "dark-style", withExtension: "json")
    }

    /// Loads the style JSON data from the bundle.
    /// Returns nil if the resource cannot be found or read.
    func loadStyleData() -> Data? {
        guard let url = styleURL else {
            print("[MapStyleProvider] dark-style.json not found in bundle")
            return nil
        }
        return try? Data(contentsOf: url)
    }
}
