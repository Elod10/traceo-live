package fr.gotraceo.live.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.normalizeLabel
import fr.gotraceo.live.widget.BadgeRenderer
import kotlin.math.roundToInt

/** Présentation d'une ligne de liste. */
enum class RowStyle {
    /** Sans fond, séparée de la précédente par un filet. */
    PLAIN,

    /** Carte isolée. */
    CARD,

    /** Carte en couleur inversée : l'option mise en avant. */
    INVERSE,

    /** Lignes regroupées dans une même carte. */
    GROUP_TOP,
    GROUP_MIDDLE,
    GROUP_BOTTOM,
    ;

    val startsBlock: Boolean get() = this == CARD || this == GROUP_TOP
    val isCard: Boolean get() = this != PLAIN

    companion object {
        /** La place d'une ligne dans une carte de [size] lignes. */
        fun inGroup(index: Int, size: Int): RowStyle = when {
            size <= 1 -> CARD
            index == 0 -> GROUP_TOP
            index == size - 1 -> GROUP_BOTTOM
            else -> GROUP_MIDDLE
        }
    }
}

/** Élément d'une liste de l'assistant de configuration. */
class RowItem(
    val title: String,
    val subtitle: String? = null,
    /** Ligne dont la pastille est affichée en icône (dessinée à la demande). */
    val badge: Line? = null,
    /** Pastille de texte : l'icône d'un mode de transport. */
    val chip: String? = null,
    val iconRes: Int? = null,
    val style: RowStyle = RowStyle.PLAIN,
    val chevron: Boolean = false,
    val checkable: Boolean = false,
    val radio: Boolean = false,
    var checked: Boolean = false,
    /** Faux pour une ligne qu'on ne peut pas toucher. */
    val enabled: Boolean = true,
    /** Vrai pour griser une ligne indisponible. */
    val dimmed: Boolean = false,
    /** Texte supplémentaire pris en compte par la recherche. */
    val searchExtra: String = "",
    val payload: Any? = null,
) {
    val searchText: String = normalizeLabel("$title ${subtitle.orEmpty()} $searchExtra")
}

/**
 * Adaptateur simple avec filtrage par texte.
 *
 * @param cells vrai pour une grille de pastilles, faux pour une liste
 */
