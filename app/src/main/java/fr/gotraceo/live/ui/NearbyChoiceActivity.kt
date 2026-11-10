package fr.gotraceo.live.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.Directions
import fr.gotraceo.live.data.NearbyLine
import fr.gotraceo.live.data.PassageSnapshot
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.RankedLine
import fr.gotraceo.live.data.formatDistance
import fr.gotraceo.live.widget.AutoMode
import fr.gotraceo.live.widget.BadgeRenderer
import fr.gotraceo.live.widget.Passage
import fr.gotraceo.live.widget.WaitKind
import fr.gotraceo.live.widget.WidgetContent
import fr.gotraceo.live.widget.Sources
import fr.gotraceo.live.widget.WidgetProviders
import fr.gotraceo.live.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Feuille ouverte par le double toucher d'un widget automatique : les lignes proches, à choisir. */
class NearbyChoiceActivity : Activity() {

    private val scope = MainScope()
    private lateinit var prefs: Prefs
    private lateinit var adapter: NearbyAdapter
    private lateinit var status: TextView
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var chosen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContentView(R.layout.activity_nearby_choice)
        val height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT).toInt()
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, height)
        window.setGravity(Gravity.BOTTOM)

        prefs = Prefs(this)
        status = findViewById(R.id.nearby_status)
        findViewById<TextView>(R.id.data_mention).movementMethod = LinkMovementMethod.getInstance()
        // Le format À proximité affiche plusieurs lignes : aucune n'est « la » ligne affichée.
        val shownLineId = prefs.config(widgetId)?.line?.id.takeUnless { WidgetProviders.isProximity(this, widgetId) }
        adapter = NearbyAdapter(this, prefs.favoriteLines, shownLineId) { row ->
            val favorite = prefs.toggleFavoriteLine(row.nearby.line.id)
            adapter.setFavorite(row.nearby.line.id, favorite)
        }
        val list = findViewById<ListView>(R.id.nearby_list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> choose(adapter.getItem(position).ranked.nearby) }

        // Le choix prend la main sur le premier toucher, et le prochain toucher relira la position.
        AutoMode.markChoosing(widgetId)
        AutoMode.closeChoice(this, widgetId)
        load()
    }

    override fun onDestroy() {
        scope.cancel()
        // Sans choix, l'arrêt ne change pas : seuls les passages sont actualisés.
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !chosen) WidgetUpdater.requestRefresh(this, widgetId)
        super.onDestroy()
    }

    private fun load() {
        scope.launch {
            // Le premier toucher lit encore la position : la liste doit partir de la bonne.
            if (AutoMode.isLocating(widgetId)) {
                showStatus(getString(R.string.auto_loading_position))
                while (AutoMode.isLocating(widgetId)) delay(LOCATING_POLL_MS)
            }
            val point = prefs.auto(widgetId).last
            if (point == null) {
                showStatus(getString(R.string.nearby_empty))
                return@launch
            }
            val ranked = withContext(Dispatchers.IO) { AutoMode.ranking(this@NearbyChoiceActivity, point) }?.list.orEmpty()
            if (ranked.isEmpty()) {
                showStatus(getString(R.string.nearby_empty))
                return@launch
            }
            status.visibility = View.GONE
            adapter.setRows(ranked.map { Row(it) })
            loadPassages(ranked.take(PASSAGES_SHOWN))
        }
    }

    /** Chaque ligne demande une requête : seules les premières sont interrogées. */
    private fun loadPassages(ranked: List<RankedLine>) {
        val source = Sources.passages(this@NearbyChoiceActivity)
        val hasKey = prefs.hasApiKey
        ranked.forEach { line ->
            scope.launch {
                val snapshot = async(Dispatchers.IO) { AutoMode.fetch(source, line.nearby) }.await().getOrNull()
                adapter.setPassage(line.nearby.line.id, snapshot?.let { nextPassage(it, line.nearby, hasKey) }, loaded = snapshot != null)
            }
        }
    }

    private fun nextPassage(snapshot: PassageSnapshot, nearby: NearbyLine, hasKey: Boolean): Passage? {
        val now = System.currentTimeMillis()
        val departures = if (hasKey) {
            snapshot.departures
        } else {
            Directions.withoutTerminusArrivals(snapshot.departures, nearby.stopName)
        }
        val first = Directions.upcoming(departures, now).firstOrNull { !it.isCancelled } ?: return null
        return WidgetContent.passage(first, now, nearby.line.mode)
    }

    private fun choose(nearby: NearbyLine) {
        // Un second toucher pendant que le premier choix s'enregistre ne le double pas.
        if (chosen) return
        chosen = true
        scope.launch {
            // Le choix va au bout même si la feuille est fermée entre-temps : sinon le lieu serait
            // retenu sans que le widget change, et sans l'actualisation faite quand on ne choisit rien.
            withContext(NonCancellable) { AutoMode.choose(applicationContext, widgetId, nearby) }
            finish()
        }
    }

    private fun showStatus(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
    }

    private class Row(val ranked: RankedLine) {
        val nearby: NearbyLine get() = ranked.nearby
        var loaded = false
        var passage: Passage? = null
    }

    private class NearbyAdapter(
        private val context: Context,
        favorites: Set<String>,
        private val shownLineId: String?,
        private val onStar: (Row) -> Unit,
    ) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)
        private var rows: List<Row> = emptyList()
        private val favorites = favorites.toMutableSet()

        fun setRows(items: List<Row>) {
            rows = items
            notifyDataSetChanged()
        }

        fun setFavorite(lineId: String, favorite: Boolean) {
            if (favorite) favorites.add(lineId) else favorites.remove(lineId)
            notifyDataSetChanged()
        }

        fun setPassage(lineId: String, passage: Passage?, loaded: Boolean) {
            rows.firstOrNull { it.nearby.line.id == lineId }?.let {
                it.passage = passage
                it.loaded = loaded
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Row = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.item_nearby, parent, false)
            val row = getItem(position)
            val line = row.nearby.line
            view.findViewById<ImageView>(R.id.nearby_badge).setImageBitmap(BadgeRenderer.render(context, line, BADGE_DP))
            view.findViewById<TextView>(R.id.nearby_stop).text = row.nearby.stopName
            view.findViewById<TextView>(R.id.nearby_detail).text = detail(row, line.id == shownLineId)
            val wait = view.findViewById<TextView>(R.id.nearby_wait)
            val inMinutes = row.passage?.kind == WaitKind.MINUTES
            wait.text = waitLabel(row)
            wait.textSize = if (inMinutes) WAIT_TEXT_SP else WAIT_LABEL_SP
            view.findViewById<View>(R.id.nearby_unit).visibility = if (inMinutes) View.VISIBLE else View.GONE

            val favorite = line.id in favorites
            val star = view.findViewById<ImageButton>(R.id.nearby_star)
            star.setImageResource(if (favorite) R.drawable.ic_star else R.drawable.ic_star_border)
            star.contentDescription = context.getString(
                if (favorite) R.string.nearby_star_remove else R.string.nearby_star_add,
                UiText.lineTitle(line),
            )
            star.setOnClickListener { onStar(row) }
            return view
        }

        private fun detail(row: Row, shown: Boolean): String {
            val parts = ArrayList<String>()
            if (shown) parts += context.getString(R.string.nearby_shown)
            parts += formatDistance(row.nearby.distanceM)
            row.passage?.let { parts += context.getString(R.string.nearby_towards, WidgetContent.shortName(it.destination)) }
            return parts.joinToString(" · ")
        }

        private fun waitLabel(row: Row): String {
            val passage = row.passage ?: return if (row.loaded) "—" else ""
            return when (passage.kind) {
                WaitKind.MINUTES -> passage.minutes.toString()
                WaitKind.CLOCK -> passage.clock
                WaitKind.IMMINENT -> "<1 min"
                WaitKind.AT_STOP -> passage.atStopLabel
                WaitKind.CANCELLED -> "Supprimé"
            }
        }
    }

    private companion object {
        const val SHEET_HEIGHT = 0.7f
        const val PASSAGES_SHOWN = 8
        const val LOCATING_POLL_MS = 100L
        const val BADGE_DP = 32f
        const val WAIT_TEXT_SP = 30f
        const val WAIT_LABEL_SP = 18f
    }
}
