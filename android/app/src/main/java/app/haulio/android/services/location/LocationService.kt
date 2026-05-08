package app.haulio.android.services.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LocationPoint(val latitude: Double, val longitude: Double)

class LocationService(
    private val context: Context,
    private val fusedLocationProviderClient: FusedLocationProviderClient
) {
    fun observeLocation(): Flow<LocationPoint> = callbackFlow {
        if (!hasLocationPermission(context)) {
            close()
            return@callbackFlow
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    trySend(location.toLocationPoint())
                }
            }
        }

        requestLocationUpdates(callback)
        awaitClose {
            fusedLocationProviderClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(callback: LocationCallback) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        fusedLocationProviderClient.requestLocationUpdates(
            request,
            callback,
            context.mainLooper
        )
    }

    companion object {
        fun hasLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PermissionChecker.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PermissionChecker.PERMISSION_GRANTED
            return fine || coarse
        }
    }
}

internal fun Location.toLocationPoint(): LocationPoint = LocationPoint(
    latitude = latitude,
    longitude = longitude
)
