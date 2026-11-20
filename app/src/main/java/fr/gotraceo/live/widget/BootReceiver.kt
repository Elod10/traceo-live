package fr.gotraceo.live.widget

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reprogramme l'actualisation périodique après un redémarrage, une mise à jour de l'application,
 * ou quand l'usager autorise les alarmes exactes (le tick repasse alors à la minute près).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                WidgetUpdater.renderAll(context)
                WidgetUpdater.scheduleTick(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WidgetUpdater.cancelLegacyAlarms(context)
                WidgetUpdater.renderAll(context)
                WidgetUpdater.scheduleTick(context)
            }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> WidgetUpdater.scheduleTick(context)
        }
    }
}
