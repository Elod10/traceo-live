package fr.gotraceo.live.widget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.PositionReader
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.ui.MainActivity
import java.time.ZonedDateTime
import kotlin.math.max

/**
 * Notification d'un widget : les mêmes passages, lisibles sur l'écran de verrouillage de tous les
 * téléphones, même ceux qui n'y acceptent pas de widget. Elle suit le widget : elle est mise à jour
 * chaque fois qu'il est redessiné, donc sans requête de plus, et seulement pendant sa plage horaire.
 *
 * Sous Android 16, elle peut être mise en avant (Live Update) pendant sa plage horaire : en tête de
 * l'écran de verrouillage, avec le prochain passage dans une pastille de la barre d'état.
 */
object PassageNotifications {

    const val ACTION_DISMISSED = "fr.gotraceo.live.action.NOTIFICATION_DISMISSED"

    private const val CHANNEL_ID = "passages"

    /** Les notifications des widgets ont une étiquette : celle du service de position n'en a pas. */
    private const val NOTIFICATION_TAG = "widget"
    private const val TAG = "TRWidget"
    private const val ICON_DP = 40f

    /**
     * Notification.EXTRA_REQUEST_PROMOTED_ONGOING, ajoutée par Android 16 QPR (36.1) et absente du
     * SDK 36 : on la donne par sa valeur. Les versions qui ne la connaissent pas l'ignorent.
     */
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** Vrai si Android laisse l'application afficher cette notification. */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return granted && manager.areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
    }

    /**
     * Live Update demandée pour ce widget : l'option est activée et une plage horaire est définie.
     * Android recommande les Live Updates pour des activités limitées dans le temps, pas permanentes.
     */
    fun wantsLiveUpdate(prefs: Prefs, widgetId: Int): Boolean =
        prefs.liveUpdate(widgetId) && prefs.timeWindow(widgetId) != null

    /** Vrai si Android peut mettre la notification en avant : Android 16 et réglage de l'usager. */
    fun canPromote(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        return context.getSystemService(NotificationManager::class.java)?.canPostPromotedNotifications() == true
    }

    /** Active ou retire la notification d'un widget. */
    fun setEnabled(context: Context, widgetId: Int, enabled: Boolean) {
        Prefs(context).setNotifies(widgetId, enabled)
        if (enabled) update(context, widgetId) else cancel(context, widgetId)
    }

    /** Affiche ou met à jour la notification d'un widget, si elle est activée. */
    fun update(context: Context, widgetId: Int) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (!prefs.notifies(widgetId) || !canPost(app)) return
        try {
            val manager = app.getSystemService(NotificationManager::class.java) ?: return
            // Hors de la plage horaire, ou balayée pendant celle-ci : la notification attend la suivante.
            val waiting = !prefs.inTimeWindow(widgetId) || System.currentTimeMillis() < prefs.notifyHiddenUntil(widgetId)
            val notification = if (waiting) null else build(app, prefs, widgetId)
            if (notification == null) {
                manager.cancel(NOTIFICATION_TAG, widgetId)
            } else {
                manager.notify(NOTIFICATION_TAG, widgetId, notification)
            }
        } catch (e: RuntimeException) {
            // Une notification refusée ne doit pas empêcher le widget de s'afficher.
            Log.w(TAG, "Échec de la notification du widget $widgetId", e)
        }
    }

    fun cancel(context: Context, widgetId: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_TAG, widgetId)
    }

    /**
     * Notification balayée. Avec une plage horaire, elle revient à la plage suivante ; sans plage,
     * l'option est désactivée, comme le recommande Android pour une notification qu'on écarte.
     */
    fun onDismissed(context: Context, widgetId: Int) {
        val prefs = Prefs(context)
        val window = prefs.timeWindow(widgetId)
        if (window == null) {
            prefs.setNotifies(widgetId, false)
            return
        }
        // Balayée juste après la fin de la plage, avant que le tick ne la retire : rien à masquer,
        // et surtout pas l'option à désactiver, puisqu'elle doit revenir à la plage suivante.
        val end = window.currentEnd(ZonedDateTime.now()) ?: return
        prefs.hideNotifyUntil(widgetId, end.toInstant().toEpochMilli())
    }

    private fun build(context: Context, prefs: Prefs, widgetId: Int): Notification? {
        val widget = WidgetTexts.of(context, prefs, widgetId) ?: return null
        val config = widget.config
        val proximity = widget.isProximity
        val content = NotificationContent.build(widget.model, widget.lineTitle) { WidgetTexts.noticeText(context, it) }
            ?: return null

        createChannel(context)
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_departures)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setSubText(content.subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent(context, widgetId))
            .setDeleteIntent(dismissIntent(context, widgetId))
            .addAction(
                action(context, R.drawable.ic_refresh, R.string.notification_refresh, WidgetRenderer.refreshPendingIntent(context, widgetId)),
            )
        val promoted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && wantsLiveUpdate(prefs, widgetId)
        // Dépliée : le nom de la ligne et de l'arrêt, un sens par ligne et la source en toutes lettres.
        // Une Live Update n'accepte pas le style « boîte de réception » : les sens vont alors à la ligne.
        if (content.lines.size > 1 && !promoted) {
            builder.setStyle(
                Notification.InboxStyle()
                    .setBigContentTitle(content.expandedTitle)
                    .setSummaryText(content.summary)
                    .also { style -> content.lines.forEach { style.addLine(it) } },
            )
        } else {
            builder.setStyle(
                Notification.BigTextStyle()
                    .setBigContentTitle(content.expandedTitle)
                    .setSummaryText(content.summary)
                    .bigText(content.lines.joinToString("\n")),
            )
        }
        if (promoted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            // Texte de la pastille de la barre d'état : le prochain passage.
            NotificationContent.chip(widget.model)?.let { builder.setShortCriticalText(it) }
        }
        if (!proximity) {
            builder.setLargeIcon(badgeIcon(context, config.line))
            builder.setColor(Palettes.lineColors(config.line).first)
        }
        // Toucher le bouton donne accès à la position, comme toucher le widget : rien n'est lu sans lui.
        if ((config.auto || proximity) && PositionReader(context).hasPreciseAccess) {
            builder.addAction(action(context, R.drawable.ic_stat_location, R.string.notification_locate, locateIntent(context, widgetId)))
        }
        return builder.build()
    }

    /** Sans son ni vibration : la notification change chaque minute et ne doit jamais déranger. */
    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            // Une notification « silencieuse » peut être masquée sur l'écran de verrouillage.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun action(context: Context, icon: Int, label: Int, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, icon), context.getString(label), intent).build()

    /** La pastille de la ligne, centrée dans un carré pour ne pas être déformée. */
    private fun badgeIcon(context: Context, line: Line): Icon {
        val badge = BadgeRenderer.render(context, line, ICON_DP)
        val side = max(badge.width, badge.height)
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).drawBitmap(badge, (side - badge.width) / 2f, (side - badge.height) / 2f, null)
        return Icon.createWithBitmap(square)
    }

    private fun openIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("traceolive://notification/open/$widgetId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, DeparturesWidgetProvider::class.java)
            .setAction(ACTION_DISMISSED)
            .setData(Uri.parse("traceolive://notification/dismissed/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun locateIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, AutoLocateService::class.java)
            .setData(Uri.parse("traceolive://notification/locate/$widgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AutoLocateService.EXTRA_FROM_NOTIFICATION, true)
        return PendingIntent.getForegroundService(
            context, widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
