package app.haulio.shared.traffic.models

/**
 * Identifies the data provider for a [TrafficEvent].
 */
enum class TrafficSource {
    /** US Department of Transportation 511 feed. */
    USDOT_511,

    /** Crowd-sourced report submitted by a Haulio driver. */
    CROWD,

    /** TomTom real-time flow API (extreme-mode only). */
    TOMTOM,
}
