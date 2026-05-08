import CoreLocation

extension CLLocationCoordinate2D {
    /// Geographic center of the contiguous United States.
    static let usaCenter = CLLocationCoordinate2D(
        latitude: 39.8283,
        longitude: -98.5795
    )
}

// MARK: - Sendable conformance for safe concurrency usage
extension CLLocationCoordinate2D: @retroactive @unchecked Sendable {}
