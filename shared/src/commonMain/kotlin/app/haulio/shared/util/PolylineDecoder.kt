package app.haulio.shared.util

import app.haulio.shared.navigation.models.GeoPoint
import kotlin.math.pow

/**
 * Decodes Valhalla polyline strings (precision 6) into [GeoPoint]s.
 */
object PolylineDecoder {
    /**
     * Decode an encoded polyline to coordinates.
     */
    fun decode(encoded: String, precision: Int = 6): List<GeoPoint> {
        val coordinates = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0
        var lon = 0
        val factor = 10.0.pow(precision.toDouble())

        while (index < encoded.length) {
            val latChange = decodeValue(encoded, index)
            index = latChange.nextIndex
            lat += latChange.value

            val lonChange = decodeValue(encoded, index)
            index = lonChange.nextIndex
            lon += lonChange.value

            coordinates.add(GeoPoint(lat / factor, lon / factor))
        }
        return coordinates
    }

    private data class DecodedValue(val value: Int, val nextIndex: Int)

    private fun decodeValue(encoded: String, startIndex: Int): DecodedValue {
        var result = 0
        var shift = 0
        var index = startIndex
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20 && index < encoded.length + 1)

        val value = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        return DecodedValue(value, index)
    }
}
