package es.fuelradar.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {
    fun permitted(context: Context) = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun locate(context: Context): Position? {
        if (!permitted(context)) return null
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (providers.isEmpty()) return null
        val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0..120_000_000_000L }
            .minByOrNull { it.accuracy }
        if (last != null) return Position(last.latitude, last.longitude, last.time)
        return withTimeoutOrNull(25_000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) {
                            manager.removeUpdates(this)
                            continuation.resume(Position(location.latitude, location.longitude, location.time))
                        }
                    }
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                    @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                var registered = false
                providers.forEach { provider ->
                    try {
                        manager.requestLocationUpdates(provider, 1000, 0f, listener, Looper.getMainLooper())
                        registered = true
                    } catch (_: SecurityException) { }
                }
                if (!registered && continuation.isActive) continuation.resume(null)
            }
        }
    }
}
