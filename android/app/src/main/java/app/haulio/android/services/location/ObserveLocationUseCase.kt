package app.haulio.android.services.location

import kotlinx.coroutines.flow.Flow

class ObserveLocationUseCase(
    private val locationRepository: LocationRepository
) {
    operator fun invoke(): Flow<LocationPoint> = locationRepository.observeLocation()
}
