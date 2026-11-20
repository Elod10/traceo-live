package fr.gotraceo.live.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import fr.gotraceo.live.R
import fr.gotraceo.live.data.AnchorPolicy
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.DirectionOption
import fr.gotraceo.live.data.Directions
import fr.gotraceo.live.data.GeoPoint
import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.NearbyLookup
import fr.gotraceo.live.data.Network
import fr.gotraceo.live.data.NetworkCatalog
import fr.gotraceo.live.data.NetworkChoices
import fr.gotraceo.live.data.NetworkStatus
import fr.gotraceo.live.data.Networks
import fr.gotraceo.live.data.PassageRoute
import fr.gotraceo.live.data.PassageSnapshot
import fr.gotraceo.live.data.PositionFix
import fr.gotraceo.live.data.PositionReader
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.data.RankedLine
import fr.gotraceo.live.data.ReferentialRepository
import fr.gotraceo.live.data.RefreshSchedule
import fr.gotraceo.live.data.SameNameStops
import fr.gotraceo.live.data.Stop
import fr.gotraceo.live.data.StopIndexStore
import fr.gotraceo.live.data.TransportMode
import fr.gotraceo.live.data.WidgetConfig
import fr.gotraceo.live.data.WidgetState
import fr.gotraceo.live.data.WidgetStates
import fr.gotraceo.live.data.WidgetTheme
import fr.gotraceo.live.data.formatDistance
import fr.gotraceo.live.data.normalizeLabel
import fr.gotraceo.live.widget.AutoMode
import fr.gotraceo.live.widget.BadgeRenderer
import fr.gotraceo.live.widget.Palettes
import fr.gotraceo.live.widget.WidgetFormat
import fr.gotraceo.live.widget.Sources
import fr.gotraceo.live.widget.WidgetProviders
import fr.gotraceo.live.widget.WidgetRenderer
import fr.gotraceo.live.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Assistant de configuration d'un widget :
 * réseau → mode de transport → ligne → arrêt → sens et couleur, ou le mode automatique, qui suit la
 * position : ligne proche → couleur. L'étape du réseau n'existe que s'il y en a un autre que
 * l'Île-de-France à proposer.
 */
class ConfigureActivity : Activity() {

    companion object {
        const val EXTRA_FROM_WIDGET = "fr.gotraceo.live.extra.FROM_WIDGET"

        /** Ouverture par le système, juste après l'ajout du widget depuis l'application. */
        const val EXTRA_FROM_PIN = "fr.gotraceo.live.extra.FROM_PIN"

        private const val REQUEST_LOCATION = 1
        private const val REQUEST_NETWORK_LOCATION = 2
        private const val AUTO_ROWS_SHOWN = 30

        /** À la configuration, le catalogue est redemandé s'il a plus que cet âge : la liste doit être à jour. */
        private const val CATALOG_MAX_AGE_MS = 10L * 60 * 1000

        /** Une grille n'a de sens que pour quelques lignes au nom très court, comme le métro. */
        private const val GRID_MAX_LINES = 40
        private const val GRID_MAX_LABEL = 3
        private const val PREVIEW_MAX_HEIGHT_DP = 240f

        /** Niveau d'un ClipDrawable qui en montre la moitié (10 000 = tout). */
        private const val CLIP_HALF = 5000

        private val openScreens = HashMap<Int, WeakReference<ConfigureActivity>>()

        /**
         * Vrai si la configuration de ce widget est déjà à l'écran. Un écran quitté sans enregistrer
         * (retour à l'accueil) existe encore mais ne se voit plus : il ne doit pas empêcher d'en ouvrir un autre.
         */
        fun isOpen(widgetId: Int): Boolean = openScreens[widgetId]?.get()?.let { !it.isFinishing && it.started } == true
    }

    private enum class Step { NETWORK, MODE, LINE, STOP, DIRECTION, AUTO, AUTO_COLOR }

    /** Marqueur de l'option « Tous les sens ». */
    private object AllDirections

    /** Marqueur de la ligne « Automatique » de la liste des modes. */
    private object AutoRow

    /** Marqueur de la ligne qui mène à la clé API. */
    private object KeyRow

    /** Marqueur de la ligne « Trouver mon réseau ». */
    private object FindNetworkRow

    private val scope = MainScope()
    private var loadJob: Job? = null

    private lateinit var prefs: Prefs
    private lateinit var repository: ReferentialRepository
    private lateinit var adapter: RowAdapter
    private lateinit var gridAdapter: RowAdapter

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var existing: WidgetConfig? = null
    private var step = Step.MODE

    /** Les réseaux connus du téléphone, redemandés à Tracéo pendant l'étape du réseau. */
    private var catalog = NetworkCatalog()

    /** Le réseau du widget en cours de configuration. */
    private var network: Network = Networks.ILE_DE_FRANCE
    private var catalogJob: Job? = null
    private var locateJob: Job? = null

    /** Vrai tant que le catalogue est redemandé à Tracéo. */
    private var catalogLoading = false

    /** Vrai si Tracéo n'a pas donné le catalogue : la liste est peut-être incomplète. */
    private var catalogFailed = false

    /** Dernière position approximative lue à l'étape du réseau, et le réseau qui la dessert. */
    private var networkPoint: GeoPoint? = null
    private var nearestNetwork: String? = null

    /** Vrai dès que l'usager a touché un réseau : la position ne déplace plus son choix. */
    private var networkTouched = false

    /** Message de l'étape du réseau, à la place de son aide. */
    private var networkNotice: Int? = null

    /** Vrai si la liste des réseaux est assez longue pour qu'on y cherche. */
    private var networkSearch = false
    private var mode: TransportMode? = null
    private var line: Line? = null
    private var stop: Stop? = null
    private var viaShortcut = false
    private var directionOptions: List<DirectionOption> = emptyList()
    private var directionResults: List<Result<PassageSnapshot>> = emptyList()
    private var previewState: WidgetState? = null
    private var selectedTheme = WidgetTheme.AUTO
    private var autoFix: PositionFix? = null
    private var autoFirst: RankedLine? = null
    private var autoItems: List<RowItem> = emptyList()

    /** Réseau des lignes proches, déduit de la position : c'est le sien que cite la mention. */
    private var autoNetwork: String? = null

    /** Vrai une fois « Alarmes et rappels » ouvert après l'enregistrement : le retour ferme l'écran. */
    private var awaitingAlarmAccess = false

    /** Vrai entre onStart et onStop : l'écran est visible, ou sur le point de l'être. */
    private var started = false

    /** Format choisi dans la liste des widgets du lanceur (null pour l'entrée automatique). */
    private val chosenFormat: WidgetFormat? by lazy {
        WidgetProviders.formatOf(AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className)
    }

    /** Le format À proximité suit toujours la position : seule la permission reste à demander. */
    private val isProximity: Boolean get() = chosenFormat == WidgetFormat.PROXIMITE

    /** Vrai si le format choisi n'affiche qu'un sens : un seul peut alors être coché. */
    private val oneDirectionOnly: Boolean get() = chosenFormat?.followsOneDirection == true

