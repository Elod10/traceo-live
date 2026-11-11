package fr.gotraceo.live.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.PassageRoute
import fr.gotraceo.live.data.PositionReader
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.ProximityState
import fr.gotraceo.live.data.WidgetState
import fr.gotraceo.live.widget.DepartureFormat
import fr.gotraceo.live.widget.WidgetFormat
import fr.gotraceo.live.widget.WidgetProviders
import fr.gotraceo.live.widget.WidgetRenderer
import fr.gotraceo.live.widget.WidgetUpdater
import kotlin.math.min
import kotlin.math.roundToInt

/** Accueil : les widgets de l'écran d'accueil, tels qu'ils s'y affichent. */
class MainActivity : Activity() {

    private companion object {
        const val REBUILD_DELAY_MS = 200L
        const val TICK_MS = 30_000L

        /** Au-delà de cette part de la largeur, le widget laisse ses textes en dessous de lui. */
        const val SIDE_BY_SIDE_BELOW = 0.62f
    }

    private lateinit var prefs: Prefs
    private lateinit var widgetsContainer: LinearLayout
    private lateinit var widgetsEmpty: View
    private lateinit var widgetsTitle: View
    private lateinit var exactAlarmCard: View

    private val handler = Handler(Looper.getMainLooper())
    private val rebuild = Runnable { refreshWidgetList() }

