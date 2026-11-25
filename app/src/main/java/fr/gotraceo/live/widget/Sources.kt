package fr.gotraceo.live.widget

import android.content.Context
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.NearbyLine
import fr.gotraceo.live.data.NetworkCatalogStore
import fr.gotraceo.live.data.PassageSnapshot
import fr.gotraceo.live.data.PassageSource
import fr.gotraceo.live.data.Prefs
import java.io.File

/**
 * Sources de passages de l'application : pour l'Île-de-France, la clé, son quota du jour et le repli
 * sur Tracéo ; pour un autre réseau, le cache de Tracéo. Le `nextAt` de Tracéo est retenu réseau par
 * réseau, pour caler les actualisations suivantes.
 */
object Sources {

    private const val CATALOG_FILE = "networks.json"

    /** Le catalogue des réseaux gardé sur le téléphone. */
    fun catalog(context: Context): NetworkCatalogStore =
        NetworkCatalogStore(File(context.applicationContext.noBackupFilesDir, CATALOG_FILE))

    fun passages(context: Context, networkId: String): PassageSource {
        val prefs = Prefs(context)
        val base = DataSources.networkSource(
            networkId, prefs.apiKey, prefs.primFormats, QuotaAlerts.listener(context), prefs.primPaused,
        )
        return PassageSource { stopId, lineId -> base.passages(stopId, lineId).also { note(prefs, networkId, it) } }
    }

    fun proximity(context: Context): (NearbyLine) -> Result<PassageSnapshot> {
        val prefs = Prefs(context)
        val base = DataSources.proximitySource(
            prefs.apiKey, prefs.primFormats, QuotaAlerts.listener(context), prefs.primPaused,
        )
        return { nearby ->
            base(nearby).also { result -> result.getOrNull()?.let { note(prefs, nearby.line.networkId, it) } }
        }
    }

    /** Retient l'heure du prochain relevé du réseau chez Tracéo ; sans annonce (collecte suspendue), l'oublie. */
    private fun note(prefs: Prefs, networkId: String, snapshot: PassageSnapshot) {
        if (!snapshot.viaTraceo) return
        val next = snapshot.nextAt ?: 0L
        if (next != prefs.traceoNextAt(networkId)) prefs.setTraceoNextAt(networkId, next)
    }
}
