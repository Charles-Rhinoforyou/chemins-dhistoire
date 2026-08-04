package com.cheminsdhistoire.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Fournit un flux continu de positions GPS réelles.
 * L'appelant doit avoir obtenu la permission ACCESS_FINE_LOCATION.
 */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 5_000L): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(2_000L)
            .setMinUpdateDistanceMeters(20f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        // Position immédiate si disponible, pour ne pas attendre le premier fix.
        client.lastLocation.addOnSuccessListener { last -> last?.let { trySend(it) } }

        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())

        awaitClose { client.removeLocationUpdates(callback) }
    }

    companion object {
        /** Distance en mètres entre deux points (formule de Haversine). */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
            return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        }
    }
}
