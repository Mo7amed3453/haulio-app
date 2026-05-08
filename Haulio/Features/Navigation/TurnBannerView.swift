import SwiftUI

/// Top banner showing the current maneuver arrow, next street name,
/// and distance to the turn. Updates reactively via NavigationViewModel.
@MainActor
struct TurnBannerView: View {

    let step: NavigationStep
    let distanceMeters: Double

    // MARK: - Body

    var body: some View {
        HStack(spacing: 16) {

            // Maneuver icon
            Image(systemName: maneuverSystemImage(step.maneuver))
                .font(.system(size: 36, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 48, alignment: .center)
                .accessibilityLabel(maneuverAccessibilityLabel(step.maneuver))

            // Street + distance
            VStack(alignment: .leading, spacing: 2) {
                Text(step.streetName)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                Text(formattedDistance)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundColor(.white.opacity(0.85))
            }

            Spacer()

            // Exit number pill (if applicable)
            if let exit = step.exitNumber {
                exitPill(number: exit)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(bannerBackground)
    }

    // MARK: - Sub-views

    @ViewBuilder
    private var bannerBackground: some View {
        Color(.systemBlue)
            .opacity(0.96)
            .ignoresSafeArea(edges: .top)
            .shadow(color: .black.opacity(0.3), radius: 8, x: 0, y: 4)
    }

    @ViewBuilder
    private func exitPill(number: String) -> some View {
        VStack(spacing: 1) {
            Text("EXIT")
                .font(.system(size: 9, weight: .semibold))
                .foregroundColor(.white.opacity(0.9))
                .kerning(0.8)
            Text(number)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .overlay(
            RoundedRectangle(cornerRadius: 5)
                .strokeBorder(Color.white.opacity(0.8), lineWidth: 1.5)
        )
    }

    // MARK: - Formatting

    private var formattedDistance: String {
        if distanceMeters >= 1_609.34 {
            return String(format: "%.1f mi", distanceMeters / 1_609.34)
        } else {
            let feet = distanceMeters * 3.28084
            if feet >= 500 {
                return String(format: "%.0f ft", feet)
            } else {
                return String(format: "%.0f ft", feet)
            }
        }
    }

    // MARK: - Icon Mapping

    private func maneuverSystemImage(_ maneuver: ManeuverType) -> String {
        switch maneuver {
        case .turnLeft:          return "arrow.turn.up.left"
        case .turnRight:         return "arrow.turn.up.right"
        case .turnSharpLeft:     return "arrow.turn.down.left"
        case .turnSharpRight:    return "arrow.turn.down.right"
        case .turnSlightLeft:    return "arrow.up.left"
        case .turnSlightRight:   return "arrow.up.right"
        case .straight:          return "arrow.up"
        case .exitLeft:          return "arrow.up.left"
        case .exitRight:         return "arrow.up.right"
        case .ramp:              return "arrow.turn.up.right"
        case .merge:             return "arrow.merge"
        case .roundaboutLeft:    return "arrow.counterclockwise"
        case .roundaboutRight:   return "arrow.clockwise"
        case .arrive:            return "mappin.circle.fill"
        case .depart:            return "location.fill"
        case .unknown:           return "arrow.up"
        }
    }

    private func maneuverAccessibilityLabel(_ maneuver: ManeuverType) -> String {
        switch maneuver {
        case .turnLeft:          return "Turn left"
        case .turnRight:         return "Turn right"
        case .turnSharpLeft:     return "Sharp left"
        case .turnSharpRight:    return "Sharp right"
        case .turnSlightLeft:    return "Keep left"
        case .turnSlightRight:   return "Keep right"
        case .straight:          return "Straight ahead"
        case .exitLeft:          return "Exit left"
        case .exitRight:         return "Exit right"
        case .ramp:              return "Take ramp"
        case .merge:             return "Merge"
        case .roundaboutLeft,
             .roundaboutRight:   return "Roundabout"
        case .arrive:            return "Arrive"
        case .depart:            return "Depart"
        case .unknown:           return "Proceed"
        }
    }
}

// MARK: - Preview

#if DEBUG
#Preview("Turn Right") {
    VStack {
        TurnBannerView(
            step: NavigationStep(
                maneuver: .turnRight,
                streetName: "Route 1 North",
                distanceMeters: 650,
                exitNumber: "42B"
            ),
            distanceMeters: 650
        )
        Spacer()
    }
    .background(Color.gray.opacity(0.2))
}

#Preview("Straight – far") {
    VStack {
        TurnBannerView(
            step: NavigationStep(
                maneuver: .straight,
                streetName: "I-95 Express",
                distanceMeters: 3_200
            ),
            distanceMeters: 3_200
        )
        Spacer()
    }
}
#endif
