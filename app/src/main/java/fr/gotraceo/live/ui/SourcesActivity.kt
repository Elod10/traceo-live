package fr.gotraceo.live.ui

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.NetworkMentions
import fr.gotraceo.live.data.Networks
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.widget.AutoMode
import fr.gotraceo.live.widget.Sources
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Sources et licences : les mentions dépendent de la source active en Île-de-France, et des autres
 * réseaux utilisés, dont la source et la licence viennent du catalogue de Tracéo.
 */
class SourcesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sources)
        SystemBars.pad(this)
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val about = findViewById<TextView>(R.id.about)
        about.setText(if (Prefs(this).hasApiKey) R.string.main_about_prim else R.string.main_about_traceo)
        about.movementMethod = LinkMovementMethod.getInstance()
        findViewById<TextView>(R.id.code_licence).movementMethod = LinkMovementMethod.getInstance()

        // La Licence Ouverte demande la date des données réutilisées : celle de l'index, s'il existe.
        AutoMode.store(this).savedAt()?.let { savedAt ->
            val date = DateFormat.getDateInstance(DateFormat.LONG, Locale.FRENCH).format(Date(savedAt))
            findViewById<TextView>(R.id.index_date).apply {
                text = getString(R.string.sources_index_date, date)
                visibility = View.VISIBLE
            }
        }

        showNetworks()

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val versionView = findViewById<TextView>(R.id.version)
        if (version == null) {
            versionView.visibility = View.GONE
        } else {
            versionView.text = getString(R.string.sources_version, version)
        }
    }

    /** Une entrée par réseau utilisé hors Île-de-France, et ces réseaux nommés là où l'application dit ne dépendre de personne. */
    private fun showNetworks() {
        val catalog = Sources.catalog(this).read()
        val networks = Sources.usedNetworks(this)
            .filter { !Networks.isIdfm(it) }
            .map { Mentions.network(catalog.byId(it), it) }
        if (networks.isEmpty()) return

        findViewById<TextView>(R.id.not_affiliated).text = getString(
            R.string.sources_not_affiliated_networks,
            networks.joinToString(" ; ") { NetworkMentions.publisher(it) },
        )
        val dates = DateFormat.getDateInstance(DateFormat.LONG, Locale.FRENCH)
        val paragraphs = networks.joinToString("<br><br>") { network ->
            // L'ODbL et la Licence Ouverte demandent la date des données : celle des horaires compilés.
            NetworkMentions.paragraph(network, network.indexAt?.let { dates.format(Date(it)) })
        }
        findViewById<TextView>(R.id.networks_about).apply {
            text = Mentions.html(
                "<b>" + NetworkMentions.escape(getString(R.string.sources_networks_title)) + "</b><br><br>" + paragraphs,
            )
            movementMethod = LinkMovementMethod.getInstance()
            visibility = View.VISIBLE
        }
    }
}
