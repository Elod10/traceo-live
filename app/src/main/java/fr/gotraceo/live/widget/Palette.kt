package fr.gotraceo.live.widget

import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.Networks
import fr.gotraceo.live.data.WidgetTheme
import kotlin.math.pow

/** Couleurs d'un widget (ARGB). */
data class Palette(
    /** Vrai quand le fond du widget est la couleur de la ligne. */
    val isLine: Boolean,
    val background: Int,
    val text: Int,
    val text2: Int,
    val text3: Int,
    val late: Int,
    val cancelled: Int,
    /** Chiffre grisé lorsque l'actualisation a échoué. */
    val muted: Int,
    val rail: Int,
    val node: Int,
    val badgeBg: Int,
    val badgeFg: Int,
    val hair: Int,
    val chip: Int,
    /** Bloc neutre (voie supprimée ou données anciennes). */
    val surface: Int,
    /** Bloc de la voie et pastille « À quai ». */
    val tileBg: Int,
    val tileFg: Int,
    /** Étiquette de retard sur fond de couleur de ligne. */
    val tagBg: Int,
    val tagFg: Int,
)

object Palettes {

    private const val MIN_CONTRAST = 4.5
    private val INK = 0xFF15171C.toInt()
    private val PAPER = 0xFFF7F6F1.toInt()

    /**
     * Palette d'un widget selon le thème choisi et son format.
     *
     * @param night vrai si le système est en thème sombre : le thème automatique le suit
     */
    fun resolve(theme: WidgetTheme, format: WidgetFormat, line: Line?, night: Boolean = true): Palette {
        val (color, on) = line?.let(::lineColors) ?: (NEUTRAL to onColor(NEUTRAL, null))
        return when {
            // La liste porte des retards en couleur, illisibles sur une couleur de ligne.
            theme == WidgetTheme.LINE && line != null && format != WidgetFormat.LISTE -> linePalette(color, on)
            theme == WidgetTheme.LIGHT || (theme == WidgetTheme.AUTO && !night) -> lightPalette(color, on)
            else -> darkPalette(color, on)
        }
    }

    /**
     * Couleur d'une ligne et couleur de texte lisible dessus. Une ligne dont le réseau ne publie pas
     * de couleur prend celle de son mode ; la couleur de texte publiée n'est gardée que si elle se lit
     * sur la couleur de la ligne.
     */
    fun lineColors(line: Line): Pair<Int, Int> {
        val published = publishedColor(line)
        val color = published ?: modeColor(line.mode)
        return color to onColor(color, publishedTextColor(line).takeIf { published != null })
    }

    /**
     * La couleur de texte que le réseau publie pour sa ligne, ou null. Tracéo sert `null` quand le
     * réseau n'en publie pas, même pour une ligne dont il publie la couleur : [onColor] choisit alors
     * le noir ou le blanc. Un noir servi est donc un choix du réseau, gardé s'il se lit. Sans couleur
     * de ligne publiée, la couleur de texte ne dit rien de celle du mode.
     */
    fun publishedTextColor(line: Line): Int? = parseColor(line.textColor)

    /**
     * La couleur que le réseau publie pour sa ligne, ou null s'il n'en publie pas.
     *
     * Hors Île-de-France, le blanc avec un texte noir (ou sans texte) compte comme une absence : ce
     * sont les valeurs par défaut des horaires au format GTFS, et Tracéo les a servies telles quelles
     * pour une ligne sans couleur avant de servir `null`. Un réseau qui choisirait vraiment ce couple
     * verrait donc ses lignes à la couleur de leur mode.
     */
    fun publishedColor(line: Line): Int? {
        val color = parseColor(line.color) ?: return null
        if (Networks.isIdfm(line.networkId)) return color
        val text = parseColor(line.textColor)
        val gtfsDefault = color == WHITE && (text == null || text == BLACK)
        return color.takeUnless { gtfsDefault }
    }

    private fun darkPalette(line: Int, on: Int) = Palette(
        isLine = false,
        background = INK,
        text = 0xFFF5F3EC.toInt(),
        text2 = 0xFFB7BBC3.toInt(),
        text3 = 0xFF8F949E.toInt(),
        late = 0xFFFFB03B.toInt(),
        cancelled = 0xFFFF8F80.toInt(),
        muted = 0xFF8F949E.toInt(),
        rail = line,
        node = 0xFFF5F3EC.toInt(),
        badgeBg = line,
        badgeFg = on,
        hair = 0xFF2A2D35.toInt(),
        chip = 0xFF2A2D35.toInt(),
        surface = 0xFF23262D.toInt(),
        tileBg = line,
        tileFg = on,
        tagBg = 0xFFFFB03B.toInt(),
        tagFg = INK,
    )