    private lateinit var stepBar: View
    private lateinit var stepCount: TextView
    private lateinit var stepSegments: List<View>
    private lateinit var overline: View
    private lateinit var stepTitle: TextView
    private lateinit var crumb: View
    private lateinit var crumbBadge: ImageView
    private lateinit var crumbText: TextView
    private lateinit var stepHelp: TextView
    private lateinit var searchBox: View
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var grid: GridView
    private lateinit var emptyText: View
    private lateinit var statusBox: View
    private lateinit var progress: ProgressBar
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var statusSettings: Button
    private lateinit var retry: Button
    private lateinit var previewBox: View
    private lateinit var previewImage: ImageView
    private lateinit var footerHelp: TextView
    private lateinit var bottomBar: View
    private lateinit var colorSection: View
    private lateinit var swatches: LinearLayout
    private lateinit var notifySection: View
    private lateinit var notifyIcon: ImageView
    private lateinit var notifySummary: TextView
    private lateinit var save: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Résultat par défaut : annulation (le lanceur retire alors le widget).
        setResult(RESULT_CANCELED, resultIntent())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Après un ajout depuis l'application, la galerie prend le relais si l'identifiant manque.
            if (intent?.getBooleanExtra(EXTRA_FROM_PIN, false) != true) {
                Toast.makeText(this, R.string.invalid_widget, Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }
        // Certains lanceurs ouvrent eux aussi la configuration après un ajout depuis l'application.
        if (isOpen(widgetId)) {
            setResult(RESULT_OK, resultIntent())
            finish()
            return
        }
        // Une configuration d'un autre widget, quittée sans enregistrer, est abandonnée : elle ne doit
        // pas réapparaître à la place de celle-ci, ni derrière elle une fois celle-ci enregistrée.
        openScreens.values.mapNotNull { it.get() }.filter { !it.isFinishing }.forEach { it.finish() }
        openScreens[widgetId] = WeakReference(this)

        setContentView(R.layout.activity_configure)
        SystemBars.pad(this)

        prefs = Prefs(this)
        repository = ReferentialRepository(this)
        existing = prefs.config(widgetId)
        selectedTheme = ThemeChoices.initial(existing?.theme, chosenFormat)
        catalog = Sources.catalog(this).read()
        // Le réseau du widget à modifier, sinon le dernier choisi : l'étape se franchit alors d'un toucher.
        network = existing?.let { cfg -> catalog.byId(cfg.networkId) ?: Networks.placeholder(cfg.networkId, cfg.line.mode) }
            ?: prefs.lastNetwork?.let(catalog::byId)?.takeIf { it.status != NetworkStatus.OFF }
            ?: Networks.ILE_DE_FRANCE

        bindViews()
        adapter = RowAdapter(this)
        gridAdapter = RowAdapter(this, cells = true)
        list.adapter = adapter
        grid.adapter = gridAdapter
        // La position tient compte de l'aperçu placé en tête de liste.
        list.setOnItemClickListener { parent, _, position, _ ->
            (parent.getItemAtPosition(position) as? RowItem)?.let(::onItemClicked)
        }
        grid.setOnItemClickListener { _, _, position, _ -> onItemClicked(gridAdapter.getItem(position)) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applySearch(s?.toString().orEmpty())
        })
        // Notification, suivi de la position et plage horaire s'enregistrent sur leur propre écran.
        notifySection.setOnClickListener { startActivity(LockScreenActivity.intent(this, widgetId)) }
        retry.setOnClickListener { goTo(step, forceRefresh = true) }
        statusSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        save.setOnClickListener {
            when (step) {
                Step.NETWORK -> confirmNetwork()
                Step.AUTO -> goTo(Step.AUTO_COLOR)
                Step.AUTO_COLOR -> saveAutoConfig()
                else -> saveConfig()
            }
        }
        findViewById<View>(R.id.back).setOnClickListener { navigateBack() }

