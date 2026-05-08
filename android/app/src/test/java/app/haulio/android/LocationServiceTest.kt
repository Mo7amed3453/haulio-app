package app.haulio.android

import android.location.Location
import app.haulio.android.services.location.toLocationPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocationServiceTest {
    @Test
    fun `maps android location to location point`() {
        val location = Location("test").apply {
            latitude = 39.8283
            longitude = -98.5795
        }

        val point = location.toLocationPoint()

        assertEquals(39.8283, point.latitude, 0.0)
        assertEquals(-98.5795, point.longitude, 0.0)
    }
}
