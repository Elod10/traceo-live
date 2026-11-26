package fr.gotraceo.live.ui

import android.content.Context
import android.text.Html
import android.text.SpannableStringBuilder
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Network
import fr.gotraceo.live.data.NetworkMentions
import fr.gotraceo.live.data.Networks
import fr.gotraceo.live.widget.Sources

/**
 * Mention courte des écrans qui montrent des données, selon les réseaux affichés : celle de
 * l'Île-de-France est écrite dans l'application, les autres viennent du catalogue de Tracéo.
 */
object Mentions {

    /**
     * Pose sous un écran la mention des réseaux dont il montre les données.
     * @param networkIds réseaux affichés ; sans aucun, la mention est celle de l'Île-de-France
     */
    fun bind(view: TextView, networkIds: Collection<String>) {
        view.text = short(view.context, networkIds)
    }

    fun short(context: Context, networkIds: Collection<String>): CharSequence {
        val ids = networkIds.distinct().ifEmpty { listOf(Networks.IDFM) }
        val catalog = Sources.catalog(context).read()
        val text = SpannableStringBuilder()
        val idfm = ids.any(Networks::isIdfm)
        if (idfm) text.append(context.getText(R.string.data_mention))
        // Les autres réseaux par ville, comme partout ailleurs. Un seul garde sa mention entière ;
        // plusieurs tiennent en une phrase, qui renvoie à l'écran des sources pour le détail.
        val others = ids.filter { !Networks.isIdfm(it) }.map { network(catalog.byId(it), it) }
        NetworkMentions.others(others, afterIdf = idfm)?.let { mention ->
            if (text.isNotEmpty()) text.append("\n")
            text.append(html(mention))
        }
        return text
    }

    fun html(source: String): CharSequence = Html.fromHtml(source, Html.FROM_HTML_MODE_COMPACT)

    /** Le réseau du catalogue, ou ce qu'on en sait encore quand il n'y figure plus : son identifiant. */
    fun network(known: Network?, networkId: String): Network = known ?: Networks.placeholder(networkId, "bus")
}
