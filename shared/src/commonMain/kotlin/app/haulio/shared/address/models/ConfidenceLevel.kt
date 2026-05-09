package app.haulio.shared.address.models

import kotlinx.serialization.Serializable

/**
 * Confidence level for a geocoded address result.
 *
 * @property VERIFIED_ZIP4 Address verified with full ZIP+4 precision.
 * @property APPROXIMATE Address geocoded but without ZIP+4 or with lower confidence.
 * @property NOT_FOUND Neither Pelias nor fallback geocoder returned a usable result.
 */
@Serializable
enum class ConfidenceLevel {
    VERIFIED_ZIP4,
    APPROXIMATE,
    NOT_FOUND,
}
