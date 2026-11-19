package fr.gotraceo.live.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.Network
import fr.gotraceo.live.data.Networks
import fr.gotraceo.live.data.TransportMode

/** Libellés partagés par les écrans de l'application. */
object UiText {

    const val PRIM_PORTAL_URL = "https://prim.iledefrance-mobilites.fr/"

    /**
     * « Métro 4 », « RER B », « Bus 38 »… En Île-de-France, le nom d'une ligne est son numéro ; ailleurs
     * c'est souvent son parcours (« Bréviandes - Voves ») : le titre se fait alors avec son code. Un
     * code long (« Flexo 51 », « Navette ») se suffit : il n'a pas besoin du nom de son mode.
     */
    fun lineTitle(line: Line): String {
        val idfm = Networks.isIdfm(line.networkId)
        val prefix = when {
            line.mode == "metro" -> "Métro"
            line.isRer() -> "RER"
            line.mode == "rail" -> "Train"
            line.mode == "tram" -> "Tram"
            line.mode == "bus" -> "Bus"
            // « Câble » est le nom du Câble C1 francilien ; ailleurs, c'est un téléphérique.
            line.mode == "cableway" -> if (idfm) "Câble" else "Téléphérique"
            line.mode == "funicular" -> "Funiculaire"
            line.mode == "boat" -> "Bateau"
            else -> ""
        }
        val name = (if (idfm) line.name else line.shortName.ifBlank { line.name }).trim()
        return if (prefix.isNotEmpty() && name.length <= 6 && !name.startsWith(prefix, ignoreCase = true)) {
            "$prefix $name"
        } else {
            name
        }
    }

    /** Ce que regroupe un mode. Ces exemples sont franciliens : ailleurs, le mode se présente seul. */
    fun modeSubtitle(mode: TransportMode, networkId: String = Networks.IDFM): String? =
        if (!Networks.isIdfm(networkId)) {
            null
        } else {
            when (mode) {
                TransportMode.METRO -> "Lignes 1 à 14"
                TransportMode.RER -> "RER A, B, C, D, E"
                TransportMode.TRAIN -> "Transilien, TER"
                TransportMode.TRAM -> "Lignes de tramway"
                TransportMode.BUS -> "RATP, Île-de-France Mobilités, Noctilien…"
                TransportMode.OTHER -> "Téléphérique, funiculaire de Montmartre"
                TransportMode.BOAT -> null
            }
        }

    /** Pastille de texte d'un mode de transport, dans ce réseau. */
    fun modeChip(mode: TransportMode, network: Network = Networks.ILE_DE_FRANCE): String = when (mode) {
        TransportMode.METRO -> "M"
        TransportMode.RER -> "RER"
        TransportMode.TRAIN -> "Train"
        TransportMode.TRAM -> "T"
        TransportMode.BUS -> "Bus"
        // Un réseau qui n'a qu'un funiculaire ne se voit pas proposer un câble.
        TransportMode.OTHER -> if (!network.isIdfm && "cableway" !in network.modes && "funicular" in network.modes) "Funi" else "Câble"
        TransportMode.BOAT -> "Bateau"
    }

    fun openUrl(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, url, Toast.LENGTH_LONG).show()
        }
    }
}