    /** Tant que l'écran reste ouvert, les temps d'attente affichés doivent suivre l'heure. */
    private val tick = object : Runnable {
        override fun run() {
            refreshWidgetList()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val widgetsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.removeCallbacks(rebuild)
        handler.postDelayed(rebuild, REBUILD_DELAY_MS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        widgetsContainer = findViewById(R.id.widgets_container)
        widgetsEmpty = findViewById(R.id.widgets_empty)
        widgetsTitle = findViewById(R.id.widgets_title)
        findViewById<TextView>(R.id.data_mention).movementMethod = LinkMovementMethod.getInstance()

        findViewById<View>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.add_widget).setOnClickListener {
            startActivity(Intent(this, AddWidgetActivity::class.java))
        }
        findViewById<View>(R.id.open_support).setOnClickListener { Support.open(this) }
        exactAlarmCard = findViewById(R.id.exact_alarm_card)
        findViewById<View>(R.id.exact_alarm_allow).setOnClickListener { ExactAlarmAccess.openSettings(this) }
        SystemBars.pad(this)
    }

    override fun onResume() {
        super.onResume()
        prefs.registerWidgetsListener(widgetsListener)
        refreshWidgetList()
        handler.postDelayed(tick, TICK_MS)
        // Ouvrir l'application remet les widgets à jour : ceux qui sont dus sont actualisés, et le tick
        // repart s'il s'était arrêté.
        WidgetUpdater.requestTick(this)
    }

    override fun onPause() {
        prefs.unregisterWidgetsListener(widgetsListener)
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun refreshWidgetList() {
        val ids = WidgetUpdater.widgetIds(this)
        widgetsContainer.removeAllViews()
        widgetsEmpty.visibility = if (ids.isEmpty()) View.VISIBLE else View.GONE
        widgetsTitle.visibility = if (ids.isEmpty()) View.GONE else View.VISIBLE
        val needsExactAlarms = ids.isNotEmpty() && WidgetUpdater.needsExactAlarmAccess(this)
        exactAlarmCard.visibility = if (needsExactAlarms) View.VISIBLE else View.GONE
        val now = System.currentTimeMillis()
        ids.forEach { widgetsContainer.addView(widgetView(it, now)) }
    }

    private fun widgetView(id: Int, now: Long): View {
        val view = layoutInflater.inflate(R.layout.item_widget, widgetsContainer, false)
        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(id)?.provider?.className
        val entry = WidgetProviders.entryOf(provider)
        val format = entry?.format
        val proximity = format == WidgetFormat.PROXIMITE
        val config = prefs.config(id)
        val state = prefs.state(id)
        val nearby = prefs.proximity(id)
        val hasKey = prefs.hasApiKey

        val (widthDp, heightDp) = WidgetRenderer.widgetSize(this, manager.getAppWidgetOptions(id))
        val preview = WidgetRenderer.preview(
            this, config, state, hasKey, widthDp, heightDp, now, format,
            auto = prefs.auto(id),
            proximity = nearby,
        )
        val density = resources.displayMetrics.density
        val available = (resources.displayMetrics.widthPixels - 2 * resources.getDimension(R.dimen.screen_padding))
        val scale = min(1f, available / (widthDp * density))
        val shownWidth = (widthDp * density * scale).roundToInt()
        val shownHeight = (heightDp * density * scale).roundToInt()

        val image = view.findViewById<ImageView>(R.id.item_widget_image)
        image.layoutParams = LinearLayout.LayoutParams(shownWidth, shownHeight)
        image.setImageBitmap(preview.bitmap)
        image.contentDescription = preview.description

        if (shownWidth < available * SIDE_BY_SIDE_BELOW) layOutSideBySide(view)

        view.findViewById<TextView>(R.id.item_widget_title).text =
            entry?.let { getString(it.nameRes) } ?: getString(R.string.widget_name)

        val reconfigure = config != null && !proximity &&
            DataSources.route(config, hasKey) == PassageRoute.RECONFIGURE
        val needsAccess = config?.auto == true && !PositionReader(this).hasPreciseAccess
        val subtitle = view.findViewById<TextView>(R.id.item_widget_subtitle)
        val summary = if (proximity) nearby?.let(::summaryOf) else state
        subtitle.text = when {
            config == null -> getString(R.string.widget_short_not_configured)
            reconfigure -> getString(R.string.widget_short_reconfigure)
            else -> statusLine(summary)
        }
        if (config != null && !reconfigure && summary?.error != null) {
            subtitle.setTextColor(getColor(R.color.error_text))
        }

        val openConfiguration = {
            startActivity(
                Intent(this, ConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            )
        }
        val actions = view.findViewById<View>(R.id.item_widget_actions)
        val primary = view.findViewById<Button>(R.id.item_widget_primary)
        if (config == null || reconfigure || needsAccess) {
            actions.visibility = View.GONE
            primary.visibility = View.VISIBLE
            primary.setText(if (reconfigure) R.string.main_widget_reconfigure else R.string.main_widget_setup)
            primary.setOnClickListener { openConfiguration() }
        } else {
            bindNotifyButton(view.findViewById(R.id.item_widget_notify), id)
            view.findViewById<View>(R.id.item_widget_refresh).setOnClickListener { WidgetUpdater.requestRefresh(this, id) }
            val configure = view.findViewById<View>(R.id.item_widget_configure)
            // Le format À proximité n'a rien à régler : il suit la position et le thème du système.
            configure.visibility = if (proximity) View.GONE else View.VISIBLE
            configure.setOnClickListener { openConfiguration() }
        }
        return view
    }

    /**
     * La cloche ouvre les réglages du widget pour l'écran de verrouillage. Pleine, elle signale une
     * notification ou un suivi de la position actifs. C'est le seul accès pour le format À proximité.
     */
    private fun bindNotifyButton(button: ImageButton, id: Int) {
        val active = LockScreenActivity.isActive(this, prefs, id)
        button.setImageResource(if (active) R.drawable.ic_notification_on else R.drawable.ic_notification_off)
        val label = getString(R.string.main_widget_lock) + " (" + LockScreenActivity.summary(this, prefs, id) + ")"
        button.contentDescription = label
        button.tooltipText = label
        button.setOnClickListener { startActivity(LockScreenActivity.intent(this, id)) }
    }

    /** Un petit widget garde ses textes et ses boutons à côté de lui. */
    private fun layOutSideBySide(view: View) {
        (view.findViewById<LinearLayout>(R.id.item_widget_root)).orientation = LinearLayout.HORIZONTAL
        val column = view.findViewById<LinearLayout>(R.id.item_widget_column)
        column.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = (14 * resources.displayMetrics.density).roundToInt()
        }
        val meta = view.findViewById<LinearLayout>(R.id.item_widget_meta)
        meta.orientation = LinearLayout.VERTICAL
        meta.gravity = android.view.Gravity.START
        val texts = view.findViewById<View>(R.id.item_widget_texts)
        texts.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    /** Le résumé d'un widget À proximité, qui porte les mêmes informations de relevé qu'un autre. */
    private fun summaryOf(state: ProximityState): WidgetState =
        WidgetState(state.fetchedAt, state.attemptedAt, emptyList(), state.error, state.dataAt, state.fresh)

    /** Heure du relevé ou, en direct, de la dernière mise à jour. */
    private fun statusLine(state: WidgetState?): String {
        state ?: return getString(R.string.main_widget_no_data)
        state.error?.let { return it }
        val at = if (state.isSnapshot) state.dataAt else state.fetchedAt
        val time = at?.takeIf { it > 0 }?.let { DepartureFormat.clock(it, withSeconds = true) }
            ?: return getString(R.string.main_widget_no_data)
        val label = when {
            !state.isSnapshot -> R.string.main_status_updated
            state.fresh == false -> R.string.main_status_snapshot_old
            else -> R.string.main_status_snapshot
        }
        return getString(label, time)
    }
}
