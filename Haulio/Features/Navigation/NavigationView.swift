import CoreLocation
import SwiftUI

/// Main navigation screen.
///
/// Layout (back to front, using ZStack):
///   1. Full-screen map (MapViewController + route polyline)
///   2. Turn banner – top edge, slides down when step is available
///   3. Exit guide overlay – just below the banner, conditional on proximity
///   4. Bottom HUD panel – speed, current instruction, ETA
///   5. Route deviation popup – modal overlay (zIndex 10)
@MainActor
struct NavigationView: View {

    @Bindable var viewModel: NavigationViewModel

    /// Demo route used on first launch / in Preview.
    /// Replace with a real route from your route-planning service.
    private static let demoRoute: [CLLocationCoordinate2D] = [
        CLLocationCoordinate2D(latitude: 41.3083, longitude: -72.9279), // New Haven, CT
        CLLocationCoordinate2D(latitude: 41.2565, longitude: -72.8974)  // West Haven, CT
    ]

    var body: some View {
        ZStack(alignment: .top) {

            // ── Layer 1: Map ─────────────────────────────────────────────────
            // In production wire MapViewController(viewModel: mapViewModel) here
            // and overlay a route polyline using MapLibre annotation layer.
            Color(.systemGroupedBackground)
                .ignoresSafeArea()
                .overlay(
                    Text("Map layer (wire MapViewController)")
                        .foregroundColor(.secondary)
                        .font(.caption)
                )

            // ── Layer 2 & 3: Turn banner + Exit guide (top VStack) ───────────
            VStack(spacing: 0) {
                if let step = viewModel.currentStep {
                    TurnBannerView(
                        step: step,
                        distanceMeters: viewModel.distanceToNextTurnMeters
                    )
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(1)
                }

                ExitGuideOverlay(
                    step: viewModel.exitGuideStep,
                    isVisible: viewModel.showExitGuide
                )
                .padding(.top, 8)
                .zIndex(0)

                Spacer()
            }
            .animation(.easeInOut(duration: 0.3), value: viewModel.currentStep?.id)

            // ── Layer 4: Bottom HUD ─────────────────────────────────────────
            VStack {
                Spacer()
                bottomHUD
            }

            // ── Layer 5: Deviation popup ─────────────────────────────────────
            if viewModel.showDeviationPopup {
                RouteDeviationView(
                    onReasonSelected: { reason in
                        viewModel.handleDeviationReason(reason)
                    },
                    onDismiss: {
                        viewModel.dismissDeviation()
                    }
                )
                .transition(.opacity)
                .zIndex(10)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.showDeviationPopup)
        .navigationBarHidden(true)
        .task {
            await viewModel.startNavigation(along: Self.demoRoute)
        }
        .onDisappear {
            Task { await viewModel.stopNavigation() }
        }
    }

    // MARK: - Bottom HUD

    private var bottomHUD: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(alignment: .center, spacing: 0) {

                // Speed readout
                speedCell

                dividerLine

                // Current instruction
                instructionCell
                    .frame(maxWidth: .infinity)

                dividerLine

                // ETA
                etaCell
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(
                Color(.systemBackground)
                    .opacity(0.97)
                    .ignoresSafeArea(edges: .bottom)
            )
        }
        .shadow(color: .black.opacity(0.12), radius: 14, x: 0, y: -4)
    }

    @ViewBuilder
    private var speedCell: some View {
        VStack(spacing: 2) {
            Text(viewModel.formattedSpeed)
                .font(.system(size: 20, weight: .bold, design: .rounded))
                .foregroundColor(.primary)
                .contentTransition(.numericText())
            Text("Speed")
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(width: 72)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Current speed: \(viewModel.formattedSpeed)")
    }

    @ViewBuilder
    private var instructionCell: some View {
        VStack(alignment: .leading, spacing: 3) {
            if let step = viewModel.currentStep {
                Text(step.streetName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)

                Text(formattedTurnDistance(viewModel.distanceToNextTurnMeters))
                    .font(.caption)
                    .foregroundColor(.secondary)
            } else {
                Text("Calculating route…")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.horizontal, 12)
    }

    @ViewBuilder
    private var etaCell: some View {
        VStack(spacing: 2) {
            Text(viewModel.formattedETA)
                .font(.system(size: 17, weight: .bold, design: .rounded))
                .foregroundColor(.primary)
                .contentTransition(.numericText())
            Text(viewModel.formattedRemainingDistance)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(width: 72)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("ETA \(viewModel.formattedETA), \(viewModel.formattedRemainingDistance) remaining")
    }

    @ViewBuilder
    private var dividerLine: some View {
        Divider()
            .frame(height: 40)
    }

    // MARK: - Helpers

    private func formattedTurnDistance(_ meters: Double) -> String {
        if meters >= 1_609.34 {
            return String(format: "%.1f mi", meters / 1_609.34)
        } else {
            return String(format: "%.0f ft", meters * 3.28084)
        }
    }
}

// MARK: - Preview

#if DEBUG
#Preview {
    let manager  = MockNavigationManager()
    let tracker  = MockRouteTracker()
    let eta      = MockETACalculator()
    let vm = NavigationViewModel(
        navigationManager: manager,
        routeTracker: tracker,
        etaCalculator: eta
    )
    return NavigationView(viewModel: vm)
}
#endif