class RowAdapter(private val context: Context, private val cells: Boolean = false) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val badgeCache = LruCache<String, Bitmap>(80)
    private val density = context.resources.displayMetrics.density
    private var all: List<RowItem> = emptyList()
    private var visible: List<RowItem> = emptyList()
    private var query: String = ""

    fun setItems(items: List<RowItem>) {
        all = items
        applyFilter()
    }

    fun filter(text: String) {
        query = normalizeLabel(text)
        applyFilter()
    }

    val items: List<RowItem> get() = all

    private fun applyFilter() {
        val words = query.split(' ').filter { it.isNotEmpty() }
        visible = if (words.isEmpty()) all else all.filter { item -> words.all { item.searchText.contains(it) } }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = visible.size
    override fun getItem(position: Int): RowItem = visible[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun areAllItemsEnabled(): Boolean = false
    override fun isEnabled(position: Int): Boolean = visible[position].enabled

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        if (cells) cellView(position, convertView, parent) else rowView(position, convertView, parent)

    private fun cellView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_line_cell, parent, false)
        val item = getItem(position)
        view.contentDescription = item.title
        val badge = view.findViewById<ImageView>(R.id.cell_badge)
        item.badge?.let { badge.setImageBitmap(badgeFor(it, CELL_BADGE_DP)) }
        return view
    }

    private fun rowView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_row, parent, false)
        val item = getItem(position)
        val style = item.style

        val root = view.findViewById<View>(R.id.row_root)
        root.setPaddingRelative(root.paddingStart, dp(if (style.startsBlock && position > 0) 10 else 0), root.paddingEnd, 0)
        val card = view.findViewById<View>(R.id.row_card)
        when (style) {
            RowStyle.PLAIN -> card.setBackgroundResource(selectableBackground())
            RowStyle.CARD -> card.setBackgroundResource(R.drawable.bg_card_touch)
            RowStyle.INVERSE -> card.setBackgroundResource(R.drawable.bg_inverse_card)
            RowStyle.GROUP_TOP -> card.setBackgroundResource(R.drawable.bg_group_top)
            RowStyle.GROUP_MIDDLE -> card.setBackgroundResource(R.drawable.bg_group_middle)
            RowStyle.GROUP_BOTTOM -> card.setBackgroundResource(R.drawable.bg_group_bottom)
        }

        val hasIcon = item.badge != null || item.chip != null || item.iconRes != null
        val divider = view.findViewById<View>(R.id.row_divider)
        val showDivider = when (style) {
            RowStyle.PLAIN -> position > 0 && getItem(position - 1).style == RowStyle.PLAIN
            RowStyle.GROUP_MIDDLE, RowStyle.GROUP_BOTTOM -> true
            else -> false
        }
        divider.visibility = if (showDivider) View.VISIBLE else View.GONE
        (divider.layoutParams as LinearLayout.LayoutParams).marginStart = dp(
            when {
                style == RowStyle.PLAIN && hasIcon -> ICON_INDENT_DP
                style == RowStyle.PLAIN -> 0
                hasIcon -> CARD_PADDING_DP + ICON_INDENT_DP
                item.checkable -> CARD_PADDING_DP + CHECK_INDENT_DP
                else -> CARD_PADDING_DP
            },
        )

        val content = view.findViewById<View>(R.id.row_content)
        val inset = dp(if (style.isCard) CARD_PADDING_DP else 0)
        content.setPaddingRelative(inset, dp(8), inset, dp(8))
        content.minimumHeight = dp(if (item.subtitle.isNullOrBlank()) MIN_HEIGHT_DP else MIN_HEIGHT_WITH_SUBTITLE_DP)
        content.alpha = if (item.dimmed) DIMMED_ALPHA else 1f

        val inverse = style == RowStyle.INVERSE
        bindIcon(view, item, hasIcon, inverse)

        val title = view.findViewById<TextView>(R.id.item_title)
        val subtitle = view.findViewById<TextView>(R.id.item_subtitle)
        // Une simple liste de noms reste légère : seules les lignes à deux étages ont un titre appuyé.
        title.setTextAppearance(
            if (style == RowStyle.PLAIN && !hasIcon && item.subtitle.isNullOrBlank()) R.style.Text_RowTitleLight else R.style.Text_RowTitle,
        )
        title.setTextColor(context.getColor(if (inverse) R.color.on_action_primary else R.color.text_primary))
        subtitle.setTextColor(context.getColor(if (inverse) R.color.on_action_primary else R.color.text_secondary))
        title.text = item.title
        subtitle.text = item.subtitle.orEmpty()
        subtitle.visibility = if (item.subtitle.isNullOrBlank()) View.GONE else View.VISIBLE

        view.findViewById<View>(R.id.item_chevron).visibility = if (item.chevron) View.VISIBLE else View.GONE
        val check = view.findViewById<CheckBox>(R.id.item_check)
        check.visibility = if (item.checkable && !item.radio) View.VISIBLE else View.GONE
        check.isChecked = item.checked
        check.isEnabled = !item.dimmed
        val radio = view.findViewById<RadioButton>(R.id.item_radio)
        radio.visibility = if (item.radio) View.VISIBLE else View.GONE
        radio.isChecked = item.checked
        return view
    }

    private fun bindIcon(view: View, item: RowItem, hasIcon: Boolean, inverse: Boolean) {
        view.findViewById<View>(R.id.row_icon_box).visibility = if (hasIcon) View.VISIBLE else View.GONE
        val icon = view.findViewById<ImageView>(R.id.item_icon)
        val chip = view.findViewById<TextView>(R.id.item_chip)
        chip.visibility = if (item.chip != null) View.VISIBLE else View.GONE
        chip.text = item.chip.orEmpty()
        val badge = item.badge
        when {
            badge != null -> {
                icon.imageTintList = null
                icon.setImageBitmap(badgeFor(badge, ROW_BADGE_DP))
                icon.visibility = View.VISIBLE
            }
            item.iconRes != null -> {
                // Toujours une teinte explicite : la retirer effacerait aussi celle de l'icône, dessinée en blanc.
                icon.imageTintList = context.getColorStateList(if (inverse) R.color.on_action_primary else R.color.text_primary)
                icon.setImageResource(item.iconRes)
                icon.visibility = View.VISIBLE
            }
            else -> {
                icon.setImageDrawable(null)
                icon.visibility = View.GONE
            }
        }
    }

    private fun selectableBackground(): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return value.resourceId
    }

    private fun badgeFor(line: Line, heightDp: Float): Bitmap {
        val key = "${line.networkId}|${line.mode}|${line.shortName}|${line.color}|${line.textColor}|$heightDp"
        return badgeCache.get(key) ?: BadgeRenderer.render(context, line, heightDp).also { badgeCache.put(key, it) }
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        const val CELL_BADGE_DP = 50f
        const val ROW_BADGE_DP = 30f
        const val ICON_INDENT_DP = 56
        const val CHECK_INDENT_DP = 40
        const val CARD_PADDING_DP = 14
        const val MIN_HEIGHT_DP = 50
        const val MIN_HEIGHT_WITH_SUBTITLE_DP = 62
        const val DIMMED_ALPHA = 0.45f
    }
}
