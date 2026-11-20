package fr.gotraceo.live.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lit la position quand on touche un widget automatique, puis s'arrête.
 *
 * Android n'autorise la position en arrière-plan qu'à un service au premier plan démarré depuis le
 * widget : c'est ce qui évite la permission « Toujours autoriser ». Rien ne tourne entre deux touchers.
 */
class AutoLocateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = ConcurrentHashMap.newKeySet<Int>()
    private val active = AtomicInteger()
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val app = applicationContext
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (!promote()) {
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetUpdater.requestRefresh(app, widgetId)
            stopWhenIdle()
            return START_NOT_STICKY
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            stopWhenIdle()
            return START_NOT_STICKY
        }
        val now = System.currentTimeMillis()
        // Le bouton de la notification relit seulement la position : le double toucher est propre au widget.
        val fromNotification = intent?.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) == true
        if (!fromNotification) {
            if (Prefs(app).auto(widgetId).isChoiceOpen(now)) {
                AutoMode.openChoice(app, widgetId)
                stopWhenIdle()
                return START_NOT_STICKY
            }
            AutoMode.openDoubleTapWindow(app, widgetId, now)
        }
        if (!running.add(widgetId)) return START_NOT_STICKY

        AutoMode.beginLocating(widgetId)
        active.incrementAndGet()
        scope.launch {
            try {
                withTimeoutOrNull(TIMEOUT_MS) { AutoMode.onTap(app, widgetId, now) }
            } catch (t: Exception) {
                Log.w(TAG, "Échec du mode automatique", t)
            } finally {
                AutoMode.endLocating(widgetId)
                running.remove(widgetId)
            }
            try {
                redrawWhenWindowsEnd(app, widgetId)
                withContext(Dispatchers.IO) { AutoMode.refreshIndexIfStale(app) }
            } catch (t: Exception) {
                Log.w(TAG, "Échec après la mise à jour du widget", t)
            } finally {
                if (active.decrementAndGet() == 0) {
                    mainHandler.post { stopWhenIdle() }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Le toucher redevient un simple toucher, puis l'heure revient à la place du message. */
    private suspend fun redrawWhenWindowsEnd(app: Context, widgetId: Int) {
        val prefs = Prefs(app)
        WidgetUpdater.render(app, widgetId)
        while (true) {
            val state = prefs.auto(widgetId)
            val now = System.currentTimeMillis()
            val next = listOf(state.choiceUntil, state.hintUntil).filter { it > now }.minOrNull() ?: return
            delay(next - now)
            WidgetUpdater.render(app, widgetId)
        }
    }

    private fun stopWhenIdle() {
        if (active.get() == 0) stopSelfResult(lastStartId)
    }

    private fun promote(): Boolean = try {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.auto_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle(getString(R.string.auto_notification_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: RuntimeException) {
        // Permission retirée, ou démarrage refusé par le système : on actualise seulement les passages.
        Log.w(TAG, "Service de position refusé", e)
        false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Démarrage par le bouton « Me localiser » de la notification du widget. */
        const val EXTRA_FROM_NOTIFICATION = "fr.gotraceo.live.extra.FROM_NOTIFICATION"

        private const val TAG = "TRWidget"
        private const val CHANNEL_ID = "position"
        private const val NOTIFICATION_ID = 1
        private const val TIMEOUT_MS = 30_000L
    }
}
