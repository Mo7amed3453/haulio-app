package app.haulio.android

import app.haulio.android.features.map.MapViewModel
import app.haulio.android.services.location.LocationPoint
import app.haulio.android.services.location.LocationRepository
import app.haulio.android.services.location.ObserveLocationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updates ui state when location emits`() = runTest {
        val repository = object : LocationRepository {
            override fun observeLocation(): Flow<LocationPoint> {
                return flowOf(LocationPoint(1.0, 2.0))
            }
        }
        val viewModel = MapViewModel(ObserveLocationUseCase(repository))

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1.0, viewModel.uiState.value.userLocation!!.latitude, 0.0)
        assertEquals(2.0, viewModel.uiState.value.userLocation!!.longitude, 0.0)
    }
}
