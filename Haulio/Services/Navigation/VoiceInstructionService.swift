import AVFoundation
import Foundation

/// Announces turn-by-turn navigation instructions via AVSpeechSynthesizer.
///
/// Audio ducking (AVAudioSession .duckOthers) lowers music/podcasts while
/// the instruction plays and automatically restores them when finished.
///
/// Announcement schedule (distance thresholds):
///   • 1 mile  (1 609 m)  – "In 1 mile, turn right onto Main Street"
///   • ½ mile  (  805 m)  – "In half a mile, take exit 42B"
///   • At turn (   50 m)  – "Take exit 42B now"
@MainActor
final class VoiceInstructionService: NSObject {

    // MARK: - Distance Thresholds (metres)

    private static let farThreshold: Double = 1_609.34   // 1 mile
    private static let nearThreshold: Double = 804.67    // 0.5 mile
    private static let immediateThreshold: Double = 50.0 // at-turn

    // MARK: - Private State

    private let synthesizer = AVSpeechSynthesizer()
    private let audioSession = AVAudioSession.sharedInstance()

    private var lastStepID: UUID?
    private var announcedFar = false
    private var announcedNear = false
    private var announcedImmediate = false

    // MARK: - Lifecycle

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    /// Configure AVAudioSession for navigation. Call once when nav starts.
    func prepareAudioSession() {
        do {
            try audioSession.setCategory(
                .playback,
                mode: .voicePrompt,
                options: [.duckOthers, .mixWithOthers]
            )
            try audioSession.setActive(true)
        } catch {
            print("[VoiceInstruction] Session setup failed: \(error.localizedDescription)")
        }
    }

    /// Deactivate the audio session when navigation ends.
    func deactivateAudioSession() {
        do {
            try audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            print("[VoiceInstruction] Session deactivation failed: \(error.localizedDescription)")
        }
    }

    // MARK: - Public API

    /// Called on every location update. Speaks the appropriate instruction when
    /// a distance threshold is crossed for the current step.
    func updateDistance(_ distanceMeters: Double, for step: NavigationStep) {
        if step.id != lastStepID {
            resetState(for: step.id)
        }

        if distanceMeters <= Self.immediateThreshold, !announcedImmediate {
            announcedImmediate = true
            speak(immediateInstruction(for: step))
        } else if distanceMeters <= Self.nearThreshold, !announcedNear {
            announcedNear = true
            speak(nearInstruction(for: step))
        } else if distanceMeters <= Self.farThreshold, !announcedFar {
            announcedFar = true
            speak(farInstruction(for: step))
        }
    }

    /// Force an immediate announcement (e.g., "Rerouting…").
    func announceImmediately(_ text: String) {
        speak(text)
    }

    // MARK: - Instruction Builders

    private func farInstruction(for step: NavigationStep) -> String {
        if let exit = step.exitNumber {
            let destinations = step.destinationNames.prefix(2).joined(separator: " / ")
            let toward = destinations.isEmpty ? "" : " toward \(destinations)"
            return "In 1 mile, take exit \(exit)\(toward)"
        }
        return "In 1 mile, \(maneuverPhrase(step.maneuver)) \(step.streetName)"
    }

    private func nearInstruction(for step: NavigationStep) -> String {
        if let exit = step.exitNumber {
            return "In half a mile, take exit \(exit)"
        }
        return "In half a mile, \(maneuverPhrase(step.maneuver)) \(step.streetName)"
    }

    private func immediateInstruction(for step: NavigationStep) -> String {
        if let exit = step.exitNumber {
            return "Take exit \(exit) now"
        }
        return "\(maneuverPhrase(step.maneuver).capitalized) \(step.streetName) now"
    }

    private func maneuverPhrase(_ maneuver: ManeuverType) -> String {
        switch maneuver {
        case .turnLeft:          return "turn left onto"
        case .turnRight:         return "turn right onto"
        case .turnSharpLeft:     return "turn sharp left onto"
        case .turnSharpRight:    return "turn sharp right onto"
        case .turnSlightLeft:    return "keep left toward"
        case .turnSlightRight:   return "keep right toward"
        case .straight:          return "continue straight on"
        case .exitLeft,
             .exitRight:         return "take the exit toward"
        case .ramp:              return "take the ramp toward"
        case .merge:             return "merge onto"
        case .roundaboutLeft:    return "take the roundabout, then exit left onto"
        case .roundaboutRight:   return "take the roundabout, then exit right onto"
        case .arrive:            return "arrive at"
        case .depart:            return "head toward"
        case .unknown:           return "proceed to"
        }
    }

    // MARK: - Private Helpers

    private func speak(_ text: String) {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.52
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0
        synthesizer.speak(utterance)
    }

    private func resetState(for stepID: UUID) {
        lastStepID = stepID
        announcedFar = false
        announcedNear = false
        announcedImmediate = false
    }
}

// MARK: - AVSpeechSynthesizerDelegate

extension VoiceInstructionService: AVSpeechSynthesizerDelegate {
    nonisolated func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        // Audio ducking releases automatically after synthesis finishes.
    }
}
