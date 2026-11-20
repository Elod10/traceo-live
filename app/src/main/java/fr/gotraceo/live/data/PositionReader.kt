package fr.gotraceo.live.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * Lit la position avec le service de localisation d'Android, sans dépendance à Google.
 *
 * Une position récente et assez précise est utilisée tout de suite. Sinon, le réseau et le GPS sont
 * interrogés en même temps : le premier résultat assez précis gagne, et on n'attend pas au-delà de
 * [TIMEOUT_MS]. Une précision de 100 m suffit pour distinguer deux arrêts.
 */
class PositionReader(context: Context) {

    private val app = context.applicationContext
    private val manager: LocationManager? = app.getSystemService(LocationManager::class.java)

    /** Faux si l'usager a refusé la position, ou n'a accordé que la position approximative. */
    val hasPreciseAccess: Boolean
        get() = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Vrai si la position peut être lue sans toucher le widget : permission « Toujours autoriser »,
     * facultative. Avant Android 10, la permission de position suffit.
     */
    val hasBackgroundAccess: Boolean
        get() = hasPreciseAccess && (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                app.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            )

    /** Position actuelle, ou null si elle reste introuvable. */
    @SuppressLint("MissingPermission")
    suspend fun read(timeoutMs: Long = TIMEOUT_MS): PositionFix? {
        val lm = manager ?: return null
        if (!hasPreciseAccess) return null
        val enabled = try {
            lm.getProviders(true)
        } catch (e: SecurityException) {
            return null
        }
        recent(lm, enabled, GOOD_AGE_MS, GOOD_ACCURACY_M)?.let { return it.toFix() }

        val providers = listOf(FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it in enabled }
        if (providers.isEmpty()) return null

        val finished = CompletableDeferred<Unit>()
        val lock = Any()
        var best: Location? = null
        val pending = AtomicInteger(providers.size)
        val offer = { location: Location? ->
            synchronized(lock) {
                if (location != null && isBetter(location, best)) best = location
                if (best?.let(::isPrecise) == true) finished.complete(Unit)
            }
            if (pending.decrementAndGet() <= 0) finished.complete(Unit)
        }
        val stops = providers.map { request(lm, it, offer) }
        try {
            withTimeoutOrNull(timeoutMs) { finished.await() }
        } finally {
            stops.forEach { it() }
        }
        val found = synchronized(lock) { best }
        return (found ?: recent(lm, enabled, FALLBACK_AGE_MS, Float.MAX_VALUE))?.toFix()
    }

    /** Lance une demande de position et renvoie de quoi l'arrêter. */
    @SuppressLint("MissingPermission")
    private fun request(lm: LocationManager, provider: String, offer: (Location?) -> Unit): () -> Unit {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            try {
                lm.getCurrentLocation(provider, signal, app.mainExecutor, Consumer { offer(it) })
            } catch (e: SecurityException) {
                offer(null)
                return {}
            }
            return { signal.cancel() }
        }
        // Avant Android 11, toutes les méthodes de l'interface doivent être implémentées.
        val listener = object : LocationListener {
            private var answered = false

            override fun onLocationChanged(location: Location) {
                if (!answered) {
                    answered = true
                    offer(location)
                }
                lm.removeUpdates(this)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) {
                if (!answered) {
                    answered = true
                    offer(null)
                }
                lm.removeUpdates(this)
            }
        }
        try {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            offer(null)
            return {}
        }
        return { lm.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun recent(lm: LocationManager, enabled: List<String>, maxAgeMs: Long, maxAccuracyM: Float): Location? =
        enabled.mapNotNull { provider ->
            try {
                lm.getLastKnownLocation(provider)
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }
            .filter { ageMs(it) <= maxAgeMs && accuracyOf(it) <= maxAccuracyM }
            .minByOrNull { accuracyOf(it) }

    private fun isBetter(candidate: Location, current: Location?): Boolean =
        current == null || accuracyOf(candidate) < accuracyOf(current)

    private fun isPrecise(location: Location): Boolean = accuracyOf(location) <= GOOD_ACCURACY_M

    private fun accuracyOf(location: Location): Float = if (location.hasAccuracy()) location.accuracy else UNKNOWN_ACCURACY_M

    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L

    private fun Location.toFix() = PositionFix(GeoPoint(latitude, longitude), if (hasAccuracy()) accuracy else null)

    companion object {
        const val TIMEOUT_MS = 10_000L
        private const val FUSED_PROVIDER = "fused"
        private const val GOOD_ACCURACY_M = 100f
        private const val GOOD_AGE_MS = 2L * 60 * 1000
        private const val FALLBACK_AGE_MS = 10L * 60 * 1000
        private const val UNKNOWN_ACCURACY_M = 10_000f
    }
}
