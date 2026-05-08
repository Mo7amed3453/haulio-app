package app.haulio.android.di

import app.haulio.android.features.map.MapViewModel
import app.haulio.android.features.navigation.NavigationViewModel
import app.haulio.android.services.location.LocationRepository
import app.haulio.android.services.location.LocationRepositoryImpl
import app.haulio.android.services.location.LocationService
import app.haulio.android.services.location.ObserveLocationUseCase
import app.haulio.android.services.map.MapStyleProvider
import app.haulio.android.services.navigation.INavigationManager
import app.haulio.android.services.navigation.MockNavigationManager
import app.haulio.android.services.navigation.VoiceInstructionService
import com.google.android.gms.location.LocationServices
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { LocationServices.getFusedLocationProviderClient(get()) }
    single { LocationService(get(), get()) }
    single<LocationRepository> { LocationRepositoryImpl(get()) }
    single { ObserveLocationUseCase(get()) }
    single { MapStyleProvider(get()) }

    // Navigation — swap MockNavigationManager for KmmNavigationManagerBridge in production.
    single<INavigationManager> { MockNavigationManager() }
    factory { VoiceInstructionService(get()) }

    viewModel { MapViewModel(get()) }
    viewModel { NavigationViewModel(get(), get(), get()) }
}
