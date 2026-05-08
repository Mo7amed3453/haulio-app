package app.haulio.android.services.map

object TileConfiguration {
    const val TILE_SERVER_BASE_URL = "https://tiles.haulio.app"
    const val VECTOR_TILE_PATH = "https://tiles.haulio.app/{z}/{x}/{y}.pbf"
    const val DEFAULT_CENTER_LATITUDE = 39.8283
    const val DEFAULT_CENTER_LONGITUDE = -98.5795
    const val DEFAULT_ZOOM = 4.0
    const val ATTRIBUTION = "© OpenStreetMap contributors"
}
