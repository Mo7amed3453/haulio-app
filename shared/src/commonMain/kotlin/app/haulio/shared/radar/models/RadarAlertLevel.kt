package app.haulio.shared.radar.models

/**
 * Alert severity bucket based on the driver's distance to an approaching speed camera.
 *
 * Distances are one-way thresholds: the engine emits the highest applicable level.
 */
enum class RadarAlertLevel(val thresholdMiles: Double) {
    /** Camera is ~0.5 mi ahead — show a subtle visual indicator only. */
    VISUAL_FAR(0.5),
    /** Camera is ~0.3 mi ahead — show toast + single audio chime. */
    AUDIO_MID(0.3),
    /** Camera is ~0.1 mi ahead — large banner + urgent double chime. */
    URGENT_CLOSE(0.1),
}
