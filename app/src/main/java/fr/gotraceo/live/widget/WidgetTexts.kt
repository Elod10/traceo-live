package fr.gotraceo.live.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.WidgetConfig
import fr.gotraceo.live.ui.UiText

/** Le contenu d'un widget, prêt à être mis en mots pour sa notification ou pour la tuile. */
class WidgetText(
    val config: WidgetConfig,
    val model: WidgetModel,
    /** « Métro 13 », ou null pour le format À proximité, qui montre plusieurs lignes. */
    val lineTitle: String?,
) {
    val isProximity: Boolean get() = model.format == WidgetFormat.PROXIMITE
}

object WidgetTexts {

    /** Le contenu actuel d'un widget, à partir de son dernier état ; null s'il n'est pas configuré. */
    fun of(context: Context, prefs: Prefs, widgetId: Int, now: Long = System.currentTimeMillis()): WidgetText? {
        val manager = AppWidgetManager.getInstance(context)
        val format = WidgetProviders.formatOf(manager.getAppWidgetInfo(widgetId)?.provider?.className)
        val config = prefs.config(widgetId) ?: return null
        // La taille du widget fixe le nombre de lignes connues du format À proximité. Celle du portrait :
        // l'orientation de l'écran au moment où la notification est écrite ne dit rien de l'écran d'accueil.
        val (width, height) = WidgetRenderer.widgetSizes(manager.getAppWidgetOptions(widgetId)).portrait
        val uncovered = config.auto && prefs.auto(widgetId).uncovered
        return if (format == WidgetFormat.PROXIMITE) {
            val state = prefs.proximity(widgetId)
            val networkId = state?.entries?.firstOrNull()?.nearby?.line?.networkId ?: config.networkId
            val model = WidgetModels.buildProximity(
                config, state, false, width, height, now, night = true,
                networkName = Sources.networkName(context, networkId),
                uncovered = uncovered,
            )
            WidgetText(config, model, null)
        } else {
            val model = WidgetModels.build(
                config, prefs.state(widgetId), prefs.hasApiKey, false, width, height, now, format,
                networkName = Sources.networkName(context, config.networkId),
                uncovered = uncovered,
            )
            WidgetText(config, model, UiText.lineTitle(config.line))
        }
    }

    /** Texte affiché à la place des passages. */
    fun noticeText(context: Context, notice: Notice): String? = when (notice.kind) {
        NoticeKind.NOT_CONFIGURED -> null
        NoticeKind.RECONFIGURE -> context.getString(R.string.widget_reconfigure_title)
        NoticeKind.LOADING -> context.getString(R.string.widget_loading)
        NoticeKind.NO_NETWORK -> context.getString(R.string.widget_no_network_title)
        NoticeKind.NO_DEPARTURE -> notice.detail ?: context.getString(R.string.widget_no_departure)
        NoticeKind.ERROR -> notice.detail ?: context.getString(R.string.notification_error)
    }
}
