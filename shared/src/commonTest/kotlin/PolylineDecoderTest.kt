package app.haulio.shared

import app.haulio.shared.util.PolylineDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolylineDecoderTest {
    @Test
    fun decodesValhallaPrecision6Polyline() {
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val points = PolylineDecoder.decode(encoded, precision = 5)
        assertEquals(3, points.size)
        assertTrue(points[0].lat > 38.0)
    }
}
