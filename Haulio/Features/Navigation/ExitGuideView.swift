import SwiftUI

// MARK: - MUTCD Exit Guide Sign
//
// Conforms to US MUTCD Part 2E standards:
//   • Background: #006B3C (Federal highway green)
//   • White text and border
//   • Exit number badge – top-left
//   • Destination name(s) – centred, bold
//   • Service icons (gas / food / lodging …) – bottom-left row
//   • Corner radius: 8 pt
//   • Border: 2 pt white stroke
//   • Aspect ratio: ~3 : 2
//   • Font: Highway Gothic (see Resources/Fonts/). If unavailable the system
//     monospaced digit variant is used as the closest approximation.

@MainActor
struct ExitGuideView: View {

    let step: NavigationStep

    // MUTCD standard highway green  (Pantone 342 C ≈ #006B3C)
    private static let highwayGreen = Color(red: 0 / 255, green: 107 / 255, blue: 60 / 255)

    // Highway Gothic is a US federal font; include "HighwayGothic.ttf" in
    // Resources/Fonts/ and register it in Info.plist under UIAppFonts.
    // The fallback below is the closest system approximation.
    private func highwayFont(size: CGFloat, weight: Font.Weight = .regular) -> Font {
        if let _ = UIFont(name: "HighwayGothic", size: size) {
            return Font.custom("HighwayGothic", size: size).weight(weight)
        }
        return Font.system(size: size, weight: weight, design: .monospaced)
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(Self.highwayGreen)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .strokeBorder(Color.white, lineWidth: 2)
                )

            VStack(alignment: .leading, spacing: 6) {

                // Exit number badge – top-left
                HStack(alignment: .top) {
                    if let exitNumber = step.exitNumber {
                        exitBadge(number: exitNumber)
                    }
                    Spacer()
                }

                Spacer(minLength: 0)

                // Destination names – centred
                VStack(spacing: 2) {
                    if step.destinationNames.isEmpty {
                        Text(step.streetName)
                            .font(highwayFont(size: 22, weight: .bold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .minimumScaleFactor(0.7)
                            .frame(maxWidth: .infinity)
                    } else {
                        ForEach(step.destinationNames.prefix(3), id: \.self) { name in
                            Text(name)
                                .font(highwayFont(size: 20, weight: .bold))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                                .lineLimit(1)
                                .minimumScaleFactor(0.65)
                                .frame(maxWidth: .infinity)
                        }
                    }
                }

                Spacer(minLength: 0)

                // Service icons – bottom row
                if !step.serviceIcons.isEmpty {
                    HStack(spacing: 14) {
                        ForEach(step.serviceIcons.prefix(5), id: \.rawValue) { icon in
                            serviceIconView(icon)
                        }
                        Spacer()
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .aspectRatio(3.0 / 2.0, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 16)
    }

    // MARK: - Sub-views

    @ViewBuilder
    private func exitBadge(number: String) -> some View {
        VStack(spacing: 1) {
            Text("EXIT")
                .font(highwayFont(size: 10, weight: .semibold))
                .foregroundColor(.white)
                .kerning(1)
            Text(number)
                .font(highwayFont(size: 20, weight: .bold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .overlay(
            RoundedRectangle(cornerRadius: 4)
                .strokeBorder(Color.white, lineWidth: 1.5)
        )
    }

    @ViewBuilder
    private func serviceIconView(_ icon: ServiceIcon) -> some View {
        Image(systemName: icon.rawValue)
            .font(.system(size: 20, weight: .medium))
            .foregroundColor(.white)
            .frame(width: 32, height: 32)
    }
}

// MARK: - Animated Overlay Wrapper

/// Wraps ExitGuideView with a spring slide-in from the top.
/// Place this inside a VStack at the top of your navigation ZStack.
@MainActor
struct ExitGuideOverlay: View {

    let step: NavigationStep?
    let isVisible: Bool

    var body: some View {
        Group {
            if isVisible, let step {
                ExitGuideView(step: step)
                    .transition(
                        .asymmetric(
                            insertion: .move(edge: .top).combined(with: .opacity),
                            removal:   .move(edge: .top).combined(with: .opacity)
                        )
                    )
            }
        }
        .animation(.spring(response: 0.45, dampingFraction: 0.78), value: isVisible)
    }
}

// MARK: - Preview

#if DEBUG
#Preview("Exit Guide – with services") {
    let step = NavigationStep(
        maneuver: .exitRight,
        streetName: "Route 1 North",
        distanceMeters: 2_800,
        exitNumber: "42B",
        destinationNames: ["Milford", "Orange"],
        serviceIcons: [.gas, .food, .lodging]
    )
    return ExitGuideView(step: step)
        .padding()
        .background(Color.gray)
}

#Preview("Exit Guide – no exit number") {
    let step = NavigationStep(
        maneuver: .exitLeft,
        streetName: "I-95 Express",
        distanceMeters: 1_000,
        destinationNames: ["New Haven"],
        serviceIcons: [.gas]
    )
    return ExitGuideView(step: step)
        .padding()
        .background(Color.gray)
}
#endif
