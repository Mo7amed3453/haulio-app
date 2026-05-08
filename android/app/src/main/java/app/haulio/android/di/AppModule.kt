package app.haulio.android.di

import app.haulio.android.features.address.AddressSearchViewModel
import app.haulio.android.features.delivery.ArrivalViewModel
import app.haulio.android.features.map.MapViewModel
import app.haulio.android.features.navigation.NavigationViewModel
import app.haulio.android.features.scanner.BarcodeScannerViewModel
import app.haulio.android.services.address.IAddressParser
import app.haulio.android.services.address.IDeliveryLogger
import app.haulio.android.services.address.IGeocodingManager
import app.haulio.android.services.address.MockAddressParser
import app.haulio.android.services.address.MockDeliveryLogger
import app.haulio.android.services.address.MockGeocodingManager
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

    // Address / Geocoding — swap Mock* for Kmm*Bridge in production.
    single<IAddressParser> { MockAddressParser() }
    single<IGeocodingManager> { MockGeocodingManager() }
    single<IDeliveryLogger> { MockDeliveryLogger() }

    viewModel { MapViewModel(get()) }
    viewModel { NavigationViewModel(get(), get(), get()) }
    viewModel { AddressSearchViewModel(get(), get(), get()) }
    viewModel { BarcodeScannerViewModel(get(), get()) }
    viewModel { ArrivalViewModel(get(), get()) }
}
