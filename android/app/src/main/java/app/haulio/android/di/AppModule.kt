package app.haulio.android.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.haulio.android.features.address.AddressSearchViewModel
import app.haulio.android.features.crime.CrimeViewModel
import app.haulio.android.features.delivery.ArrivalViewModel
import app.haulio.android.features.fuel.FuelViewModel
import app.haulio.android.features.incident.IncidentReportViewModel
import app.haulio.android.features.map.MapViewModel
import app.haulio.android.features.navigation.NavigationViewModel
import app.haulio.android.features.radar.RadarViewModel
import app.haulio.android.features.route.RouteCompareViewModel
import app.haulio.android.features.scanner.BarcodeScannerViewModel
import app.haulio.android.features.traffic.TrafficViewModel
import app.haulio.android.services.address.IAddressParser
import app.haulio.android.services.address.IDeliveryLogger
import app.haulio.android.services.address.IGeocodingManager
import app.haulio.android.services.address.MockAddressParser
import app.haulio.android.services.address.MockDeliveryLogger
import app.haulio.android.services.address.MockGeocodingManager
import app.haulio.android.services.crime.ICrimeRepository
import app.haulio.android.services.crime.MockCrimeRepository
import app.haulio.android.services.fuel.IFuelDataAggregator
import app.haulio.android.services.fuel.MockFuelDataAggregator
import app.haulio.android.services.location.LocationRepository
import app.haulio.android.services.location.LocationRepositoryImpl
import app.haulio.android.services.location.LocationService
import app.haulio.android.services.location.ObserveLocationUseCase
import app.haulio.android.services.map.MapStyleProvider
import app.haulio.android.services.navigation.INavigationManager
import app.haulio.android.services.navigation.MockNavigationManager
import app.haulio.android.services.navigation.VoiceInstructionService
import app.haulio.android.services.prefs.appDataStore
import app.haulio.android.services.radar.IRadarProximityEngine
import app.haulio.android.services.radar.IRadarRepository
import app.haulio.android.services.radar.MockRadarProximityEngine
import app.haulio.android.services.radar.MockRadarRepository
import app.haulio.android.services.traffic.IIncidentRepository
import app.haulio.android.services.traffic.IRerouteListener
import app.haulio.android.services.traffic.IRouteClient
import app.haulio.android.services.traffic.ITrafficAggregator
import app.haulio.android.services.traffic.MockIncidentRepository
import app.haulio.android.services.traffic.MockRerouteListener
import app.haulio.android.services.traffic.MockRouteClient
import app.haulio.android.services.traffic.MockTrafficAggregator
import com.google.android.gms.location.LocationServices
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { LocationServices.getFusedLocationProviderClient(get()) }
    single { LocationService(get(), get()) }
    single<LocationRepository> { LocationRepositoryImpl(get()) }
    single { ObserveLocationUseCase(get()) }
    single { MapStyleProvider(get()) }

    // DataStore — single shared Preferences store for all features
    single<DataStore<Preferences>> { androidContext().appDataStore }

    // Navigation — swap MockNavigationManager for KmmNavigationManagerBridge in production.
    single<INavigationManager> { MockNavigationManager() }
    factory { VoiceInstructionService(get()) }

    // Address / Geocoding — swap Mock* for Kmm*Bridge in production.
    single<IAddressParser>     { MockAddressParser() }
    single<IGeocodingManager>  { MockGeocodingManager() }
    single<IDeliveryLogger>    { MockDeliveryLogger() }

    // Traffic bridge — swap Mock* for Kmm*Bridge when KMM traffic layer ships.
    single<ITrafficAggregator>  { MockTrafficAggregator() }
    single<IRouteClient>        { MockRouteClient() }
    single<IIncidentRepository> { MockIncidentRepository() }
    single<IRerouteListener>    { MockRerouteListener() }

    // Fuel bridge — swap MockFuelDataAggregator for KmmFuelDataAggregatorBridge in production.
    single<IFuelDataAggregator> { MockFuelDataAggregator() }

    // Crime bridge — swap MockCrimeRepository for KmmCrimeRepositoryBridge in production.
    single<ICrimeRepository> { MockCrimeRepository() }

    // Radar bridge — swap Mock* for Kmm*Bridge when KMM radar layer ships.
    single<IRadarRepository>       { MockRadarRepository() }
    single<IRadarProximityEngine>  { MockRadarProximityEngine() }

    // ViewModels
    viewModel { MapViewModel(get()) }
    viewModel { NavigationViewModel(get(), get(), get()) }
    viewModel { AddressSearchViewModel(get(), get(), get()) }
    viewModel { BarcodeScannerViewModel(get(), get()) }
    viewModel { ArrivalViewModel(get(), get()) }
    viewModel { TrafficViewModel(get(), get(), get<DataStore<Preferences>>()) }
    viewModel { RouteCompareViewModel(get(), get<DataStore<Preferences>>()) }
    viewModel { IncidentReportViewModel(get()) }
    viewModel { FuelViewModel(get()) }
    viewModel { CrimeViewModel(get(), get<DataStore<Preferences>>()) }
    viewModel { RadarViewModel(get(), get()) }
}
