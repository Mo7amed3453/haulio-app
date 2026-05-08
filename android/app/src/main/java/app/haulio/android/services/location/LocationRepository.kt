package app.haulio.android.services.location

import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeLocation(): Flow<LocationPoint>
}

class LocationRepositoryImpl(
    private val locationService: LocationService
) : LocationRepository {
    override fun observeLocation(): Flow<LocationPoint> = locationService.observeLocation()
}
