package fr.gotraceo.live.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Données en arrière-plan : coupées pour l'appli (réglage « Données en arrière-plan ») ou par
 * l'économiseur de données, elles empêchent les widgets de s'actualiser seuls. Comme pour les
 * alarmes exactes, il n'existe pas de fenêtre système pour les demander : l'usager les rétablit
 * dans les réglages.
 */
object BackgroundDataAccess {

    /** Ouvre l'écran des données de l'appli, ou à défaut sa fiche dans les réglages. */
    fun openSettings(activity: Activity) {
        val app = Uri.fromParts("package", activity.packageName, null)
        try {
            activity.startActivity(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, app))
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
        }
    }
}