    private fun lightPalette(line: Int, on: Int) = Palette(
        isLine = false,
        background = PAPER,
        text = INK,
        text2 = 0xFF4A4F58.toInt(),
        text3 = 0xFF60656F.toInt(),
        late = 0xFF945000.toInt(),
        cancelled = 0xFFBE2A1C.toInt(),
        muted = 0xFF60656F.toInt(),
        rail = line,
        node = INK,
        badgeBg = line,
        badgeFg = on,
        hair = 0xFFDEDDD5.toInt(),
        chip = 0xFFE9E8E1.toInt(),
        surface = 0xFFE9E8E1.toInt(),
        tileBg = line,
        tileFg = on,
        tagBg = 0xFF945000.toInt(),
        tagFg = PAPER,
    )

    private fun linePalette(line: Int, preferred: Int): Palette {
        var on = preferred
        var ground = ensureContrast(line, on)
        if (contrast(ground, on) < MIN_CONTRAST) {
            // Même retouché, ce fond ne laisse pas lire le petit texte dans la couleur publiée (rouge
            // sur blanc, par exemple) : le texte passe au noir ou au blanc.
            on = onColor(line, null)
            ground = ensureContrast(line, on)
        }
        return Palette(
            isLine = true,
            background = ground,
            text = on,
            text2 = on,
            text3 = on,
            late = on,
            cancelled = on,
            muted = withAlpha(on, 0x80),
            rail = on,
            node = on,
            badgeBg = on,
            badgeFg = ground,
            hair = withAlpha(on, 0x40),
            chip = withAlpha(on, 0x20),
            surface = withAlpha(on, 0x20),
            tileBg = on,
            tileFg = ground,
            tagBg = on,
            tagFg = ground,
        )
    }

    /** Couleur de texte d'une ligne : celle du référentiel si elle est lisible, sinon noir ou blanc. */
    fun onColor(background: Int, preferred: Int?): Int {
        if (preferred != null && contrast(background, preferred) >= 3.0) return preferred
        return if (contrast(background, INK) >= contrast(background, 0xFFFFFFFF.toInt())) INK else 0xFFFFFFFF.toInt()
    }

    /** Assombrit ou éclaircit le fond jusqu'à ce que le petit texte reste lisible. */
    fun ensureContrast(background: Int, on: Int): Int {
        var ground = background
        val darken = luminance(on) > luminance(background)
        var steps = 0
        while (contrast(ground, on) < MIN_CONTRAST && steps < 30) {
            ground = if (darken) scale(ground, 0.95) else lighten(ground, 0.08)
            steps++
        }
        return ground
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun scale(color: Int, factor: Double): Int = mapChannels(color) { (it * factor).toInt() }

    private fun lighten(color: Int, amount: Double): Int = mapChannels(color) { (it + (255 - it) * amount).toInt() }

    private inline fun mapChannels(color: Int, transform: (Int) -> Int): Int {
        val r = transform((color shr 16) and 0xFF).coerceIn(0, 255)
        val g = transform((color shr 8) and 0xFF).coerceIn(0, 255)
        val b = transform(color and 0xFF).coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** Lit une couleur « RRGGBB » ou « #RRGGBB » du référentiel. */
    fun parseColor(hex: String?): Int? {
        val digits = hex?.trim()?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
        val value = digits.toLongOrNull(16) ?: return null
        return (0xFF000000L or value).toInt()
    }

    /** Couleur d'un mode, pour une ligne sans couleur publiée. Le blanc s'y lit toujours. */
    fun modeColor(mode: String): Int = when (mode) {
        "metro" -> 0xFF003CA6.toInt()
        "rail" -> 0xFF7A2E8C.toInt()
        "tram" -> 0xFF0B7A75.toInt()
        "bus" -> 0xFF2E7D32.toInt()
        "boat" -> 0xFF00639B.toInt()
        else -> NEUTRAL
    }

    private val NEUTRAL = 0xFF546E7A.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val BLACK = 0xFF000000.toInt()
}
