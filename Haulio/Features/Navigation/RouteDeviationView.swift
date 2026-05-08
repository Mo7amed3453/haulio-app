import SwiftUI

/// Modal popup displayed when the user deviates >50 m off-route for >10 seconds.
///
/// Behaviour:
///   • Auto-dismisses after 30 seconds with no interaction (continues without rerouting).
///   • Selecting a reason stores it locally and triggers reroute via NavigationManager.
///   • Tapping "Dismiss" or the background overlay continues without rerouting.
@MainActor
struct RouteDeviationView: View {

    let onReasonSelected: (DeviationReason) -> Void
    let onDismiss: () -> Void

    @State private var remainingSeconds: Int = 30
    @State private var countdownTask: Task<Void, Never>?

    var body: some View {
        ZStack {
            // Semi-transparent scrim
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .onTapGesture { handleDismiss() }
                .accessibilityLabel("Dismiss")

            // Centred card
            cardContent
                .frame(maxWidth: 320)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color(.systemBackground))
                )
                .shadow(color: .black.opacity(0.28), radius: 24, x: 0, y: 8)
                // Stop tap on card propagating to scrim
                .onTapGesture { }
        }
        .task { await runCountdown() }
        .onDisappear { countdownTask?.cancel() }
    }

    // MARK: - Card

    private var cardContent: some View {
        VStack(spacing: 0) {

            // Header
            VStack(spacing: 4) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 28))
                    .foregroundColor(.orange)
                    .padding(.top, 24)

                Text("Why did you skip this road?")
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.primary)
                    .padding(.horizontal, 20)

                Text("Continuing in \(remainingSeconds)s…")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding(.bottom, 16)
            }

            Divider()

            // Reason buttons
            VStack(spacing: 0) {
                ForEach(DeviationReason.allCases) { reason in
                    Button {
                        countdownTask?.cancel()
                        onReasonSelected(reason)
                    } label: {
                        reasonLabel(reason)
                    }
                    .buttonStyle(.plain)

                    if reason != DeviationReason.allCases.last {
                        Divider()
                    }
                }

                Divider()

                // Dismiss (no reroute)
                Button {
                    handleDismiss()
                } label: {
                    Text("Dismiss")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.plain)
                .padding(.bottom, 4)
            }
        }
    }

    @ViewBuilder
    private func reasonLabel(_ reason: DeviationReason) -> some View {
        HStack {
            Image(systemName: reasonIcon(reason))
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.blue)
                .frame(width: 24)
            Text(reason.rawValue)
                .font(.body)
                .foregroundColor(.blue)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
    }

    private func reasonIcon(_ reason: DeviationReason) -> String {
        switch reason {
        case .roadClosed: return "road.lanes.curved.left"
        case .accident:   return "car.2.fill"
        case .other:      return "ellipsis.circle"
        }
    }

    // MARK: - Countdown

    private func handleDismiss() {
        countdownTask?.cancel()
        onDismiss()
    }

    private func runCountdown() async {
        for remaining in stride(from: 30, through: 1, by: -1) {
            guard !Task.isCancelled else { return }
            remainingSeconds = remaining
            try? await Task.sleep(for: .seconds(1))
        }
        guard !Task.isCancelled else { return }
        onDismiss()
    }
}

// MARK: - Preview

#if DEBUG
#Preview {
    RouteDeviationView(
        onReasonSelected: { reason in print("Selected: \(reason.rawValue)") },
        onDismiss: { print("Dismissed") }
    )
}
#endif
