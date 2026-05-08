import CoreLocation
import Foundation

// MARK: - Navigation Data Models

/// Maneuver type matching OSRM/KMM conventions.
enum ManeuverType: String, Sendable, Hashable {
    case turnLeft = "turn-left"
    case turnRight = "turn-right"
    case turnSharpLeft = "turn-sharp-left"
    case turnSharpRight = "turn-sharp-right"
    case turnSlightLeft = "turn-slight-left"
    case turnSlightRight = "turn-slight-right"
    case straight = "straight"
    case exitLeft = "exit-left"
    case exitRight = "exit-right"
    case ramp = "ramp"
    case merge = "merge"
    case roundaboutLeft = "roundabout-left"
    case roundaboutRight = "roundabout-right"
    case arrive = "arrive"
    case depart = "depart"
    case unknown = "unknown"
}

/// Service icon available at a highway exit.
enum ServiceIcon: String, Sendable, Hashable, CaseIterable {
    case gas = "fuelpump.fill"
    case food = "fork.knife"
    case lodging = "bed.double.fill"
    case hospital = "cross.fill"
    case restroom = "figure.walk"
}

/// Represents a single navigation step/maneuver.
struct NavigationStep: Sendable, Identifiable {
    let id: UUID
    let maneuver: ManeuverType
    let streetName: String
    /// Total distance for this step in meters (decrements as user progresses).
    let distanceMeters: Double
    let exitNumber: String?
    let destinationNames: [String]
    let serviceIcons: [ServiceIcon]

    init(
        id: UUID = UUID(),
        maneuver: ManeuverType,
        streetName: String,
        distanceMeters: Double,
        exitNumber: String? = nil,
        destinationNames: [String] = [],
        serviceIcons: [ServiceIcon] = []
    ) {
        self.id = id
        self.maneuver = maneuver
        self.streetName = streetName
        self.distanceMeters = distanceMeters
        self.exitNumber = exitNumber
        self.destinationNames = destinationNames
        self.serviceIcons = serviceIcons
    }
}

/// Represents a route deviation event (user has gone >50m off-route for >10s).
struct RouteDeviation: Sendable {
    let offsetMeters: Double
    let durationSeconds: Double
    /// Stored as raw components to avoid CLLocationCoordinate2D Sendable concerns.
    let latitude: Double
    let longitude: Double

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

/// User-reported reason for skipping a route segment.
enum DeviationReason: String, Sendable, CaseIterable, Identifiable {
    case roadClosed = "Road Closed"
    case accident = "Accident"
    case other = "Other"

    var id: String { rawValue }
}

/// ETA and remaining route information.
struct ETAInfo: Sendable {
    let arrivalDate: Date
    let remainingDistanceMeters: Double
    let remainingDurationSeconds: Double
}

// MARK: - KMM Bridge Protocols
//
// These protocols mirror the KMM shared module interfaces.
// When the KMM NavigationManager, RouteTracker, and ETACalculator are compiled
// into the iOS target (via the shared/ Kotlin Multiplatform module), replace
// the Mock* classes below with wrapper adapters conforming to these protocols.

/// Mirrors KMM NavigationManager.
protocol NavigationManagerProtocol: AnyObject {
    /// Start navigating along the given route coordinates.
    func startNavigation(routeCoordinates: [CLLocationCoordinate2D]) async
    /// Stop active navigation session.
    func stopNavigation() async
    /// Trigger reroute from current GPS position.
    func requestReroute() async
    /// AsyncStream of navigation steps emitted as user progresses.
    var stepStream: AsyncStream<NavigationStep> { get }
    /// The most recently emitted step (may be nil before navigation starts).
    var currentStep: NavigationStep? { get }
}

/// Mirrors KMM RouteTracker.
protocol RouteTrackerProtocol: AnyObject {
    /// Emits a RouteDeviation when user is >50m off-route for >10 consecutive seconds.
    var deviationStream: AsyncStream<RouteDeviation> { get }
    /// Current speed in metres per second (from GPS or KMM fused source).
    var currentSpeedMps: Double { get }
    /// Whether the user is currently considered on-route.
    var isOnRoute: Bool { get }
}

/// Mirrors KMM ETACalculator.
protocol ETACalculatorProtocol: AnyObject {
    /// Continuous stream of updated ETA snapshots.
    var etaStream: AsyncStream<ETAInfo> { get }
    /// Latest computed ETA (nil before first calculation).
    var currentETA: ETAInfo? { get }
}

// MARK: - Mock Implementations
//
// Used for development and when the KMM shared module is not yet linked.
// All mocks are @MainActor-isolated so they share the same concurrency domain
// as the ViewModels that consume them.

@MainActor
final class MockNavigationManager: NavigationManagerProtocol {
    private(set) var currentStep: NavigationStep?
    private var stepContinuation: AsyncStream<NavigationStep>.Continuation?

    lazy var stepStream: AsyncStream<NavigationStep> = {
        AsyncStream { [weak self] continuation in
            self?.stepContinuation = continuation
        }
    }()

    func startNavigation(routeCoordinates: [CLLocationCoordinate2D]) async {
        let step = NavigationStep(
            maneuver: .depart,
            streetName: "US-1 North",
            distanceMeters: 1_200,
            exitNumber: nil,
            destinationNames: ["New Haven", "Hartford"],
            serviceIcons: []
        )
        currentStep = step
        stepContinuation?.yield(step)

        // Simulate a second step with exit after 3 s (demo only)
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(3))
            guard let self else { return }
            let exitStep = NavigationStep(
                maneuver: .exitRight,
                streetName: "Route 1 North",
                distanceMeters: 3_000,
                exitNumber: "42B",
                destinationNames: ["Milford", "Orange"],
                serviceIcons: [.gas, .food, .lodging]
            )
            self.currentStep = exitStep
            self.stepContinuation?.yield(exitStep)
        }
    }

    func stopNavigation() async {
        currentStep = nil
        stepContinuation?.finish()
        stepContinuation = nil
    }

    func requestReroute() async {
        let step = NavigationStep(
            maneuver: .turnRight,
            streetName: "Alternate Route",
            distanceMeters: 800
        )
        currentStep = step
        stepContinuation?.yield(step)
    }
}

@MainActor
final class MockRouteTracker: RouteTrackerProtocol {
    private(set) var currentSpeedMps: Double = 13.4  // ~30 mph
    private(set) var isOnRoute: Bool = true
    private var deviationContinuation: AsyncStream<RouteDeviation>.Continuation?

    lazy var deviationStream: AsyncStream<RouteDeviation> = {
        AsyncStream { [weak self] continuation in
            self?.deviationContinuation = continuation
        }
    }()

    /// Call this during testing/preview to trigger a deviation popup.
    func simulateDeviation(latitude: Double, longitude: Double) {
        isOnRoute = false
        deviationContinuation?.yield(RouteDeviation(
            offsetMeters: 75.0,
            durationSeconds: 12.0,
            latitude: latitude,
            longitude: longitude
        ))
    }
}

@MainActor
final class MockETACalculator: ETACalculatorProtocol {
    private(set) var currentETA: ETAInfo? = ETAInfo(
        arrivalDate: Date().addingTimeInterval(1_800),
        remainingDistanceMeters: 24_140,
        remainingDurationSeconds: 1_800
    )
    private var etaContinuation: AsyncStream<ETAInfo>.Continuation?

    lazy var etaStream: AsyncStream<ETAInfo> = {
        AsyncStream { [weak self] continuation in
            self?.etaContinuation = continuation
            if let eta = self?.currentETA {
                continuation.yield(eta)
            }
        }
    }()
}
