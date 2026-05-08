import CoreLocation
import Foundation

/// Service providing user location updates via AsyncStream.
/// Uses CLLocationManager internally and bridges delegate callbacks to Swift Concurrency.
@MainActor
final class LocationService: Sendable {
    private let manager: CLLocationManager
    private let delegate: LocationDelegate

    init() {
        self.manager = CLLocationManager()
        self.delegate = LocationDelegate()
        self.manager.delegate = delegate
        self.manager.desiredAccuracy = kCLLocationAccuracyBest
        self.manager.distanceFilter = 10
    }

    /// Request when-in-use location authorization.
    func requestAuthorization() {
        manager.requestWhenInUseAuthorization()
    }

    /// Current authorization status.
    var authorizationStatus: CLAuthorizationStatus {
        manager.authorizationStatus
    }

    /// Start tracking and return an AsyncStream of location updates.
    /// The stream ends when the task is cancelled.
    func startTracking() -> AsyncStream<CLLocation> {
        requestAuthorization()

        let stream = AsyncStream<CLLocation> { continuation in
            self.delegate.setContinuation(continuation)

            continuation.onTermination = { @Sendable _ in
                Task { @MainActor in
                    self.manager.stopUpdatingLocation()
                    self.delegate.clearContinuation()
                }
            }
        }

        manager.startUpdatingLocation()
        return stream
    }

    /// Stop location updates.
    func stopTracking() {
        manager.stopUpdatingLocation()
        delegate.clearContinuation()
    }
}

// MARK: - Location Delegate

/// Bridges CLLocationManagerDelegate to AsyncStream continuation.
@MainActor
private final class LocationDelegate: NSObject, CLLocationManagerDelegate {
    private var continuation: AsyncStream<CLLocation>.Continuation?

    func setContinuation(_ continuation: AsyncStream<CLLocation>.Continuation) {
        self.continuation = continuation
    }

    func clearContinuation() {
        continuation?.finish()
        continuation = nil
    }

    nonisolated func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        guard let location = locations.last else { return }
        let capturedLocation = location
        Task { @MainActor [weak self] in
            self?.continuation?.yield(capturedLocation)
        }
    }

    nonisolated func locationManager(
        _ manager: CLLocationManager,
        didFailWithError error: Error
    ) {
        // Log error but don't terminate the stream — transient failures are expected
        print("[LocationService] Location error: \(error.localizedDescription)")
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        // Authorization changes are handled reactively by the view model
    }
}
