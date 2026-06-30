package com.farm_tech.farmhub.services
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import android.location.LocationManager

class LocationService(private val context: Context) {
    private val TAG = "LocationService"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // Use platform LocationManager to avoid adding Play Services dependency for this utility.
    suspend fun getCurrentLocation(): Location = suspendCancellableCoroutine { continuation ->
        try {
            val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val passive = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            val location = gps ?: network ?: passive
            if (location != null) {
                Log.d(TAG, "Location obtained: lat=${'$'}{location.latitude} lon=${'$'}{location.longitude}")
                continuation.resume(location)
            } else {
                continuation.resumeWithException(Exception("Location is null"))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: missing permissions: ${'$'}e")
            continuation.resumeWithException(e)
        }
    }
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
