import CoreLocation
import Foundation
import SwiftUI

/// Bridges the KMM NavigationManager, RouteTracker, and ETACalculator
/// to reactive SwiftUI state consumed by NavigationView.
///
/// Swift 6 concurrency contract:
///   • @MainActor ensures all state mutations happen on the main thread.
///   • Async listener Tasks inherit @MainActor isolation from their call site.
///   • All stored types are value types (Sendable) or MainActor-isolated.
@MainActor
@Observable
final class NavigationViewModel {

    // MARK: - UI State

    var currentStep: NavigationStep?
    var distanceToNextTurnMeters: Double = 0
    var currentSpeedMph: Double = 0
    var eta: ETAInfo?

    var showExitGuide: Bool = false
    var exitGuideStep: NavigationStep?

    var showDeviationPopup: Bool = false
    var pendingDeviation: RouteDeviation?

    var isNavigating: Bool = false
    var routeCoordinates: [CLLocationCoordinate2D] = []

    // MARK: - Deviation History (persisted locally for future crowdsource feature)

    private(set) var storedDeviationReasons: [StoredDeviationRecord] = []

    struct StoredDeviationRecord: Sendable {
        let deviation: RouteDeviation
        let reason: DeviationReason
        let recordedAt: Date
    }

    // MARK: - Dependencies

    private let navigationManager: any NavigationManagerProtocol
    private let routeTracker: any RouteTrackerProtocol
    private let etaCalculator: any ETACalculatorProtocol
    let voiceService: VoiceInstructionService

    // MARK: - Constants

    /// Show exit guide when within 2 miles of the exit.
    private static let exitGuideThresholdMeters: Double = 3_218.69

    /// Speed conversion: m/s → mph.
    private static let mpsToMph: Double = 2.23694

    // MARK: - Listener Tasks

    private var stepTask: Task<Void, Never>?
    private var deviationTask: Task<Void, Never>?
    private var etaTask: Task<Void, Never>?

    // MARK: - Init

    init(
        navigationManager: any NavigationManagerProtocol,
        routeTracker: any RouteTrackerProtocol,
        etaCalculator: any ETACalculatorProtocol,
        voiceService: VoiceInstructionService = VoiceInstructionService()
    ) {
        self.navigationManager = navigationManager
        self.routeTracker = routeTracker
        self.etaCalculator = etaCalculator
        self.voiceService = voiceService
    }

    // MARK: - Navigation Lifecycle

    /// Begin navigation along the supplied route coordinates.
    func startNavigation(along coordinates: [CLLocationCoordinate2D]) async {
        guard !isNavigating else { return }
        routeCoordinates = coordinates
        isNavigating = true
        voiceService.prepareAudioSession()
        await navigationManager.startNavigation(routeCoordinates: coordinates)
        startListeners()
    }

    /// Gracefully stop the active navigation session.
    func stopNavigation() async {
        guard isNavigating else { return }
        isNavigating = false
        cancelListeners()
        await navigationManager.stopNavigation()
        voiceService.deactivateAudioSession()
        resetState()
    }

    // MARK: - Location Feed-in (called from location stream on each update)

    /// Update speed and evaluate distance-based logic on each GPS fix.
    func updateCurrentLocation(_ location: CLLocation) {
        let speedMps = max(location.speed, 0)
        currentSpeedMph = speedMps * Self.mpsToMph

        guard let step = currentStep else { return }

        // Voice distance announcements
        voiceService.updateDistance(distanceToNextTurnMeters, for: step)

        // Exit guide: show/hide based on proximity
        let shouldShow = step.exitNumber != nil
            && distanceToNextTurnMeters <= Self.exitGuideThresholdMeters

        if shouldShow, !showExitGuide {
            withAnimation {
                exitGuideStep = step
                showExitGuide = true
            }
        } else if !shouldShow, showExitGuide {
            withAnimation { showExitGuide = false }
        }
    }

    // MARK: - Deviation Handling

    /// User supplied a reason – store it and request reroute.
    func handleDeviationReason(_ reason: DeviationReason) {
        guard let deviation = pendingDeviation else { return }
        storedDeviationReasons.append(
            StoredDeviationRecord(deviation: deviation, reason: reason, recordedAt: Date())
        )
        showDeviationPopup = false
        pendingDeviation = nil
        Task {
            await navigationManager.requestReroute()
            voiceService.announceImmediately("Rerouting")
        }
    }

    /// User dismissed without supplying a reason – continue current heading.
    func dismissDeviation() {
        showDeviationPopup = false
        pendingDeviation = nil
    }

    // MARK: - Formatted Helpers

    var formattedSpeed: String { "\(Int(currentSpeedMph.rounded())) mph" }

    var formattedETA: String {
        guard let eta else { return "--:--" }
        let fmt = DateFormatter()
        fmt.timeStyle = .short
        fmt.dateStyle = .none
        return fmt.string(from: eta.arrivalDate)
    }

    var formattedRemainingDistance: String {
        guard let eta else { return "" }
        let miles = eta.remainingDistanceMeters / 1_609.34
        return miles >= 10
            ? "\(Int(miles.rounded())) mi"
            : String(format: "%.1f mi", miles)
    }

    // MARK: - Private: Stream Listeners

    private func startListeners() {
        // Step stream – runs on @MainActor (inherited from call site)
        stepTask = Task { [weak self] in
            guard let self else { return }
            for await step in navigationManager.stepStream {
                currentStep = step
                distanceToNextTurnMeters = step.distanceMeters
            }
        }

        // Deviation stream
        deviationTask = Task { [weak self] in
            guard let self else { return }
            for await deviation in routeTracker.deviationStream {
                pendingDeviation = deviation
                withAnimation { showDeviationPopup = true }
            }
        }

        // ETA stream
        etaTask = Task { [weak self] in
            guard let self else { return }
            for await etaInfo in etaCalculator.etaStream {
                eta = etaInfo
            }
        }
    }

    private func cancelListeners() {
        stepTask?.cancel();      stepTask = nil
        deviationTask?.cancel(); deviationTask = nil
        etaTask?.cancel();       etaTask = nil
    }

    private func resetState() {
        currentStep = nil
        distanceToNextTurnMeters = 0
        currentSpeedMph = 0
        eta = nil
        showExitGuide = false
        exitGuideStep = nil
        showDeviationPopup = false
        pendingDeviation = nil
    }
}
