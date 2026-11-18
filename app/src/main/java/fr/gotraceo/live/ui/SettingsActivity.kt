package fr.gotraceo.live.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.NetworkCatalog
import fr.gotraceo.live.data.Networks
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.widget.QuotaAlerts
import fr.gotraceo.live.widget.Sources
import fr.gotraceo.live.widget.WidgetUpdater

/** Réglages : source des données, clé API, fréquence d'actualisation, sources et confidentialité. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SystemBars.pad(this)
        prefs = Prefs(this)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        // Avec d'autres réseaux au catalogue, on choisit d'abord le réseau de la clé.
        findViewById<View>(R.id.open_key).setOnClickListener {
            val target = if (Sources.catalog(this).read().others.isEmpty()) KeyActivity::class.java else KeyNetworksActivity::class.java
            startActivity(Intent(this, target))
        }
        findViewById<View>(R.id.open_sources).setOnClickListener { startActivity(Intent(this, SourcesActivity::class.java)) }
        findViewById<View>(R.id.open_privacy).setOnClickListener { PrivacyPolicy.open(this) }
        findViewById<View>(R.id.open_support).setOnClickListener { Support.open(this) }
    }

    override fun onResume() {
        super.onResume()
        // La source, le texte d'aide et les fréquences proposées dépendent de la clé.
        val hasKey = prefs.hasApiKey
        val paused = prefs.primPaused
        // Avec d'autres réseaux au catalogue, chaque source dit le réseau qu'elle sert.
        val catalog = Sources.catalog(this).read()
        val multiNetwork = catalog.others.isNotEmpty()
        val idfmTitle = getString(
            when {
                paused -> R.string.main_source_prim_paused
                hasKey -> R.string.main_source_prim
                else -> R.string.main_source_traceo
            },
        )
        findViewById<TextView>(R.id.source_title).text =
            if (multiNetwork) getString(R.string.settings_source_idfm, idfmTitle) else idfmTitle
        findViewById<TextView>(R.id.source_detail).setText(
            when {
                paused -> R.string.main_source_prim_paused_detail
                hasKey -> R.string.main_source_prim_detail
                else -> R.string.main_source_traceo_detail
            },
        )
        renderOtherSources(catalog)

        // La clé PRIM n'est qu'une des clés possibles dès qu'il y a d'autres réseaux.
        val primUsage = prefs.primUsage()
        val usage = getString(R.string.settings_key_usage, QuotaAlerts.number(primUsage.used), QuotaAlerts.number(primUsage.quota))
        findViewById<TextView>(R.id.key_title).setText(
            when {
                multiNetwork && hasKey -> R.string.settings_keys_edit
                multiNetwork -> R.string.settings_keys_add
                hasKey -> R.string.settings_key_edit
                else -> R.string.settings_key_add
            },
        )
        findViewById<TextView>(R.id.key_subtitle).text = when {
            multiNetwork && hasKey -> getString(R.string.settings_keys_usage, usage)
            multiNetwork -> getString(R.string.settings_keys_add_sub)
            hasKey -> usage
            else -> getString(R.string.settings_key_add_sub)
        }
        val fastest = fastestSourceMinutes(hasKey)
        findViewById<TextView>(R.id.refresh_help).setText(
            when {
                hasKey -> R.string.main_refresh_help_key
                // Un réseau relevé chaque minute permet « Chaque minute », que l'Île-de-France sans clé ne suit pas.
                fastest < DataSources.TRACEO_MIN_REFRESH_MINUTES -> R.string.main_refresh_help_networks
                else -> R.string.main_refresh_help
            },
        )
        renderRefreshChoices(fastest)
    }

    /** Une source par réseau hors Île-de-France des widgets posés : son nom, et la cadence de son relevé. */
    private fun renderOtherSources(catalog: NetworkCatalog) {
        val container = findViewById<LinearLayout>(R.id.other_sources)
        container.removeAllViews()
        Sources.usedNetworks(this).filter { !Networks.isIdfm(it) }.forEach { id ->
            val network = catalog.byId(id)
            val view = layoutInflater.inflate(R.layout.item_source_network, container, false)
            view.findViewById<TextView>(R.id.source_network_title).text = when {
                network == null -> getString(R.string.settings_source_network_city, id)
                network.name == network.city -> getString(R.string.settings_source_network_city, network.city)
                else -> getString(R.string.settings_source_network, network.city, network.name)
            }
            val minutes = network?.sourceMinutes ?: DataSources.TRACEO_MIN_REFRESH_MINUTES
            view.findViewById<TextView>(R.id.source_network_detail).text = if (minutes == 1) {
                getString(R.string.settings_source_network_every_minute)
            } else {
                getString(R.string.settings_source_network_every_n, minutes)
            }
            container.addView(view)
        }
    }

    /**
     * Cadence, en minutes, de la source la plus rapide parmi celles des widgets posés : elle borne
     * les intervalles proposés. L'Île-de-France est relevée toutes les 2 minutes sans clé, et suit
     * la minute avec une clé ; un autre réseau a la cadence que lui donne le catalogue.
     */
    private fun fastestSourceMinutes(hasKey: Boolean): Int {
        val idfm = if (hasKey) 1 else DataSources.TRACEO_MIN_REFRESH_MINUTES
        val catalog = Sources.catalog(this).read()
        val others = WidgetUpdater.widgetIds(this).asList()
            .mapNotNull { prefs.config(it)?.networkId }
            .filter { !Networks.isIdfm(it) }
            .distinct()
            .map { catalog.byId(it)?.sourceMinutes ?: DataSources.TRACEO_MIN_REFRESH_MINUTES }
        return (others + idfm).min()
    }

    private fun renderRefreshChoices(sourceMinutes: Int) {
        val group = findViewById<RadioGroup>(R.id.refresh_choices)
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()
        val choices = DataSources.refreshChoices(Prefs.REFRESH_CHOICES, sourceMinutes)
        val current = DataSources.refreshMinutes(prefs.refreshMinutes, sourceMinutes).takeIf { it in choices }
            ?: Prefs.DEFAULT_REFRESH_MINUTES
        choices.forEach { minutes ->
            val button = layoutInflater.inflate(R.layout.item_refresh_choice, group, false) as RadioButton
            button.id = View.generateViewId()
            button.tag = minutes
            button.text = when (minutes) {
                0 -> getString(R.string.refresh_manual)
                1 -> getString(R.string.refresh_every_minute)
                else -> getString(R.string.refresh_every_n, minutes)
            }
            group.addView(button)
            if (minutes == current) group.check(button.id)
        }
        group.setOnCheckedChangeListener { radio, checkedId ->
            val minutes = radio.findViewById<View>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
            // Comparer à l'intervalle appliqué conserve « Chaque minute » pour le retour d'une clé.
            if (minutes != DataSources.refreshMinutes(prefs.refreshMinutes, sourceMinutes)) {
                prefs.refreshMinutes = minutes
                WidgetUpdater.scheduleTick(this)
            }
        }
    }
}
