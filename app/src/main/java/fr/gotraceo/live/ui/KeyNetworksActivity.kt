package fr.gotraceo.live.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.DataSources
import fr.gotraceo.live.data.Network
import fr.gotraceo.live.data.NetworkMentions
import fr.gotraceo.live.data.Prefs
import fr.gotraceo.live.widget.QuotaAlerts
import fr.gotraceo.live.widget.Sources

/**
 * Choix du réseau d'une clé API, ouvert depuis les réglages quand Tracéo propose d'autres réseaux.
 * Seule l'Île-de-France en accepte une aujourd'hui (PRIM) : les autres réseaux figurent dans la liste
 * avec la raison pour laquelle il n'y a rien à demander.
 */
class KeyNetworksActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: RowAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_key_networks)
        SystemBars.pad(this)
        prefs = Prefs(this)
        findViewById<View>(R.id.back).setOnClickListener { finish() }

        val list = findViewById<ListView>(R.id.list)
        val footer = layoutInflater.inflate(R.layout.list_help_footer, list, false)
        footer.findViewById<TextView>(R.id.footer_help).apply {
            setText(R.string.key_networks_footer)
            visibility = View.VISIBLE
        }
        list.addFooterView(footer, null, false)
        adapter = RowAdapter(this)
        list.adapter = adapter
        list.setOnItemClickListener { parent, _, position, _ ->
            val network = (parent.getItemAtPosition(position) as? RowItem)?.payload as? Network ?: return@setOnItemClickListener
            if (DataSources.acceptsPersonalKey(network)) startActivity(Intent(this, KeyActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Au retour de l'écran de la clé PRIM, la clé a pu être ajoutée ou retirée.
        val networks = Sources.catalog(this).read().networks
        adapter.setItems(networks.mapIndexed { index, network -> row(network, RowStyle.inGroup(index, networks.size)) })
    }

    private fun row(network: Network, style: RowStyle): RowItem {
        if (!DataSources.acceptsPersonalKey(network)) {
            // Lisible mais inerte : l'explication compte autant que le choix.
            return RowItem(
                title = NetworkMentions.title(network),
                subtitle = getString(R.string.key_network_open, network.name),
                style = style,
                enabled = false,
                payload = network,
            )
        }
        val status = if (prefs.hasApiKey) {
            val usage = prefs.primUsage()
            getString(R.string.settings_key_usage, QuotaAlerts.number(usage.used), QuotaAlerts.number(usage.quota))
        } else {
            getString(R.string.key_network_none)
        }
        return RowItem(
            title = getString(R.string.key_network_idfm_title),
            subtitle = getString(R.string.key_network_idfm_sub) + "\n" + status,
            style = style,
            chevron = true,
            payload = network,
        )
    }
}
