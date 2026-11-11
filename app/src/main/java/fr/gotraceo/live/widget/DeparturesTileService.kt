package fr.gotraceo.live.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.ui.MainActivity
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Tuile « Prochains passages » des réglages rapides, accessible même téléphone verrouillé.
 *
 * Elle montre un widget choisi dans l'application, ou le premier widget configuré. Elle n'est liée
 * qu'à l'ouverture du panneau : elle relit alors les passages en cache, avec des temps d'attente à
 * jour, et demande une actualisation si l'intervalle est écoulé. La toucher actualise tout de suite.
 *
 * Son icône est le prochain passage : c'est tout ce que montre une petite tuile.
 */
class DeparturesTileService : TileService() {

    /** Android ne dit pas aux applications quelles tuiles sont affichées : on retient ce qu'il signale ici. */
    override fun onTileAdded() {
        super.onTileAdded()
        Prefs(this).tileAdded = true
    }

    override fun onTileRemoved() {
        Prefs(this).tileAdded = false
        super.onTileRemoved()
    }

    override fun onStartListening() {
        super.onStartListening()
        // Une tuile ajoutée avant que l'application ne le retienne se signale à l'ouverture du volet.
        val prefs = Prefs(this)
        if (!prefs.tileAdded) prefs.tileAdded = true
        QuickTile.listening = WeakReference(this)
        show()
        val id = QuickTile.widgetId(this) ?: return
        if (WidgetUpdater.isDue(this, id, System.currentTimeMillis())) {
            WidgetUpdater.requestRefresh(this, id)
        } else {
            // Rien à demander, mais le tick doit tourner pour que la tuile et le widget suivent l'heure.
            WidgetUpdater.scheduleTick(this)
        }
    }

    override fun onStopListening() {
        if (QuickTile.listening?.get() === this) QuickTile.listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val id = QuickTile.widgetId(this)
        if (id != null) {
            WidgetUpdater.requestRefresh(this, id)
            return
        }
        // Aucun widget : la tuile mène à l'application, après déverrouillage.
        if (isLocked) unlockAndRun { openApp() } else openApp()
    }

    /** Recalcule la tuile à partir du dernier état du widget. */
    fun show() {
        val tile = qsTile ?: return
        val text = QuickTile.text(this)
        tile.label = text?.label ?: getString(R.string.tile_label)
        tile.contentDescription = text?.description ?: getString(R.string.tile_empty)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = text?.subtitle ?: getString(R.string.tile_empty)
        }
        tile.icon = text?.icon?.let { TileIconRenderer.render(this, it) }
            ?: Icon.createWithResource(this, R.drawable.ic_stat_departures)
        tile.state = if (text?.active == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

object QuickTile {

    private const val TAG = "TRWidget"

    /** La tuile pendant que le panneau est ouvert, pour la redessiner dès que le widget change. */
    @Volatile
    var listening: WeakReference<DeparturesTileService>? = null

    /** Widget montré par la tuile : celui choisi s'il existe encore, sinon le premier configuré. */
    fun widgetId(context: Context): Int? {
        val prefs = Prefs(context)
        val ids = WidgetUpdater.widgetIds(context).filter { prefs.config(it) != null }
        return prefs.tileWidget.takeIf { it in ids } ?: ids.firstOrNull()
    }

    fun text(context: Context): TileText? {
        val id = widgetId(context) ?: return null
        val widget = WidgetTexts.of(context, Prefs(context), id) ?: return null
        return NotificationContent.tile(widget.model, widget.lineTitle) { WidgetTexts.noticeText(context, it) }
    }

    /** Vrai si la tuile est dans les réglages rapides et montre ce widget. */
    fun shows(context: Context, widgetId: Int): Boolean =
        Prefs(context).tileAdded && widgetId(context) == widgetId

    /** Redessine la tuile si le panneau est ouvert ; sinon elle sera relue à la prochaine ouverture. */
    fun update(context: Context, widgetId: Int) {
        val service = listening?.get() ?: return
        if (widgetId(context) != widgetId) return
        // Le widget est souvent redessiné hors du fil principal, où vit la tuile.
        Handler(Looper.getMainLooper()).post {
            try {
                service.show()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Échec de la mise à jour de la tuile", e)
            }
        }
    }

    /** Propose d'ajouter la tuile aux réglages rapides (Android 13 et plus). */
    fun requestAdd(context: Context, executor: Executor, result: Consumer<Int>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val manager = context.getSystemService(StatusBarManager::class.java) ?: return false
        manager.requestAddTileService(
            ComponentName(context, DeparturesTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_stat_departures),
            executor,
            result,
        )
        return true
    }
}