        goTo(
            when {
                isProximity -> Step.AUTO
                // Un widget à modifier garde son réseau : on y revient par le bouton retour.
                existing != null || !hasNetworkStep -> Step.MODE
                else -> Step.NETWORK
            },
        )
        // Le format À proximité déduit son réseau de la position : il relit le catalogue lui-même.
        if (!isProximity) refreshCatalog()
    }

    /**
     * L'étape du réseau n'a lieu d'être que s'il y a un choix à faire : un autre réseau connu, ou un
     * catalogue encore jamais reçu, qui peut en apporter. Sans cela, la configuration garde ses
     * quatre étapes.
     */
    private val hasNetworkStep: Boolean get() = catalog.others.isNotEmpty() || catalog.savedAt == 0L

    /**
     * Le seul mode du réseau, quand il n'en propose qu'un (Troyes : le bus). L'étape du mode n'a alors
     * rien à faire choisir : la configuration passe directement aux lignes, qui reprennent en tête
     * l'option automatique et « Garder la ligne et l'arrêt actuels ».
     */
    private val onlyMode: TransportMode? get() = DataSources.onlyMode(network, prefs.hasApiKey)

    private fun bindViews() {
        stepBar = findViewById(R.id.step_bar)
        stepCount = findViewById(R.id.step_count)
        stepSegments = listOf(
            findViewById(R.id.step_segment_1),
            findViewById(R.id.step_segment_2),
            findViewById(R.id.step_segment_3),
            findViewById(R.id.step_segment_4),
            findViewById(R.id.step_segment_5),
        )
        overline = findViewById(R.id.overline)
        stepTitle = findViewById(R.id.step_title)
        crumb = findViewById(R.id.crumb)
        crumbBadge = findViewById(R.id.crumb_badge)
        crumbText = findViewById(R.id.crumb_text)
        stepHelp = findViewById(R.id.step_help)
        searchBox = findViewById(R.id.search_box)
        search = findViewById(R.id.search)
        list = findViewById(R.id.list)
        grid = findViewById(R.id.grid)
        emptyText = findViewById(R.id.empty_text)
        statusBox = findViewById(R.id.status_box)
        progress = findViewById(R.id.progress)
        statusIcon = findViewById(R.id.status_icon)
        statusText = findViewById(R.id.status_text)
        statusSettings = findViewById(R.id.status_settings)
        retry = findViewById(R.id.retry)
        bottomBar = findViewById(R.id.bottom_bar)
        colorSection = findViewById(R.id.color_section)
        swatches = findViewById(R.id.swatches)
        notifySection = findViewById(R.id.notify_section)
        notifyIcon = findViewById(R.id.notify_icon)
        notifySummary = findViewById(R.id.notify_summary)
        save = findViewById(R.id.save)
        findViewById<TextView>(R.id.data_mention).movementMethod = LinkMovementMethod.getInstance()

        val header =layoutInflater.inflate(R.layout.list_preview_header, list, false)
        previewBox = header.findViewById(R.id.preview_box)
        previewImage = header.findViewById(R.id.preview_image)
        list.addHeaderView(header, null, false)
        val footer = layoutInflater.inflate(R.layout.list_help_footer, list, false)
        footerHelp = footer.findViewById(R.id.footer_help)
        list.addFooterView(footer, null, false)
    }

    override fun onStart() {
        super.onStart()
        started = true
    }

    override fun onStop() {
        started = false
        super.onStop()
    }

    override fun onRestart() {
        super.onRestart()
        // Une clé ajoutée entre-temps propose d'autres modes.
        if (step == Step.MODE) showModes()
    }

    override fun onResume() {
        super.onResume()
        // Retour de « Alarmes et rappels » : le widget est déjà enregistré, accordée ou non.
        if (awaitingAlarmAccess) {
            finish()
            return
        }
        // Au retour de l'écran de verrouillage, son résumé change.
        val active = LockScreenActivity.isActive(this, prefs, widgetId)
        notifyIcon.setImageResource(if (active) R.drawable.ic_notification_on else R.drawable.ic_notification_off)
        notifySummary.text = LockScreenActivity.summary(this, prefs, widgetId)
    }

    override fun onDestroy() {
        scope.cancel()
        if (openScreens[widgetId]?.get() === this) openScreens.remove(widgetId)
        super.onDestroy()
    }

    private fun resultIntent(): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    // --- Navigation ------------------------------------------------------------------------

    private fun goTo(target: Step, forceRefresh: Boolean = false) {
        // Un réseau d'un seul mode n'a pas d'étape du mode : ses lignes en tiennent lieu.
        val only = onlyMode
        if (target == Step.MODE && only != null) {
            mode = only
            return goTo(Step.LINE, forceRefresh)
        }
        loadJob?.cancel()
        step = target
        updateBackCallback()
        search.setText("")
        adapter.setItems(emptyList())
        gridAdapter.setItems(emptyList())
        updateChrome()
        when (target) {
            Step.NETWORK -> showNetworks()
            Step.MODE -> showModes()
            Step.LINE -> loadLines(forceRefresh)
            Step.STOP -> loadStops(forceRefresh)
            Step.DIRECTION -> loadDirections()
            Step.AUTO -> if (autoItems.isEmpty() || forceRefresh) startAuto() else showAutoRows()
            Step.AUTO_COLOR -> showAutoColor()
        }
    }

    /** Retour à l'étape précédente, ou fermeture depuis la première. */
    private fun navigateBack() {
        when (step) {
            Step.DIRECTION -> goTo(if (viaShortcut) Step.MODE else Step.STOP)
            Step.STOP -> goTo(Step.LINE)
            Step.LINE -> if (onlyMode == null) goTo(Step.MODE) else backFromFirstChoice()
            Step.AUTO_COLOR -> goTo(Step.AUTO)
            // Sans étape du mode, goTo(MODE) ramène aux lignes, où se trouve l'option automatique.
            Step.AUTO -> if (isProximity) finish() else goTo(Step.MODE)
            Step.MODE -> backFromFirstChoice()
            Step.NETWORK -> finish()
        }
    }

    /** Retour depuis le premier choix après le réseau (mode, ou lignes d'un réseau d'un seul mode). */
    private fun backFromFirstChoice() {
        if (hasNetworkStep) goTo(Step.NETWORK) else finish()
    }

    /** Vrai si le retour change d'étape au lieu de fermer l'écran. */
    private val backStaysInside: Boolean
        get() = when (step) {
            Step.NETWORK -> false
            Step.MODE -> hasNetworkStep
            Step.LINE -> onlyMode == null || hasNetworkStep
            Step.AUTO -> !isProximity
            else -> true
        }

    /**
     * Android 13 et plus : le geste retour passe par OnBackInvokedCallback (onBackPressed n'est
     * plus appelé pour une appli qui cible Android 16). Le rappel n'est inscrit que lorsqu'il y a
     * une étape où revenir, pour laisser sinon au système l'animation de retour prédictif.
     */
    private var backCallback: Any? = null

    private fun updateBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val registered = backCallback as OnBackInvokedCallback?
        if (backStaysInside && registered == null) {
            val callback = OnBackInvokedCallback { navigateBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        } else if (!backStaysInside && registered != null) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(registered)
            backCallback = null
        }
    }

    /** Jusqu'à Android 12. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = navigateBack()

    /**
     * Rang de l'étape et nombre d'étapes (0 hors du parcours, en automatique). L'étape du réseau en
     * ajoute une ; un réseau d'un seul mode en retire une, celle du mode : le compte suit le réseau coché.
     */
    private fun stepPosition(): Pair<Int, Int> {
        val withNetwork = if (hasNetworkStep) 1 else 0
        val shift = withNetwork - (if (onlyMode != null) 1 else 0)
        val index = when (step) {
            Step.NETWORK -> 1
            Step.MODE -> 1 + withNetwork
            Step.LINE -> 2 + shift
            Step.STOP -> 3 + shift
            Step.DIRECTION -> 4 + shift
            Step.AUTO, Step.AUTO_COLOR -> 0
        }
        return index to 4 + shift
    }

    /** Barre des étapes et titre de la fenêtre, redessinés aussi quand le réseau coché change. */
    private fun updateStepCounter() {
        val (index, total) = stepPosition()
        val label = stepTitle.text.toString()
        title = if (index == 0) label else getString(R.string.step_counter, index, total, label)
        val inSteps = index > 0
        stepBar.visibility = if (inSteps) View.VISIBLE else View.GONE
        stepCount.visibility = if (inSteps) View.VISIBLE else View.GONE
        stepCount.text = getString(R.string.step_count, index, total)
        stepSegments.forEachIndexed { i, segment ->
            segment.visibility = if (i < total) View.VISIBLE else View.GONE
            segment.setBackgroundResource(if (i < index) R.drawable.bg_step_on else R.drawable.bg_step_off)
        }
    }

    private fun updateChrome() {
        stepTitle.text = getString(
            when (step) {
                Step.NETWORK -> R.string.step_network
                Step.MODE -> R.string.step_mode
                Step.LINE -> R.string.step_line
                Step.STOP -> R.string.step_stop
                Step.DIRECTION, Step.AUTO_COLOR -> R.string.step_direction
                Step.AUTO -> if (isProximity) R.string.widget_proximite_name else R.string.step_auto
            },
        )
        updateStepCounter()
        val inSteps = stepPosition().first > 0
        overline.visibility = if (!inSteps && !isProximity) View.VISIBLE else View.GONE

        // Le fil d'Ariane rappelle les choix faits ; le réseau n'y figure que s'il a été choisi.
        val place = network.city.takeIf { hasNetworkStep }
        when (step) {
            Step.MODE -> if (place != null) showCrumb(null, place) else crumb.visibility = View.GONE
            Step.LINE -> showCrumb(null, listOfNotNull(place, mode?.labelIn(network)).joinToString(" · "))
            Step.STOP -> showCrumb(line, line?.let(UiText::lineTitle).orEmpty())
            else -> crumb.visibility = View.GONE
        }

        // À l'étape du réseau, la recherche n'apparaît que si la liste est longue : renderNetworks en décide.
        searchBox.visibility = if (step == Step.LINE || step == Step.STOP) View.VISIBLE else View.GONE
        search.hint = getString(
            when {
                step == Step.NETWORK -> R.string.search_network_hint
                step == Step.LINE -> R.string.search_line_hint
                network.isIdfm && prefs.hasApiKey -> R.string.search_stop_hint
                // Les arrêts de Tracéo n'ont pas de commune, ou pas toujours hors Île-de-France.
                else -> R.string.search_stop_hint_no_town
            },
        )
        stepHelp.visibility = View.GONE
        previewBox.visibility = View.GONE
        footerHelp.visibility = View.GONE
        emptyText.visibility = View.GONE

        val choosesFromList = step == Step.AUTO || step == Step.NETWORK
        bottomBar.visibility = if (step == Step.DIRECTION || step == Step.AUTO_COLOR || step == Step.NETWORK) View.VISIBLE else View.GONE
        colorSection.visibility = if (choosesFromList) View.GONE else View.VISIBLE
        notifySection.visibility = colorSection.visibility
        save.setText(if (choosesFromList) R.string.continue_label else R.string.save_widget)
        bindMention()
        if (step == Step.DIRECTION || step == Step.AUTO_COLOR) renderSwatches()
        hideKeyboard()
    }

    /** La mention est celle du réseau dont l'étape montre les données : en automatique, celui de la position. */
    private fun bindMention() {
        val automatic = step == Step.AUTO || step == Step.AUTO_COLOR
        Mentions.bind(findViewById(R.id.data_mention), listOf(autoNetwork.takeIf { automatic } ?: network.id))
    }

    private fun showCrumb(badge: Line?, text: String) {
        crumb.visibility = View.VISIBLE
        crumbText.text = text
        crumbBadge.visibility = if (badge != null) View.VISIBLE else View.GONE
        badge?.let { crumbBadge.setImageBitmap(BadgeRenderer.render(this, it, 22f)) }
    }

    /** @param keepScroll vrai pour redessiner une liste déjà à l'écran sans la ramener en haut */
    private fun showList(keepScroll: Boolean = false) {
        statusBox.visibility = View.GONE
        grid.visibility = View.GONE
        val shown = list.visibility == View.VISIBLE
        list.visibility = View.VISIBLE
        if (!keepScroll || !shown) list.setSelection(0)
    }

    private fun showGrid() {
        statusBox.visibility = View.GONE
        list.visibility = View.GONE
        grid.visibility = View.VISIBLE
        grid.setSelection(0)
    }

    private fun showLoading(message: String) {
        list.visibility = View.GONE
        grid.visibility = View.GONE
        statusBox.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        statusIcon.visibility = View.GONE
        statusText.text = message
        statusSettings.visibility = View.GONE
        retry.visibility = View.GONE
    }

    /**
     * @param iconRes illustration du message
     * @param canOpenSettings vrai si seul le système peut lever le blocage
     */
    private fun showMessage(message: String, canRetry: Boolean, iconRes: Int = R.drawable.ic_alert, canOpenSettings: Boolean = false) {
        list.visibility = View.GONE
        grid.visibility = View.GONE
        statusBox.visibility = View.VISIBLE
        progress.visibility = View.GONE
        statusIcon.visibility = View.VISIBLE
        statusIcon.setImageResource(iconRes)
        statusText.text = message
        statusSettings.visibility = if (canOpenSettings) View.VISIBLE else View.GONE
        retry.visibility = if (canRetry) View.VISIBLE else View.GONE
    }

    private fun applySearch(text: String) {
        adapter.filter(text)
        gridAdapter.filter(text)
        // Les résultats se lisent depuis le haut. Après le filtre : une liste qui change garde sinon sa place.
        if (list.visibility == View.VISIBLE) list.setSelection(0)
        updateEmptyText()
        // Le réseau coché peut être masqué par la recherche : « Continuer » ne le validerait pas.
        if (step == Step.NETWORK) updateSaveEnabled()
    }

    /**
     * « Aucun résultat », sans toucher aux listes : les adaptateurs gardent le texte cherché et le
     * réappliquent à chaque nouvelle liste. Les refiltrer après avoir placé la liste annulerait ce
     * placement.
     */
    private fun updateEmptyText() {
        val searching = step == Step.LINE || step == Step.STOP || step == Step.NETWORK
        val showing = if (grid.visibility == View.VISIBLE) gridAdapter.count else adapter.count
        emptyText.visibility = if (searching && statusBox.visibility != View.VISIBLE && search.text.isNotBlank() && showing == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(search.windowToken, 0)
    }

    // --- Réseau ------------------------------------------------------------------------------

    /**
     * La liste des réseaux s'affiche tout de suite, avec ce que le téléphone en connaît et sans
     * demander la position. Le catalogue est redemandé à Tracéo pendant ce temps, et le réseau de
     * l'endroit est coché d'avance si la position est déjà accordée.
     */
    private fun showNetworks() {
        renderNetworks(fromTop = true)
        refreshCatalog()
        if (PositionReader(this).hasAnyAccess) locateNetwork(announce = false)
    }

    /**
     * @param fromTop vrai à l'arrivée sur l'étape ; faux quand le catalogue ou la position redessinent
     *        une liste que l'usager parcourt peut-être déjà
     */
    private fun renderNetworks(fromTop: Boolean = false) {
        // Un réseau coupé depuis le dernier choix ne peut plus être proposé d'avance. Celui d'un
        // widget à modifier reste le sien.
        catalog.byId(network.id)?.let { known ->
            val unusable = known.status == NetworkStatus.OFF && existing?.networkId != known.id
            network = if (unusable) Networks.ILE_DE_FRANCE else known
        }
        // Le réseau du widget, le plus proche et le dernier choisi passent devant, avec celui qui est
        // coché ; les autres suivent par ville. Une liste longue se cherche, et ses premiers choix
        // ont leur propre carte.
        val choices = NetworkChoices.of(catalog, existing?.networkId, nearestNetwork, prefs.lastNetwork, checked = network.id)
        networkSearch = choices.searchable
        searchBox.visibility = if (networkSearch) View.VISIBLE else View.GONE
        // Une liste redevenue courte n'a plus de champ : le texte qui s'y trouvait ne doit plus la filtrer.
        if (!networkSearch && search.text.isNotEmpty()) search.setText("")
        val items = ArrayList<RowItem>()
        if (!PositionReader(this).hasAnyAccess) {
            items += RowItem(
                title = getString(R.string.network_find),
                subtitle = getString(R.string.network_find_sub),
                iconRes = R.drawable.ic_pin,
                style = RowStyle.CARD,
                payload = FindNetworkRow,
                // Une action, pas un réseau : elle s'efface devant les résultats d'une recherche.
                searchOnly = "",
            )
        }
        choices.cards.forEach { group ->
            group.forEachIndexed { index, item ->
                val off = item.status == NetworkStatus.OFF
                items += RowItem(
                    title = item.city,
                    subtitle = networkSubtitle(item),
                    style = RowStyle.inGroup(index, group.size),
                    radio = true,
                    checked = item.id == network.id,
                    enabled = !off,
                    dimmed = off,
                    payload = item,
                    // Par ville ou par nom de réseau, pas par état ni par raison d'être en tête.
                    searchOnly = NetworkChoices.searchText(item),
                )
            }
        }
        adapter.setItems(items)
        // Placer la liste en dernier : un changement de contenu après coup la ramènerait où elle était.
        showList(keepScroll = !fromTop)
        updateEmptyText()
        stepHelp.setText(
            networkNotice ?: when {
                catalogLoading && catalog.others.isEmpty() -> R.string.network_loading
                catalogFailed -> R.string.network_unavailable
                else -> R.string.network_help
            },
        )
        stepHelp.visibility = View.VISIBLE
        updateSaveEnabled()
        // La position ou le catalogue ont pu cocher un autre réseau.
        updateStepCounter()
        bindMention()
    }

    /** Nom du réseau, son état s'il est dégradé, et ce qui le distingue pour cet usager. */
    private fun networkSubtitle(item: Network): String {
        val parts = ArrayList<String>()
        if (item.name != item.city) parts += item.name
        when (item.status) {
            NetworkStatus.OFF -> parts += getString(R.string.network_off)
            NetworkStatus.STALE -> parts += getString(R.string.network_stale)
            NetworkStatus.OK -> Unit
        }
        when (item.id) {
            existing?.networkId -> parts += getString(R.string.network_reason_current)
            nearestNetwork -> parts += getString(R.string.network_reason_nearest)
            prefs.lastNetwork -> parts += getString(R.string.network_reason_last)
        }
        return parts.joinToString(" · ")
    }

    /**
     * Redemande le catalogue à Tracéo s'il a vieilli, sans retenir la liste déjà affichée. Après un
     * échec, revenir à l'étape du réseau le redemande.
     */
    private fun refreshCatalog() {
        if (catalogJob?.isActive == true) return
        val store = Sources.catalog(this)
        if (!store.isStale(System.currentTimeMillis(), CATALOG_MAX_AGE_MS)) return
        catalogLoading = true
        catalogJob = scope.launch {
            val hadNetworkStep = hasNetworkStep
            catalogFailed = withContext(Dispatchers.IO) {
                try {
                    store.refresh()
                    false
                } catch (e: IOException) {
                    true
                }
            }
            catalogLoading = false
            catalog = store.read()
            network = catalog.byId(network.id) ?: network
            networkPoint?.let { nearestNetwork = catalog.nearest(it)?.id }
            when {
                // Tracéo a répondu, et n'a pas d'autre réseau : il n'y a rien à choisir ici.
                step == Step.NETWORK && !hasNetworkStep -> goTo(Step.MODE)
                step == Step.NETWORK -> showPreselected()
                // Un premier réseau vient d'arriver au catalogue : le choix est proposé tant que rien n'est engagé.
                step == Step.MODE && existing == null && hasNetworkStep && !hadNetworkStep -> goTo(Step.NETWORK)
                // L'étape du réseau est apparue ou a disparu : le compte des étapes et le retour changent.
                step == Step.MODE && hasNetworkStep != hadNetworkStep -> goTo(Step.MODE)
            }
        }
    }

    private fun findNetwork() {
        // Demander son réseau, c'est accepter qu'il soit coché à la place de celui qu'on avait touché.
        networkTouched = false
        if (PositionReader(this).hasAnyAccess) {
            locateNetwork(announce = true)
        } else {
            // La position approximative suffit pour reconnaître une ville.
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_NETWORK_LOCATION)
        }
    }

    /**
     * Coche le réseau de l'endroit où l'on est. La position ne sert qu'ici, sur le téléphone.
     * @param announce vrai si l'usager l'a demandé : un échec est alors expliqué
     */
    private fun locateNetwork(announce: Boolean) {
        locateJob?.cancel()
        locateJob = scope.launch {
            val fix = PositionReader(this@ConfigureActivity).readApproximate()
            if (step != Step.NETWORK) return@launch
            networkPoint = fix?.point
            nearestNetwork = fix?.let { catalog.nearest(it.point) }?.id
            networkNotice = when {
                !announce || nearestNetwork != null -> null
                fix == null -> R.string.network_position_unknown
                // Le catalogue n'est pas encore arrivé : il dira peut-être quel réseau dessert l'endroit.
                catalogLoading -> null
                else -> R.string.network_none_near
            }
            showPreselected()
        }
    }

    /**
     * Redessine la liste après l'arrivée du catalogue ou de la position. Si le réseau coché vient de
     * changer, il est en tête : la liste y revient, et une recherche en cours, qui le masquerait,
     * est effacée.
     */
    private fun showPreselected() {
        val changed = preselectNetwork()
        if (changed && search.text.isNotEmpty()) search.setText("")
        renderNetworks(fromTop = changed)
    }

    /**
     * Le réseau coché d'avance, tant que l'usager n'a rien décidé : celui de l'endroit, sinon le
     * dernier choisi, que le catalogue vient peut-être seulement de faire connaître. Renvoie vrai si
     * le réseau coché a changé.
     */
    private fun preselectNetwork(): Boolean {
        if (networkTouched || existing != null) return false
        val wanted = nearestNetwork?.let(catalog::byId)
            ?: prefs.lastNetwork?.let(catalog::byId)?.takeIf { it.status != NetworkStatus.OFF }
            ?: return false
        val changed = wanted.id != network.id
        network = wanted
        return changed
    }

    /** Le réseau coché, s'il est à l'écran : une recherche peut le masquer, et rien ne se valide sans être vu. */
    private fun checkedNetworkShown(): Network? =
        RowSearch.filter(adapter.items, search.text.toString()).firstOrNull { it.checked }?.payload as? Network

    private fun confirmNetwork() {
        val chosen = checkedNetworkShown()
        if (chosen == null) {
            Toast.makeText(this, R.string.network_pick_one, Toast.LENGTH_LONG).show()
            return
        }
        network = chosen
        // Continuer est une décision : la position, si elle arrive après, ne la défait pas.
        networkTouched = true
        goTo(Step.MODE)
    }

    // --- Mode -------------------------------------------------------------------------------

    /**
     * L'option automatique, puis « Garder la ligne et l'arrêt actuels » quand c'est possible : en tête
     * de l'étape du mode, ou des lignes d'un réseau d'un seul mode.
     */
    private fun leadingRows(): List<RowItem> {
        val items = ArrayList<RowItem>()
        items += RowItem(
            title = getString(R.string.auto_row),
            subtitle = getString(if (existing?.auto == true) R.string.auto_row_active else R.string.auto_row_sub),
            iconRes = R.drawable.ic_pin,
            style = RowStyle.INVERSE,
            payload = AutoRow,
        )
        // Un arrêt choisi avec une clé retirée depuis n'existe pas chez Tracéo : impossible de le garder.
        // Un arrêt d'un autre réseau que celui qui vient d'être choisi non plus.
        val keepable = existing?.takeIf {
            !it.auto && it.networkId == network.id && DataSources.route(it, prefs.hasApiKey) != PassageRoute.RECONFIGURE
        }
        keepable?.let { cfg ->
            items += RowItem(
                title = getString(R.string.keep_current),
                subtitle = getString(R.string.keep_current_sub, UiText.lineTitle(cfg.line), cfg.stop.name),
                badge = cfg.line,
                style = RowStyle.CARD,
                payload = cfg,
            )
        }
        return items
    }

    private fun showModes() {
        val hasKey = prefs.hasApiKey
        val items = ArrayList<RowItem>(leadingRows())
        val modes = DataSources.modes(network, hasKey)
        modes.forEachIndexed { index, m ->
            items += RowItem(
                title = m.labelIn(network),
                subtitle = UiText.modeSubtitle(m, network.id),
                chip = UiText.modeChip(m, network),
                style = RowStyle.inGroup(index, modes.size),
                chevron = true,
                payload = m,
            )
        }
        // La clé PRIM ne sert qu'à l'Île-de-France.
        if (network.isIdfm && !hasKey) {
            items += RowItem(
                title = getString(R.string.key_optional_row),
                subtitle = getString(R.string.key_optional_row_sub),
                iconRes = R.drawable.ic_key,
                style = RowStyle.PLAIN,
                payload = KeyRow,
            )
        }
        adapter.setItems(items)
        showList()
    }

    private fun onItemClicked(item: RowItem) {
        when (step) {
            Step.NETWORK -> when (val payload = item.payload) {
                FindNetworkRow -> findNetwork()
                is Network -> {
                    networkTouched = true
                    network = payload
                    adapter.items.forEach { if (it.radio) it.checked = it === item }
                    adapter.notifyDataSetChanged()
                    updateSaveEnabled()
                    // Le compte des étapes et la mention suivent le réseau coché.
                    updateStepCounter()
                    bindMention()
                }
            }
            Step.MODE -> when (val payload = item.payload) {
                is WidgetConfig -> keepCurrent(payload)
                is TransportMode -> {
                    mode = payload
                    line = null
                    stop = null
                    viaShortcut = false
                    goTo(Step.LINE)
                }
                KeyRow -> startActivity(Intent(this, KeyActivity::class.java))
                AutoRow -> chooseAuto()
            }
            // Sans étape du mode, les lignes portent aussi l'option automatique et « Garder ».
            Step.LINE -> when (val payload = item.payload) {
                is Line -> {
                    line = payload
                    stop = null
                    viaShortcut = false
                    goTo(Step.STOP)
                }
                is WidgetConfig -> keepCurrent(payload)
                AutoRow -> chooseAuto()
            }
            Step.STOP -> (item.payload as? Stop)?.let {
                stop = it
                goTo(Step.DIRECTION)
            }
            Step.DIRECTION -> toggleDirection(item)
            Step.AUTO -> {
                adapter.items.forEach { it.checked = it === item }
                adapter.notifyDataSetChanged()
                updateSaveEnabled()
            }
            Step.AUTO_COLOR -> Unit
        }
    }

    /** Garde la ligne et l'arrêt du widget : seuls le sens et la couleur restent à régler. */
    private fun keepCurrent(cfg: WidgetConfig) {
        mode = cfg.mode
        line = cfg.line
        stop = cfg.stop
        viaShortcut = true
        goTo(Step.DIRECTION)
    }

    private fun chooseAuto() {
        mode = null
        line = null
        stop = null
        goTo(Step.AUTO)
    }

    // --- Mode automatique ------------------------------------------------------------------------

    private fun startAuto() {
        autoItems = emptyList()
        autoNetwork = null
        if (PositionReader(this).hasPreciseAccess) {
            loadAuto()
            return
        }
        showAutoHelp()
        showLoading(getString(R.string.auto_loading_position))
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_LOCATION,
        )
    }

    private fun showAutoHelp() {
        stepHelp.setText(if (isProximity) R.string.proximite_help else R.string.auto_help)
        stepHelp.visibility = View.VISIBLE
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = permissions.filterIndexed { i, _ -> grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED }
        if (requestCode == REQUEST_NETWORK_LOCATION && step == Step.NETWORK) {
            if (granted.isNotEmpty()) {
                locateNetwork(announce = true)
            } else {
                // Refusée : la liste reste là, le réseau se choisit à la main.
                networkNotice = R.string.network_position_denied
                renderNetworks()
            }
            return
        }
        if (requestCode != REQUEST_LOCATION || step != Step.AUTO) return
        when {
            Manifest.permission.ACCESS_FINE_LOCATION in granted -> loadAuto()
            Manifest.permission.ACCESS_COARSE_LOCATION in granted -> showMessage(
                getString(R.string.auto_error_precise), canRetry = true, R.drawable.ic_location_off, canOpenSettings = true,
            )
            else -> showMessage(
                getString(R.string.auto_error_permission), canRetry = true, R.drawable.ic_location_off, canOpenSettings = true,
            )
        }
    }

    private fun loadAuto() {
        save.isEnabled = false
        loadJob = scope.launch {
            showLoading(getString(R.string.auto_loading_position))
            val fix = PositionReader(this@ConfigureActivity).read()
            if (fix == null || (fix.accuracyM ?: 0f) > AnchorPolicy.MAX_ACCURACY_M) {
                showMessage(getString(R.string.auto_error_position), canRetry = true, R.drawable.ic_location_off)
                return@launch
            }
            // Le réseau se déduit de la position, sur le téléphone. Son index d'arrêts est téléchargé
            // la première fois : celui de l'Île-de-France se construit ici, les autres viennent de Tracéo.
            val job = coroutineContext[Job]
            val lookup = withContext(Dispatchers.IO) {
                AutoMode.lookupEnsuringIndex(this@ConfigureActivity, fix.point) { downloading ->
                    runOnUiThread {
                        if (job?.isActive == true && step == Step.AUTO) showLoading(indexLoadingMessage(downloading))
                    }
                }
            }
            val ranked = when (lookup) {
                is NearbyLookup.Found -> lookup.ranking.list
                is NearbyLookup.NoIndex -> {
                    showMessage(getString(R.string.auto_error_index, lookup.network.city), canRetry = true)
                    return@launch
                }
                is NearbyLookup.NoStop -> {
                    showMessage(getString(R.string.auto_error_no_stop), canRetry = true, R.drawable.ic_search_large)
                    return@launch
                }
                NearbyLookup.NoNetwork -> {
                    // Avec une vingtaine de réseaux, les citer tous ne dit rien : le plus proche suffit.
                    val closest = Sources.catalog(this@ConfigureActivity).read().closest(fix.point)
                    val message = if (closest == null) {
                        getString(R.string.widget_no_network_title)
                    } else {
                        getString(R.string.auto_error_no_network, closest.first.city, formatDistance(closest.second))
                    }
                    showMessage(message, canRetry = true, R.drawable.ic_search_large)
                    return@launch
                }
            }
            autoNetwork = lookup.network.id
            bindMention()
            if (isProximity) {
                // Rien à choisir ici : le widget affiche les lignes proches selon le classement.
                AutoMode.saveConfiguration(
                    context = this@ConfigureActivity,
                    widgetId = widgetId,
                    nearby = ranked.first().nearby,
                    fix = fix,
                    theme = WidgetTheme.AUTO,
                    changedByUser = false,
                )
                finishSaved()
                return@launch
            }
            autoFix = fix
            autoFirst = ranked.first()
            val current = existing?.takeIf { it.auto }?.line?.id
            val preselected = ranked.firstOrNull { it.nearby.line.id == current } ?: ranked.first()
            autoItems = ranked.take(AUTO_ROWS_SHOWN).map { row ->
                RowItem(
                    title = UiText.lineTitle(row.nearby.line),
                    subtitle = getString(R.string.auto_row_detail, row.nearby.stopName, formatDistance(row.nearby.distanceM)),
                    badge = row.nearby.line,
                    radio = true,
                    checked = row === preselected,
                    payload = row,
                )
            }
            showAutoRows()
        }
    }

    private fun indexLoadingMessage(downloading: Network): String =
        if (downloading.isIdfm) {
            getString(R.string.auto_loading_index)
        } else {
            getString(R.string.auto_loading_index_network, downloading.city)
        }

    private fun showAutoRows() {
        adapter.setItems(autoItems)
        showList()
        showAutoHelp()
        bottomBar.visibility = View.VISIBLE
        updateSaveEnabled()
    }

    private fun selectedAutoRow(): RankedLine? = autoItems.firstOrNull { it.checked }?.payload as? RankedLine

    /** En automatique, le widget suit la ligne proche : seuls « Tous les sens » et la couleur se règlent. */
    private fun showAutoColor() {
        val row = selectedAutoRow() ?: return goTo(Step.AUTO)
        adapter.setItems(
            listOf(
                RowItem(
                    title = getString(R.string.direction_all),
                    subtitle = getString(R.string.direction_all_sub),
                    style = RowStyle.GROUP_TOP,
                    checkable = true,
                    checked = true,
                    enabled = false,
                ),
                RowItem(
                    title = getString(R.string.auto_direction_one),
                    style = RowStyle.GROUP_MIDDLE,
                    checkable = true,
                    enabled = false,
                    dimmed = true,
                ),
                RowItem(
                    title = getString(R.string.auto_direction_two),
                    style = RowStyle.GROUP_BOTTOM,
                    checkable = true,
                    enabled = false,
                    dimmed = true,
                ),
            ),
        )
        showList()
        footerHelp.setText(R.string.auto_direction_help)
        footerHelp.visibility = View.VISIBLE
        previewState = null
        renderPreview()
        updateSaveEnabled()
        loadJob = scope.launch {
            val networkId = row.nearby.line.networkId
            val source = Sources.passages(this@ConfigureActivity, networkId)
            var returnsMade = 0
            do {
                val result = withContext(Dispatchers.IO) { AutoMode.fetch(source, row.nearby) }
                val config = AutoMode.configFor(widgetId, row.nearby, selectedTheme)
                previewState = WidgetStates.from(
                    config, listOf(result), null, System.currentTimeMillis(), removeTerminusArrivals = !prefs.hasApiKey,
                )
                renderPreview()
            } while (awaitWake(networkId, listOf(result), returnsMade++))
        }
    }

    private fun saveAutoConfig() {
        val row = selectedAutoRow() ?: return
        val fix = autoFix ?: return
        AutoMode.saveConfiguration(
            context = this,
            widgetId = widgetId,
            nearby = row.nearby,
            fix = fix,
            theme = selectedTheme,
            changedByUser = row !== autoFirst,
        )
        finishSaved()
    }

    // --- Ligne ------------------------------------------------------------------------------

    private fun loadLines(forceRefresh: Boolean) {
        val m = mode ?: return goTo(Step.MODE)
        showLoading(getString(R.string.loading_lines))
        loadJob = scope.launch {
            try {
                val lines = repository.lines(m, forceRefresh, network)
                if (lines.isEmpty()) {
                    showMessage(getString(R.string.no_result), canRetry = true)
                } else {
                    val items = lines.map { l ->
                        RowItem(
                            title = UiText.lineTitle(l),
                            subtitle = lineSubtitle(l),
                            badge = l,
                            searchExtra = "${l.shortName} ${l.name}",
                            payload = l,
                        )
                    }
                    // Sans étape du mode, l'option automatique et « Garder » passent en tête des lignes ;
                    // la grille, faite de pastilles seules, ne saurait pas les montrer.
                    val leading = if (onlyMode != null) leadingRows() else emptyList()
                    if (leading.isEmpty() && fitsGrid(lines)) {
                        gridAdapter.setItems(items)
                        showGrid()
                    } else {
                        adapter.setItems(leading + items)
                        showList()
                    }
                    updateEmptyText()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(getString(R.string.error_referential, e.message ?: e.javaClass.simpleName), canRetry = true)
            }
        }
    }

    /**
     * En Île-de-France, le réseau commercial et l'exploitant. Ailleurs, le parcours quand le titre
     * n'est que le code de la ligne, et l'absence de temps réel, à savoir avant de choisir.
     */
    private fun lineSubtitle(l: Line): String? {
        if (Networks.isIdfm(l.networkId)) return l.subtitle().ifEmpty { null }
        val parts = ArrayList<String>()
        l.name.trim().takeIf { it.isNotEmpty() && it != UiText.lineTitle(l) && it != l.shortName.trim() }?.let { parts += it }
        if (!l.realtime) parts += getString(R.string.line_no_realtime)
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** La grille ne montre que des pastilles : elle cacherait le parcours et l'absence de temps réel d'une ligne d'un autre réseau. */
    private fun fitsGrid(lines: List<Line>): Boolean =
        lines.size <= GRID_MAX_LINES && lines.all {
            Networks.isIdfm(it.networkId) && it.shortName.trim().ifEmpty { it.name.trim() }.length <= GRID_MAX_LABEL
        }

    // --- Arrêt ------------------------------------------------------------------------------

    private fun loadStops(forceRefresh: Boolean) {
        val l = line ?: return goTo(Step.LINE)
        showLoading(getString(R.string.loading_stops))
        loadJob = scope.launch {
            try {
                val stops = repository.stops(l, forceRefresh, network)
                if (stops.isEmpty()) {
                    showMessage(getString(R.string.no_stop), canRetry = true)
                } else {
                    // Hors Île-de-France, aucun réseau ne donne la commune : deux arrêts de même nom
                    // ne se distinguent que par leur position, lue dans l'index du réseau.
                    val needsPlaces = !Networks.isIdfm(l.networkId) && SameNameStops.hasSameNames(stops)
                    val store = AutoMode.store(this@ConfigureActivity, l.networkId)
                    val indexAt = network.indexAt
                    // La liste s'affiche tout de suite, avec l'index que le téléphone a déjà.
                    showStops(stops, if (needsPlaces) withContext(Dispatchers.IO) { stopPlaces(store, l) } else emptyMap(), fromTop = true)
                    // Il n'est demandé à Tracéo que dans ce cas, s'il manque ou s'il est périmé ; la
                    // liste est alors redessinée, sans avoir fait attendre.
                    val renewed = needsPlaces && withContext(Dispatchers.IO) {
                        // Noté avant d'arriver : le ménage des index ne doit pas l'emporter pendant la configuration.
                        prefs.markIndexUsed(l.networkId, System.currentTimeMillis())
                        try {
                            store.downloadIfOutdated(indexAt)
                        } catch (e: IOException) {
                            false
                        }
                    }
                    if (renewed) showStops(stops, withContext(Dispatchers.IO) { stopPlaces(store, l) }, fromTop = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(getString(R.string.error_referential, e.message ?: e.javaClass.simpleName), canRetry = true)
            }
        }
    }

    /** Position des arrêts de la ligne d'après l'index du téléphone ; vide s'il n'y est pas. */
    private fun stopPlaces(store: StopIndexStore, l: Line): Map<String, GeoPoint> =
        store.all()?.let { SameNameStops.places(it, l.id) }.orEmpty()

    /**
     * La liste des arrêts. Sans commune, un arrêt n'a que son nom : ce qui sépare deux homonymes
     * prend la place du sous-titre. Sans position, ils restent séparés et numérotés.
     */
    private fun showStops(stops: List<Stop>, places: Map<String, GeoPoint>, fromTop: Boolean) {
        val items = SameNameStops.choices(stops, places).map { choice ->
            RowItem(
                title = choice.stop.name,
                subtitle = choice.hint ?: choice.stop.town,
                chevron = true,
                payload = choice.stop,
                searchOnly = choice.searchText,
            )
        }
        adapter.setItems(items)
        showList(keepScroll = !fromTop)
        updateEmptyText()
    }

    // --- Sens et couleur ----------------------------------------------------------------------

    /**
     * Si la réponse est celle d'un réseau qui se réveille (vide, son premier relevé à quelques
     * secondes), attend ce relevé comme le fait le widget, et dit s'il faut redemander. Sans cela,
     * le premier choix d'un arrêt d'un réseau endormi ne montrerait jamais ses sens.
     *
     * @param returnsMade retours déjà faits pour ce réveil, bornés comme ceux du widget
     */
    private suspend fun awaitWake(networkId: String, results: List<Result<PassageSnapshot>>, returnsMade: Int): Boolean {
        val now = System.currentTimeMillis()
        val wakeAt = RefreshSchedule.wakeAt(networkId, results.mapNotNull { it.getOrNull() }, now, returnsMade) ?: return false
        delay((wakeAt + RefreshSchedule.WAKE_MARGIN_MS - now).coerceIn(RefreshSchedule.WAKE_MARGIN_MS, RefreshSchedule.WAKE_WINDOW_MS))
        return true
    }

    /** @param returnsMade retours déjà faits au réveil du réseau : la liste est alors redessinée sans écran d'attente. */
    private fun loadDirections(returnsMade: Int = 0) {
        val l = line ?: return goTo(Step.LINE)
        val s = stop ?: return goTo(Step.STOP)
        if (returnsMade == 0) {
            save.isEnabled = false
            showLoading(getString(R.string.loading_directions))
        }
        loadJob = scope.launch {
            val source = Sources.passages(this@ConfigureActivity, l.networkId)
            val results = withContext(Dispatchers.IO) {
                s.stopIds.map { id -> async { id to runCatching { source.passages(id, l.id) } } }.awaitAll()
            }
            directionResults = results.map { it.second }
            directionOptions = Directions.buildOptions(
                s,
                results.associate { (id, result) ->
                    // En Île-de-France, Tracéo inclut les arrivées au terminus : elles ne sont pas un sens à proposer.
                    id to result.getOrNull()?.let { snapshot ->
                        val departures = snapshot.departures
                        if (DataSources.dropsTerminusArrivals(l.networkId, snapshot.viaTraceo)) {
                            Directions.withoutTerminusArrivals(departures, s.name)
                        } else {
                            departures
                        }
                    }
                },
            )

            val rowCount = directionOptions.size + if (oneDirectionOnly) 0 else 1
            val allItem = RowItem(
                title = getString(R.string.direction_all),
                subtitle = getString(R.string.direction_all_sub),
                style = RowStyle.inGroup(0, rowCount),
                checkable = true,
                payload = AllDirections,
            )
            val offset = if (oneDirectionOnly) 0 else 1
            val optionItems = directionOptions.mapIndexed { index, opt ->
                RowItem(
                    title = opt.title,
                    subtitle = opt.subtitle,
                    style = RowStyle.inGroup(index + offset, rowCount),
                    checkable = true,
                    payload = opt,
                )
            }
            preselect(allItem, optionItems, l, s)
            adapter.setItems(if (oneDirectionOnly) optionItems else listOf(allItem) + optionItems)
            showList()
            renderPreview()

            val errors = results.mapNotNull { it.second.exceptionOrNull() }
            val help = getString(if (oneDirectionOnly) R.string.direction_help_single else R.string.direction_help)
            footerHelp.text = when {
                errors.isNotEmpty() && errors.size == results.size ->
                    getString(R.string.error_directions, errors.first().message ?: "") + "\n\n" + help
                // Aucun sens ne sera jamais détecté : l'aide habituelle ferait attendre pour rien.
                !l.realtime -> getString(R.string.direction_no_realtime)
                else -> help
            }
            footerHelp.visibility = View.VISIBLE
            updateSaveEnabled()
            // Changer d'étape annule ce travail, attente comprise.
            if (awaitWake(l.networkId, directionResults, returnsMade)) loadDirections(returnsMade + 1)
        }
    }

    /**
     * Coche les sens déjà choisis lors d'une précédente configuration du même arrêt. Sinon, coche
     * toujours un choix, pour que « Enregistrer » ne reste jamais grisé sans raison visible :
     * le premier sens pour un format à un seul sens, « Tous les sens » pour les autres.
     */
    private fun preselect(allItem: RowItem, optionItems: List<RowItem>, l: Line, s: Stop) {
        // Un widget posé sur un seul quai retrouve ses sens dans l'arrêt qui réunit maintenant les deux.
        val previous = existing?.takeIf {
            it.line.id == l.id && it.networkId == l.networkId && it.stop.stopIds.isNotEmpty() && s.stopIds.containsAll(it.stop.stopIds)
        }?.directions
        val wasAllDirections = previous != null && s.stopIds.all { previous[it]?.isEmpty() == true }
        if (previous != null && !wasAllDirections) {
            optionItems.forEach { item ->
                val opt = item.payload as DirectionOption
                val keys = previous[opt.stopId] ?: return@forEach
                val key = opt.key
                item.checked = keys.isEmpty() || (key != null && keys.any { normalizeLabel(it) == normalizeLabel(key) })
            }
            if (oneDirectionOnly) optionItems.filter { it.checked }.drop(1).forEach { it.checked = false }
        }
        // Rien à reprendre (nouveau widget, tous les sens, ou sens disparus depuis) : choix par défaut.
        if (optionItems.none { it.checked }) {
            if (oneDirectionOnly) optionItems.firstOrNull()?.checked = true else allItem.checked = true
        }
    }

    private fun toggleDirection(item: RowItem) {
        if (!item.checkable) return
        item.checked = !item.checked
        if (item.payload === AllDirections) {
            if (item.checked) adapter.items.forEach { if (it !== item) it.checked = false }
        } else if (item.checked) {
            adapter.items.forEach { other ->
                if (other !== item && (oneDirectionOnly || other.payload === AllDirections)) other.checked = false
            }
        }
        adapter.notifyDataSetChanged()
        updateSaveEnabled()
        renderPreview()
    }

    /**
     * Sans choix, le bouton paraît grisé mais reste actif : le toucher explique ce qui manque,
     * au lieu de ne rien faire.
     */
    private fun updateSaveEnabled() {
        save.isEnabled = true
        val chosen = if (step == Step.NETWORK) checkedNetworkShown() != null else adapter.items.any { it.checked }
        save.alpha = if (chosen) 1f else DISABLED_ALPHA
    }

    /** La configuration en cours de réglage ; sans sens coché, tous les sens. */
    private fun directionConfig(): WidgetConfig? {
        val m = mode ?: return null
        val l = line ?: return null
        val s = stop ?: return null
        val selected = adapter.items.filter { it.checked }.mapNotNull { it.payload as? DirectionOption }
        val all = selected.isEmpty()
        val directions = if (all) Directions.allDirections(s) else Directions.toSelection(directionOptions, selected)
        val label = if (all) Directions.ALL_DIRECTIONS else Directions.label(directionOptions, selected, s)
        return WidgetConfig(widgetId, m, l, s, directions, label, selectedTheme)
    }

    private fun saveConfig() {
        if (adapter.items.none { it.checked }) {
            val message = when {
                adapter.items.none { it.checkable } -> R.string.direction_none_found
                oneDirectionOnly -> R.string.direction_select_single
                else -> R.string.direction_select_one
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        val config = directionConfig() ?: return
        prefs.saveConfig(config)
        prefs.lastNetwork = config.networkId
        finishSaved()
    }

    private fun finishSaved() {
        // Le rendu publie aussi la notification, si elle a été activée avant l'enregistrement.
        WidgetUpdater.render(this, widgetId)
        WidgetUpdater.requestRefresh(this, widgetId)
        WidgetUpdater.scheduleTick(this)
        setResult(RESULT_OK, resultIntent())
        Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
        // Le widget est déjà enregistré : la demande qui suit ne peut pas le laisser inachevé.
        if (existing == null && WidgetUpdater.needsExactAlarmAccess(this)) offerExactAlarms() else finish()
    }

    /**
     * Android 14 et plus : à la création d'un widget, propose d'autoriser « Alarmes et rappels », sans
     * quoi les temps d'attente peuvent prendre du retard. « Plus tard » ferme l'écran ; la carte de
     * l'accueil reste là pour y revenir, et la question revient au prochain widget créé.
     */
    private fun offerExactAlarms() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exact_alarm_title)
            .setMessage(R.string.exact_alarm_widget_body)
            .setPositiveButton(R.string.exact_alarm_allow) { _, _ ->
                awaitingAlarmAccess = true
                ExactAlarmAccess.openSettings(this)
            }
            .setNegativeButton(R.string.exact_alarm_later) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    // --- Aperçu et couleur -----------------------------------------------------------------------

    private fun renderPreview() {
        val config = when (step) {
            Step.DIRECTION -> directionConfig()?.also {
                previewState = WidgetStates.from(
                    it, directionResults, null, System.currentTimeMillis(), removeTerminusArrivals = !prefs.hasApiKey,
                )
            }
            Step.AUTO_COLOR -> selectedAutoRow()?.let { AutoMode.configFor(widgetId, it.nearby, selectedTheme) }
            else -> null
        } ?: return
        val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(widgetId)
        val (widthDp, heightDp) = WidgetRenderer.previewSize(this, options, chosenFormat)
        val preview = WidgetRenderer.preview(
            this, config, previewState, prefs.hasApiKey, widthDp, heightDp, System.currentTimeMillis(), chosenFormat,
        )
        val density = resources.displayMetrics.density
        val available = resources.displayMetrics.widthPixels - 2 * resources.getDimension(R.dimen.screen_padding)
        val scale = min(min(1f, available / (widthDp * density)), PREVIEW_MAX_HEIGHT_DP / heightDp)
        previewImage.layoutParams = FrameLayout.LayoutParams(
            (widthDp * density * scale).roundToInt(),
            (heightDp * density * scale).roundToInt(),
            Gravity.CENTER_HORIZONTAL,
        )
        previewImage.setImageBitmap(preview.bitmap)
        previewImage.contentDescription = preview.description
        previewBox.visibility = View.VISIBLE
    }

    private fun renderSwatches() {
        swatches.removeAllViews()
        val gap = (6 * resources.displayMetrics.density).roundToInt()
        ThemeChoices.available(chosenFormat).forEachIndexed { index, theme ->
            val cell = layoutInflater.inflate(R.layout.item_swatch, swatches, false)
            (cell.layoutParams as LinearLayout.LayoutParams).marginStart = if (index == 0) 0 else gap
            cell.findViewById<View>(R.id.swatch_color).background = swatchFill(theme)
            cell.findViewById<TextView>(R.id.swatch_label).setText(labelOf(theme))
            cell.isSelected = theme == selectedTheme
            cell.setOnClickListener {
                selectedTheme = theme
                renderSwatches()
                renderPreview()
            }
            swatches.addView(cell)
        }
    }

    private fun labelOf(theme: WidgetTheme): Int = when (theme) {
        WidgetTheme.AUTO -> R.string.theme_auto
        WidgetTheme.DARK -> R.string.theme_dark
        WidgetTheme.LIGHT -> R.string.theme_light
        WidgetTheme.LINE -> R.string.theme_line
    }

    private fun swatchFill(theme: WidgetTheme): Drawable = when (theme) {
        WidgetTheme.DARK -> swatchShape(DARK_SWATCH)
        WidgetTheme.LIGHT -> swatchShape(LIGHT_SWATCH)
        WidgetTheme.LINE -> swatchShape(
            (line ?: selectedAutoRow()?.nearby?.line)?.let { Palettes.lineColors(it).first } ?: Color.GRAY,
        )
        // Moitié sombre, moitié claire : la couleur suit le thème du téléphone.
        WidgetTheme.AUTO -> LayerDrawable(
            arrayOf(
                swatchShape(LIGHT_SWATCH),
                ClipDrawable(swatchShape(DARK_SWATCH), Gravity.START, ClipDrawable.HORIZONTAL).apply { level = CLIP_HALF },
            ),
        )
    }

    private fun swatchShape(color: Int): Drawable = GradientDrawable().apply {
        val density = resources.displayMetrics.density
        setColor(color)
        cornerRadius = 7 * density
        setStroke((density).roundToInt(), getColor(R.color.outline))
    }
}

private const val DISABLED_ALPHA = 0.4f
private val DARK_SWATCH = 0xFF15171C.toInt()
private val LIGHT_SWATCH = 0xFFF7F6F1.toInt()
