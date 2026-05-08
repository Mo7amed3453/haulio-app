package app.haulio.android

import app.haulio.android.services.map.TileConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class TileConfigurationTest {
    @Test
    fun `default values remain stable`() {
        assertEquals("https://tiles.haulio.app", TileConfiguration.TILE_SERVER_BASE_URL)
        assertEquals(39.8283, TileConfiguration.DEFAULT_CENTER_LATITUDE, 0.0)
        assertEquals(-98.5795, TileConfiguration.DEFAULT_CENTER_LONGITUDE, 0.0)
        assertEquals(4.0, TileConfiguration.DEFAULT_ZOOM, 0.0)
    }
}
