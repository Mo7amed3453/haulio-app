package app.haulio.shared.radar

/**
 * Local stub for the KMM shared LegalGeofence object.
 * Radar detectors are banned in Virginia (VA) and Washington DC.
 * Replace with actual KMM artifact when the shared module is wired.
 */
object LegalGeofence {

    /**
     * Returns true if ([lat], [lng]) is within a jurisdiction where radar detectors are banned.
     * Currently: Virginia and Washington DC.
     */
    fun isRadarBanned(lat: Double, lng: Double): Boolean {
        // DC bounding box
        if (lat in 38.791..38.995 && lng in -77.120..-76.909) return true
        // Virginia coarse bounding box
        if (lat in 36.54..39.47 && lng in -83.68..-75.24) return true
        return false
    }
}
