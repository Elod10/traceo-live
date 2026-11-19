package fr.gotraceo.live.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import fr.gotraceo.live.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dessine un widget dans une image.
 *
 * Android ignore les polices de l'application dans le texte natif d'un widget : pour garder la
 * police Barlow de la maquette, la frise et les pastilles, tout le widget est dessiné ici. Les
 * coordonnées sont celles de la maquette, en dp.
 */
object WidgetPainter {

    @Volatile
    private var fonts: Fonts? = null

    fun paint(context: Context, model: WidgetModel, widthDp: Float, heightDp: Float, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(
            max(1, (widthDp * density).roundToInt()),
            max(1, (heightDp * density).roundToInt()),
            Bitmap.Config.ARGB_8888,
        )
        val scale = model.format.sceneScale(widthDp, heightDp)
        val canvas = Canvas(bitmap)
        canvas.scale(density * scale, density * scale)
        Scene(context, canvas, widthDp / scale, heightDp / scale, model, fontsFor(context)).draw()
        return bitmap
    }

    private fun fontsFor(context: Context): Fonts =
        fonts ?: Fonts(context.applicationContext).also { fonts = it }
}

private enum class Face { COND_BOLD, COND_SEMI, REGULAR, MEDIUM, SEMI }

private class Fonts(context: Context) {
    private val faces = mapOf(
        Face.COND_BOLD to load(context, R.font.barlow_condensed_bold),
        Face.COND_SEMI to load(context, R.font.barlow_condensed_semibold),
        Face.REGULAR to load(context, R.font.barlow_semicondensed_regular),
        Face.MEDIUM to load(context, R.font.barlow_semicondensed_medium),
        Face.SEMI to load(context, R.font.barlow_semicondensed_semibold),
    )

    operator fun get(face: Face): Typeface = faces.getValue(face)

    private fun load(context: Context, id: Int): Typeface = try {
        context.resources.getFont(id)
    } catch (e: Exception) {
        Typeface.DEFAULT
    }
}

/** Tailles du temps d'attente selon le format. */
private class HeroSpec(
    val number: Float,
    val unit: Float,
    val atStop: Float,
    val imminent: Float,
    val cancelled: Float,
    val cancelledFace: Face = Face.COND_BOLD,
    val pillAtStop: Boolean = true,
    /** « <1 min » à la place de « Imminent », quand le mot ne tiendrait pas en gros. */
    val imminentAsNumber: Boolean = false,
    /** Chiffre barré à la place du mot « Supprimé ». */
    val cancelledAsNumber: Boolean = false,
    val unitGap: Float = 3f,
)

private class Hero(
    val text: String,
    val face: Face,
    val size: Float,
    val color: Int,
    val unit: String,
    val unitSize: Float,
    val unitGap: Float,
    val pill: Boolean,
    val strike: Boolean,
)

private class Scene(
    private val context: Context,
    private val canvas: Canvas,
    private val w: Float,
    private val h: Float,
    private val model: WidgetModel,
    private val fonts: Fonts,
) {
    private val p = model.palette
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    private val staleMessage: String get() = context.getString(R.string.widget_stale)

    /** Vrai si l'heure affichée mérite d'être signalée : actualisation échouée ou relevé ancien. */
    private val timeAlert: Boolean get() = model.stale || model.oldSnapshot

    fun draw() {
        roundRect(0f, 0f, w, h, model.format.radius, p.background)
        val notice = model.notice
        if (notice != null) {
            noticeView(notice)
            return
        }
        if (model.format == WidgetFormat.PROXIMITE) {
            proximite()
            return
        }
        val first = model.passages.firstOrNull() ?: return
        when (model.format) {
            WidgetFormat.PASTILLE -> pastille(first)
            WidgetFormat.UN_SENS -> unSens(first)
            WidgetFormat.GRAND_CHIFFRE -> grandChiffre(first)
            WidgetFormat.FRISE -> {
                // Agrandie en hauteur, la frise reste centrée.
                canvas.save()
                canvas.translate(0f, (h - WidgetFormat.FRISE.baseHeight) / 2f)
                frise(first)
                canvas.restore()
            }
            WidgetFormat.COMPACT -> compact()
            WidgetFormat.DEUX_SENS -> deuxSens()
            WidgetFormat.TRAIN -> train(first)
            WidgetFormat.DEUX_SENS_LARGE -> deuxSensLarge()
            WidgetFormat.LISTE -> liste()
            WidgetFormat.PROXIMITE -> Unit
        }
    }

    // --- À proximité ---------------------------------------------------------------------------

    private fun proximite() {
        val rows = model.proximity
        if (rows.isEmpty()) return
        val left = 16f
        val right = w - 16f
        val top = 12f
        val headerCy = top + 15f
        pin(left, headerCy, 16f, p.text)
        val titleX = left + 24f
        val noteWidth = if (model.stale) {
            textRight(staleMessage, right, headerCy + 4.5f, Face.SEMI, 13f, p.late)
        } else {
            timeLabel(right, headerCy + 4.7f, 13.5f, room = right - titleX - measure(model.stopName, Face.SEMI, 18f) - 8f)
        }
        val title = fit(model.stopName, Face.SEMI, 18f, right - titleX - noteWidth - 8f)
        text(title, titleX, baseline(top, 30f, 18f), Face.SEMI, 18f, p.text)

        val firstTop = top + 34f
        val available = h - 12f - firstTop - (rows.size - 1)
        val blockHeight = min(available / rows.size, MAX_PROXIMITY_BLOCK)
        // Les textes restent alignés quand un badge de bus est plus large qu'un badge de métro.
        val textX = left + rows.maxOf { badgeWidth(PROXIMITY_BADGE, it.badge) } + 10f
        rows.forEachIndexed { index, row ->
            val blockTop = firstTop + index * (blockHeight + 1f)
            if (index > 0) rect(left, blockTop - 1f, right, blockTop, p.hair)
            proximityBlock(row, left, textX, right, blockTop, blockHeight)
        }
    }

    /** Une ligne : l'arrêt et la distance, puis une rangée par sens. */
    private fun proximityBlock(row: ProximityRow, left: Float, textX: Float, right: Float, top: Float, height: Float) {
        badge(left, top + height / 2f, PROXIMITY_BADGE, row.badge, row.badgeBg, row.badgeFg, row.badgeBg)
        val contentHeight = PROXIMITY_PLACE + maxOf(1, row.directions.size) * PROXIMITY_DIRECTION
        val contentTop = top + (height - contentHeight) / 2f
        val place = fit(row.place, Face.REGULAR, 13f, right - textX)
        text(place, textX, baseline(contentTop, PROXIMITY_PLACE, 13f), Face.REGULAR, 13f, p.text3)

        val firstRow = contentTop + PROXIMITY_PLACE
        if (row.directions.isEmpty()) {
            val base = baseline(firstRow, PROXIMITY_DIRECTION, 14.5f)
            text(context.getString(R.string.widget_short_no_departure), textX, base, Face.MEDIUM, 14.5f, p.text2)
            textRight("—", right - PROXIMITY_THEN, base, Face.COND_BOLD, 24f, p.text3)
            return
        }
        row.directions.forEachIndexed { index, direction ->
            proximityDirection(direction, textX, right, firstRow + index * PROXIMITY_DIRECTION)
        }
    }

    /** Un sens : destination à gauche, attente et passage suivant alignés en colonnes à droite. */
    private fun proximityDirection(direction: ProximityDirection, x: Float, right: Float, top: Float) {
        val first = direction.first
        val cancelled = first.kind == WaitKind.CANCELLED
        val base = baseline(top, PROXIMITY_DIRECTION, 14.5f)
        direction.next
            ?.thenLabelFitting { measure(it, Face.MEDIUM, 13f) <= PROXIMITY_THEN - 6f }
            ?.let { textRight(it, right, base, Face.MEDIUM, 13f, if (model.stale) p.text3 else p.text2) }

        val spec = HeroSpec(
            number = 24f, unit = 12.5f, atStop = 15f, imminent = 15f, cancelled = 13f,
            cancelledFace = Face.SEMI, pillAtStop = false, imminentAsNumber = true,
        )
        val timeRight = right - PROXIMITY_THEN
        val hero = hero(first, spec, PROXIMITY_TIME, model.stale)
        val heroWidth = heroWidth(hero)
        drawHero(hero, timeRight - heroWidth, base)

        val arrowColor = if (cancelled || model.stale) p.text3 else p.text2
        arrow(x, top + PROXIMITY_DIRECTION / 2f, 10f, arrowColor)
        val labelX = x + 13f
        val label = fit(direction.label, Face.MEDIUM, 14.5f, timeRight - heroWidth - 6f - labelX)
        val labelColor = if (cancelled || model.stale) p.text3 else p.text
        text(label, labelX, base, Face.MEDIUM, 14.5f, labelColor, strike = cancelled)
    }

    private fun pin(x: Float, cy: Float, size: Float, color: Int) {
        val unit = size / 12f
        val top = cy - size / 2f
        stroke.color = color
        stroke.strokeWidth = 1.3f * unit
        path.reset()
        path.moveTo(x + 6f * unit, top + 11f * unit)
        path.cubicTo(x + 6f * unit, top + 11f * unit, x + 10f * unit, top + 7.3f * unit, x + 10f * unit, top + 4.4f * unit)
        path.arcTo(x + 2f * unit, top + 0.4f * unit, x + 10f * unit, top + 8.4f * unit, 0f, -180f, false)
        path.cubicTo(x + 2f * unit, top + 7.3f * unit, x + 6f * unit, top + 11f * unit, x + 6f * unit, top + 11f * unit)
        path.close()
        canvas.drawPath(path, stroke)
        circle(x + 6f * unit, top + 4.5f * unit, 1.3f * unit, color)
    }

    // --- Un sens ------------------------------------------------------------------------------

    private fun pastille(first: Passage) {
        val pad = 10f
        val badgeWidth = badge(pad, 19f, 22f)
        val tagRight = w - pad
        val tagBase = baseline(8f, 22f, 12.5f)
        val tagRoom = tagRight - pad - badgeWidth - 2f
        val next = WidgetContent.nextPassage(model.passages, first)
        when {
            model.refreshing -> textRight("…", tagRight, tagBase, Face.MEDIUM, 12.5f, p.text2)
            model.autoHintShort != null -> textRight(model.autoHintShort, tagRight, tagBase, Face.SEMI, 12.5f, p.text)
            model.stale -> Unit
            model.oldSnapshot -> snapshotNote(tagRight, tagBase, 12.5f, tagRoom)
            first.isDelayed -> {
                val delay = "+${first.delayMinutes}"
                if (lateTagWidth(delay, 12.5f) <= tagRoom) lateTag(delay, tagRight, tagBase, 12.5f)
            }
            // Peu de place : l'heure du relevé passe avant le passage suivant.
            model.sourceLabel != null -> snapshotNote(tagRight, tagBase, 12.5f, tagRoom)
            next != null -> {
                // Un numéro de ligne long ne laisse parfois de la place que pour le nombre de minutes.
                val label = next.thenLabelFitting { measure(it, Face.MEDIUM, 12.5f) <= tagRoom }
                if (label != null) textRight(label, tagRight, tagBase, Face.MEDIUM, 12.5f, p.text2)
            }
        }

        val spec = HeroSpec(
            number = 46f, unit = 14f, atStop = 28f, imminent = 28f, cancelled = 28f,
            pillAtStop = false, imminentAsNumber = true, cancelledAsNumber = true,
        )
        val heroTop = 30f + (h - 92f) / 2f
        drawHero(hero(first, spec, w - 2f * pad, model.stale), pad, baseline(heroTop, 38f, 46f))

        val footTop = h - 8f - 15f
        val footBase = baseline(footTop, 15f, 12.5f)
        val footCy = footTop + 7.5f
        when {
            model.stale -> {
                warning(pad, footCy, 12f, p.late)
                text(model.timeLabel, pad + 15f, footBase, Face.SEMI, 12.5f, p.late)
            }
            first.kind == WaitKind.CANCELLED -> text("Supprimé", pad, footBase, Face.SEMI, 12.5f, p.cancelled)
            else -> {
                val maxWidth = w - 2f * pad - 12f
                val name = WidgetContent.compactName(first.destination) { measure(it, Face.MEDIUM, 12.5f) <= maxWidth }
                // Sur la couleur de ligne, seul ce triangle distingue un relevé ancien.
                if (model.oldSnapshot) warning(pad, footCy, 10f, p.late) else arrow(pad, footCy, 9f, p.text2)
                text(fit(name, Face.MEDIUM, 12.5f, maxWidth), pad + 12f, footBase, Face.MEDIUM, 12.5f, p.text2)
            }
        }
    }

    private fun unSens(first: Passage) {
        val pad = 12f
        val rowTop = 9f
        val badgeWidth = badge(pad, rowTop + 15.5f, 26f)
        val textX = pad + badgeWidth + 8f
        val textWidth = w - pad - textX
        text(fit(model.stopName, Face.SEMI, 14f, textWidth), textX, baseline(rowTop, 16f, 14f), Face.SEMI, 14f, p.text)
        val cancelled = first.kind == WaitKind.CANCELLED
        destination(
            WidgetContent.shortName(first.destination), textX, rowTop + 16f, 15f, 12.5f, 9f, 3f,
            if (cancelled) p.text3 else p.text2, textWidth, strike = cancelled,
        )

        val base = baseline(h - 9f - 36f, 36f, 46f)
        val right = w - pad
        val next = WidgetContent.nextPassage(model.passages, first)
        val rightWidth = when {
            model.refreshing -> textRight("…", right, base, Face.MEDIUM, 13.5f, p.text2)
            model.autoHint != null -> textRight(model.autoHint, right, base, Face.SEMI, 13.5f, p.text)
            model.stale -> {
                val center = base - 16f
                val top = textRight(staleMessage.substringBefore(' '), right, center - 2f, Face.SEMI, 13f, p.late)
                val bottom = textRight(
                    staleMessage.substringAfter(' ') + " · " + model.timeLabel,
                    right, center + 13f, Face.SEMI, 13f, p.late,
                )
                max(top, bottom)
            }
            first.isDelayed -> lateTag("retard +${first.delayMinutes} min", right, base, 13f)
            next != null -> textRight(next.thenLabel(), right, base, Face.MEDIUM, 13.5f, p.text2)
            else -> 0f
        }

        val spec = HeroSpec(
            number = 46f, unit = 15f, atStop = 24f, imminent = 32f, cancelled = 21f,
            cancelledFace = Face.SEMI,
        )
        val heroWidth = drawHero(hero(first, spec, right - pad - rightWidth - 6f, model.stale), pad, base)
        if (model.sourceLabel != null && !model.stale) {
            snapshotNote(right, base - 16f, 12f, right - pad - heroWidth - 6f)
        }
        val noteX = pad + heroWidth + 6f
        val noteLimit = right - rightWidth - 4f
        val mission = first.mission
        when {
            cancelled -> {
                if (noteX + measure(first.clock, Face.REGULAR, 13.5f) <= noteLimit) {
                    text(first.clock, noteX, base, Face.REGULAR, 13.5f, p.text3, strike = true)
                }
            }
            mission != null && !model.stale && !first.isDelayed -> chip(mission, noteX, base, noteLimit)
        }
    }

    private fun grandChiffre(first: Passage) {
        val pad = 12f
        val cancelled = first.kind == WaitKind.CANCELLED
        val delayed = first.isDelayed && !model.stale
        // L'en-tête reste à l'heure du relevé : le retard passe alors à côté du grand chiffre.
        val delayBesideHero = delayed && model.sourceLabel != null
        header(
            pad, w - pad, pad, 28f, 28f, 17f, 12.5f, 8f,
            tag = if (delayed && !delayBesideHero) "+${first.delayMinutes} min" else null,
        )
        destination(
            WidgetContent.shortName(first.destination), pad, 45f, 17f, 13f, 10f, 3f,
            if (cancelled) p.text3 else p.text2, w - 2f * pad, strike = cancelled,
        )

        val spec = HeroSpec(
            number = 88f, unit = 23f, atStop = 60f, imminent = 44f, cancelled = 40f,
            pillAtStop = false, unitGap = 5f,
        )
        val base = baseline(64f, 76f, 88f)
        val hero = hero(first, spec, w - 2f * pad, model.stale)
        val heroWidth = drawHero(hero, pad, base)
        if (delayBesideHero) {
            val room = w - 2f * pad - heroWidth - 6f
            listOf("+${first.delayMinutes} min", "+${first.delayMinutes}")
                .firstOrNull { lateTagWidth(it, 13f) <= room }
                ?.let { lateTag(it, w - pad, base, 13f) }
        } else if (first.kind == WaitKind.MINUTES && !model.stale) {
            val clockWidth = measure(first.clock, Face.REGULAR, 14f)
            if (pad + heroWidth + 6f + clockWidth <= w - pad) {
                textRight(first.clock, w - pad, base, Face.REGULAR, 14f, p.text3)
            }
        }

        val bottom = h - pad - 34f
        if (model.stale) {
            multiline(staleMessage, pad, bottom, w - 2f * pad, 17f, 14.5f, Face.SEMI, p.late, 2)
        } else {
            val stops = WidgetContent.railPassages(model.passages, first)
            rail(pad, w - 2f * pad, bottom, stops, stops.last().railMinutes - stops.first().railMinutes, 22f)
        }
    }

    private fun frise(first: Passage) {
        val railColor = if (model.stale) p.muted else p.rail
        roundRect(30f, 38f, w - 16f, 43f, 3f, railColor)

        // La pastille est posée sur la frise, qui part de la ligne vers les passages à venir.
        val badgeWidth = badgeWidth(36f)
        roundRect(11f, 19f, 17f + badgeWidth, 61f, 21f, p.background)
        badge(14f, 40f, 36f)

        val timeBase = baseline(11f, 16f, 13f)
        val timeWidth = timeLabel(w - 16f, timeBase, 13f)
        val titleX = max(60f, 14f + badgeWidth + 10f)
        val titleBase = baseline(9f, 19f, 15.5f)
        val titleEnd = w - 16f - timeWidth - 8f
        val stop = fit(model.stopName, Face.SEMI, 15.5f, (titleEnd - titleX) * 0.6f)
        val stopWidth = text(stop, titleX, titleBase, Face.SEMI, 15.5f, p.text)
        val cancelled = first.kind == WaitKind.CANCELLED
        val destColor = if (cancelled) p.text3 else p.text2
        arrow(titleX + stopWidth + 6f, 18.5f, 11f, p.text2)
        val destX = titleX + stopWidth + 23f
        val destText = fit(WidgetContent.shortName(first.destination), Face.MEDIUM, 14f, titleEnd - destX)
        text(destText, destX, baseline(9f, 19f, 14f), Face.MEDIUM, 14f, destColor, strike = cancelled)

        val heroX = max(62f, 14f + badgeWidth + 12f)
        val firstCenter = heroX + 12f
        val spec = HeroSpec(number = 46f, unit = 15f, atStop = 28f, imminent = 30f, cancelled = 30f, unitGap = 2f)
        val base = baseline(46f, 38f, 46f)
        val heroWidth = drawHero(hero(first, spec, 170f, model.stale), heroX, base)

        val firstColor = when {
            model.stale -> p.muted
            cancelled -> p.cancelled
            first.isDelayed -> p.late
            else -> p.node
        }
        circle(firstCenter, 40f, 9f, p.background)
        circle(firstCenter, 40f, 7f, firstColor)

        if (model.stale) {
            text(staleMessage, heroX + heroWidth + 8f, base, Face.SEMI, 16f, p.late)
            return
        }
        var noteEnd = heroX + heroWidth
        if (first.isDelayed) {
            val note = "+${first.delayMinutes} min"
            val noteWidth = lateTagWidth(note, 16f)
            lateTag(note, heroX + heroWidth + 8f + noteWidth, base, 16f)
            noteEnd += 8f + noteWidth
        }
        val stops = WidgetContent.railPassages(model.passages, first)
        val minutes = stops.map { it.railMinutes }
        val centers = WidgetContent.railCenters(
            minutes, firstCenter, w - 34f, minutes.last() - minutes.first(), 28f, noteEnd + 16f,
        )
        val labelBase = baseline(60f, 28f, 24f)
        for (i in 1 until centers.size) {
            val center = centers[i]
            circle(center, 40f, 7f, p.background)
            circle(center, 40f, 5f, p.node)
            val label = stops[i].shortWait
            text(label, center - measure(label, Face.COND_SEMI, 24f) / 2f, labelBase, Face.COND_SEMI, 24f, p.text2)
        }
    }

    // --- Deux sens ----------------------------------------------------------------------------

    private fun compact() {
        // Colonne de gauche : pastille, arrêt et heure, centrés.
        val center = 41f
        val stackTop = (h - 65f) / 2f
        val badgeWidth = badgeWidth(30f)
        badge(center - badgeWidth / 2f, stackTop + 15f, 30f)
        val name = WidgetContent.compactName(model.stopName) { measure(it, Face.SEMI, 14f) <= 62f }
        val stop = fit(name, Face.SEMI, 14f, 62f)
        val stopBase = baseline(stackTop + 31f, 18f, 14f)
        text(stop, center - measure(stop, Face.SEMI, 14f) / 2f, stopBase, Face.SEMI, 14f, p.text)
        val timeColor = timeColor()
        val timeFace = if (timeAlert || model.autoHint != null) Face.SEMI else Face.REGULAR
        val timeBase = baseline(stackTop + 50f, 15f, 12.5f)
        val time = sourcedTime().takeIf { measure(it, timeFace, 12.5f) <= 74f } ?: model.timeLabel
        text(time, center - measure(time, timeFace, 12.5f) / 2f, timeBase, timeFace, 12.5f, timeColor)

        val rowsTop = (h - 72f) / 2f
        compactRow(model.groups.getOrNull(0), rowsTop, showStaleMessage = false)
        compactRow(model.groups.getOrNull(1), rowsTop + 36f, showStaleMessage = true)
    }

    private fun compactRow(group: DirectionGroup?, top: Float, showStaleMessage: Boolean) {
        val left = 80f
        val right = w - 14f
        val cy = top + 18f
        if (group == null) {
            if (showStaleMessage && model.stale) textRight(staleMessage, right, cy + 4.5f, Face.SEMI, 13f, p.late)
            return
        }
        val first = group.first
        val cancelled = first.kind == WaitKind.CANCELLED
        arrow(left, cy, 11f, p.text2)
        val destX = left + 17f
        var limit = right
        val destBase = cy + 0.35f * 14f
        val destColor = when {
            model.stale || cancelled -> p.text3
            else -> p.text
        }
        if (model.stale && showStaleMessage) {
            limit -= textRight(staleMessage, right, cy + 4.5f, Face.SEMI, 13f, p.late) + 6f
        } else {
            val next = group.next
            if (next != null) {
                val color = if (model.stale) p.text3 else p.text2
                textRight(next.thenLabel(), right, cy + 0.35f * 14.5f, Face.MEDIUM, 14.5f, color)
            }
            limit -= 50f
            val spec = HeroSpec(
                number = 32f, unit = 13f, atStop = 22f, imminent = 24f, cancelled = 16f,
                cancelledFace = Face.SEMI, unitGap = 2f,
            )
            val hero = hero(first, spec, 90f, model.stale)
            val heroWidth = heroWidth(hero)
            drawHero(hero, limit - heroWidth, centeredBaseline(cy, hero))
            limit -= heroWidth + 6f
            if (first.isDelayed && !model.stale) {
                limit -= lateTag("+${first.delayMinutes} min", limit, cy + 4.5f, 13f) + 6f
            }
        }
        text(fit(group.label, Face.MEDIUM, 14f, limit - destX), destX, destBase, Face.MEDIUM, 14f, destColor, strike = cancelled)
    }

    private fun deuxSens() {
        val pad = 14f
        header(pad, w - pad, pad, 26f, 26f, 16.5f, 12.5f, 8f)
        val firstTop = pad + 36f
        val blockHeight = 59f
        val gap = max(10f, h - pad - firstTop - 2f * blockHeight)
        model.groups.getOrNull(0)?.let { directionBlock(it, pad, firstTop, w - 2f * pad, showStaleMessage = true) }
        model.groups.getOrNull(1)?.let {
            directionBlock(it, pad, firstTop + blockHeight + gap, w - 2f * pad, showStaleMessage = false)
        }
    }

    private fun directionBlock(group: DirectionGroup, x: Float, top: Float, width: Float, showStaleMessage: Boolean) {
        val first = group.first
        val cancelled = first.kind == WaitKind.CANCELLED
        destination(group.label, x, top, 17f, 13.5f, 10f, 3f, if (cancelled) p.text3 else p.text2, width, strike = cancelled)
        val base = baseline(top + 17f, 42f, 46f)
        val right = x + width
        val next = group.next
        val rightWidth = when {
            model.stale && showStaleMessage -> {
                val center = base - 16f
                val upper = textRight(staleMessage.substringBefore(' '), right, center - 2.5f, Face.SEMI, 13.5f, p.late)
                val lower = textRight(staleMessage.substringAfter(' '), right, center + 12.5f, Face.SEMI, 13.5f, p.late)
                max(upper, lower)
            }
            first.isDelayed && !model.stale -> lateTag("+${first.delayMinutes} min", right, base, 14f)
            next != null -> textRight(next.thenLabel(), right, base, Face.MEDIUM, 14.5f, if (model.stale) p.text3 else p.text2)
            else -> 0f
        }
        val spec = HeroSpec(number = 46f, unit = 15f, atStop = 26f, imminent = 32f, cancelled = 30f)
        drawHero(hero(first, spec, width - rightWidth - 6f, model.stale), x, base)
    }

    private fun deuxSensLarge() {
        val padX = 16f
        header(padX, w - padX, 14f, 30f, 30f, 20f, 13.5f, 10f)
        val gap = 16f
        val columnWidth = (w - 2f * padX - gap) / 2f
        val top = h - 10f - 130f
        val span = model.groups.maxOfOrNull { group ->
            val stops = group.railPassages
            stops.last().railMinutes - stops.first().railMinutes
        } ?: 0
        model.groups.forEachIndexed { index, group ->
            largeColumn(group, padX + index * (columnWidth + gap), top, columnWidth, span, showStaleMessage = index == 0)
        }
    }

    private fun largeColumn(group: DirectionGroup, x: Float, top: Float, width: Float, span: Int, showStaleMessage: Boolean) {
        val first = group.first
        val cancelled = first.kind == WaitKind.CANCELLED
        destination(group.label, x, top, 18f, 14.5f, 11f, 4f, if (cancelled) p.text3 else p.text2, width, strike = cancelled)
        val spec = HeroSpec(
            number = 88f, unit = 22f, atStop = 66f, imminent = 44f, cancelled = 42f,
            pillAtStop = false, unitGap = 4f,
        )
        val base = baseline(top + 18f, 72f, 88f)
        val hero = hero(first, spec, width, model.stale)
        val heroWidth = drawHero(hero, x, base)
        if (first.isDelayed && !model.stale && heroWidth + 6f + lateTagWidth("+${first.delayMinutes} min", 13.5f) <= width) {
            lateTag("+${first.delayMinutes} min", x + width, base, 13.5f)
        }
        val railTop = top + 98f
        if (model.stale && showStaleMessage) {
            multiline(staleMessage, x, railTop, width, 16f, 14.5f, Face.SEMI, p.late, 2)
        } else {
            rail(x, width, railTop, group.railPassages, span, 20f)
        }
    }

    // --- RER et train -------------------------------------------------------------------------

    private fun train(first: Passage) {
        val pad = 12f
        val cancelled = first.kind == WaitKind.CANCELLED
        val badgeWidth = badge(pad, pad + 18f, 28f)
        val textX = pad + badgeWidth + 8f
        val timeWidth = if (shownSource != null) {
            timeLabel(w - pad, pad + 17f, 12.5f, stacked = true)
        } else {
            timeLabel(w - pad, pad + 14.5f, 12.5f)
        }
        val stop = fit(model.stopName, Face.SEMI, 15.5f, w - pad - textX - timeWidth - 6f)
        text(stop, textX, baseline(pad + 1f, 18f, 15.5f), Face.SEMI, 15.5f, p.text)
        destination(
            WidgetContent.shortName(first.destination), textX, pad + 19f, 16f, 13f, 10f, 3f,
            if (cancelled) p.text3 else p.text2, w - pad - textX, strike = cancelled,
        )

        val top = pad + 42f
        val bottom = h - pad - 28f
        val inner = w - 2f * pad - 6f
        val leftWidth = inner * 1.25f / 2.25f
        val tileX = pad + leftWidth + 6f
        val tileWidth = inner - leftWidth

        val clockBase = baseline(top + 8f, 17f, 14f)
        text(first.clock, pad + 2f, clockBase, Face.MEDIUM, 14f, p.text2, strike = cancelled)
        val spec = HeroSpec(
            number = 46f, unit = 14f, atStop = 30f, imminent = 30f, cancelled = 18f,
            cancelledFace = Face.SEMI, pillAtStop = false, imminentAsNumber = true, unitGap = 2f,
        )
        val heroBase = baseline(bottom - 8f - 42f, 42f, 46f)
        drawHero(hero(first, spec, leftWidth - 2f, model.stale), pad + 2f, heroBase)

        val quiet = cancelled || model.stale
        roundRect(tileX, top, tileX + tileWidth, bottom, 16f, if (quiet) p.surface else p.tileBg)
        val tileColor = if (quiet) p.text3 else p.tileFg
        text(first.platformLabel, tileX + 9f, clockBase, Face.SEMI, 14f, tileColor)
        val platform = first.platform ?: "—"
        val platformSize = fittedSize(platform, Face.COND_BOLD, 46f, tileWidth - 18f)
        text(platform, tileX + 9f, heroBase, Face.COND_BOLD, platformSize, tileColor, strike = cancelled)

        val footTop = h - pad - 22f
        val next = WidgetContent.nextPassage(model.passages, first)
        when {
            model.stale -> text(
                fit(staleMessage, Face.SEMI, 13.5f, w - 2f * pad - 4f),
                pad + 2f, baseline(footTop, 22f, 13.5f), Face.SEMI, 13.5f, p.late,
            )
            first.isDelayed -> text(
                "retard +${first.delayMinutes} min",
                pad + 2f, baseline(footTop, 22f, 14f), Face.SEMI, 14f, p.late,
            )
            next != null -> text(
                next.thenLabel(withUnit = true),
                pad + 2f, baseline(footTop, 22f, 14f), Face.MEDIUM, 14f, p.text2,
            )
        }
    }

    private fun liste() {
        val padX = 16f
        val top = 12f
        val headerHeight = 34f
        val firstHeight = 54f
        val badgeWidth = badge(padX, top + 15f, 28f)
        val textX = padX + badgeWidth + 10f
        val right = w - padX
        val headerBase = baseline(top, 30f, 18f)
        val noteWidth = if (model.stale) {
            textRight(staleMessage, right, top + 15f + 4.5f, Face.SEMI, 13f, p.late)
        } else {
            timeLabel(right, top + 15f + 4.7f, 13.5f, room = right - textX - measure(model.stopName, Face.SEMI, 18f) - 8f)
        }
        val stop = fit(model.stopName, Face.SEMI, 18f, right - textX - noteWidth - 8f)
        text(stop, textX, headerBase, Face.SEMI, 18f, p.text)

        val firstTop = top + headerHeight
        val hasChips = model.passages.any { it.mission != null }
        listRow(model.passages.first(), firstTop, firstHeight, padX, right, hasChips, isFirst = true)

        val remaining = h - top - (firstTop + firstHeight)
        val maxRows = max(1, (remaining / MIN_LIST_ROW).toInt())
        val rowHeight = min(remaining / maxRows, MAX_LIST_ROW)
        model.passages.drop(1).take(maxRows).forEachIndexed { index, passage ->
            val rowTop = firstTop + firstHeight + index * rowHeight
            rect(padX, rowTop, right, rowTop + 1f, p.hair)
            listRow(passage, rowTop + 1f, rowHeight - 1f, padX, right, hasChips, isFirst = false)
        }
    }

    private fun listRow(
        passage: Passage,
        top: Float,
        height: Float,
        left: Float,
        right: Float,
        hasChips: Boolean,
        isFirst: Boolean,
    ) {
        val cy = top + height / 2f
        val cancelled = passage.kind == WaitKind.CANCELLED
        val spec = if (isFirst) {
            HeroSpec(number = 46f, unit = 15f, atStop = 24f, imminent = 32f, cancelled = 17f, cancelledFace = Face.SEMI)
        } else {
            HeroSpec(number = 32f, unit = 13.5f, atStop = 20f, imminent = 24f, cancelled = 16f, cancelledFace = Face.SEMI)
        }
        val hero = hero(passage, spec, 120f, model.stale)
        val heroWidth = heroWidth(hero)
        drawHero(hero, right - heroWidth, centeredBaseline(cy, hero))

        var textX = left
        if (hasChips) {
            val mission = passage.mission
            if (mission != null) {
                roundRect(left, cy - 10f, left + 42f, cy + 10f, 6f, p.chip)
                val label = fit(mission, Face.SEMI, 13f, 38f, spacing = 0.05f)
                val labelWidth = measure(label, Face.SEMI, 13f, 0.05f)
                text(
                    label, left + (42f - labelWidth) / 2f, cy + 4.5f, Face.SEMI, 13f,
                    if (cancelled) p.text3 else p.text, spacing = 0.05f, strike = cancelled,
                )
            }
            textX = left + 52f
        }
        val textWidth = right - heroWidth - 10f - textX
        val nameSize = if (isFirst) 16.5f else 15.5f
        val nameFace = if (isFirst) Face.SEMI else Face.MEDIUM
        val detailSize = if (isFirst) 13.5f else 13f
        val detailColor = if (isFirst) p.text2 else p.text3
        val nameBase = cy - 1.5f
        val name = fit(passage.destination, nameFace, nameSize, textWidth)
        text(name, textX, nameBase, nameFace, nameSize, if (cancelled) p.text2 else p.text, strike = cancelled)
        details(passage, textX, nameBase + 15.5f, textWidth, detailSize, detailColor)
    }

    /** Ligne de détails : heure, retard en couleur, voie. */
    private fun details(passage: Passage, x: Float, base: Float, width: Float, size: Float, color: Int) {
        val delay = if (passage.delayMinutes > 0 && passage.kind != WaitKind.CANCELLED) {
            "retard +${passage.delayMinutes} min"
        } else {
            null
        }
        val platform = passage.platform?.let { "${passage.platformLabel} $it" }
        var cursor = x
        val end = x + width
        val separator = " · "
        fun run(value: String, face: Face, runColor: Int) {
            val available = end - cursor
            if (available <= 0f) return
            cursor += text(fit(value, face, size, available), cursor, base, face, size, runColor)
        }
        run(passage.clock, Face.REGULAR, color)
        if (delay != null) {
            run(separator, Face.REGULAR, color)
            run(delay, Face.SEMI, if (passage.isDelayed) p.late else color)
        }
        if (platform != null) {
            run(separator, Face.REGULAR, color)
            run(platform, Face.REGULAR, color)
        }
    }

    // --- Messages sans passages ----------------------------------------------------------------

    private fun noticeView(notice: Notice) {
        val small = model.format == WidgetFormat.PASTILLE
        val pad = if (small) 10f else 12f
        val iconHeight = if (small) 22f else 26f
        val cy = (if (small) 8f else 9f) + iconHeight / 2f
        if (notice.kind == NoticeKind.LOADING) {
            skeleton(pad, cy, iconHeight, small)
            return
        }
        val iconWidth = when (notice.kind) {
            // Hors réseau, la pastille serait celle d'une ligne d'une autre ville.
            NoticeKind.NOT_CONFIGURED, NoticeKind.NO_NETWORK -> {
                stroke.color = p.text3
                stroke.strokeWidth = 1.5f
                stroke.pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
                canvas.drawCircle(pad + iconHeight / 2f, cy, iconHeight / 2f - 1f, stroke)
                stroke.pathEffect = null
                iconHeight
            }
            NoticeKind.RECONFIGURE -> {
                circle(pad + iconHeight / 2f, cy, iconHeight / 2f, p.surface)
                key(pad + (iconHeight - 15f) / 2f, cy, 15f, p.late)
                iconHeight
            }
            else -> badge(pad, cy, iconHeight)
        }

        val title = when {
            small -> null
            notice.kind == NoticeKind.NOT_CONFIGURED -> context.getString(R.string.widget_not_configured)
            notice.kind == NoticeKind.RECONFIGURE -> context.getString(R.string.widget_reconfigure_title)
            notice.kind == NoticeKind.NO_NETWORK -> context.getString(R.string.widget_no_network_title)
            else -> model.stopName
        }
        if (title != null) {
            val titleX = pad + iconWidth + 8f
            text(fit(title, Face.SEMI, 14f, w - pad - titleX), titleX, cy + 4.9f, Face.SEMI, 14f, p.text)
        }

        val body = when (notice.kind) {
            NoticeKind.NOT_CONFIGURED -> if (small) R.string.widget_short_not_configured else R.string.widget_tap_to_configure
            NoticeKind.RECONFIGURE -> if (small) R.string.widget_short_reconfigure else R.string.widget_reconfigure_body
            NoticeKind.NO_NETWORK -> if (small) R.string.widget_short_no_network else R.string.widget_no_network_body
            NoticeKind.NO_DEPARTURE -> when {
                small -> R.string.widget_short_no_departure
                // La raison est connue (ligne sans temps réel) : elle remplace le message habituel.
                notice.detail != null -> null
                else -> R.string.widget_no_departure
            }
            else -> if (small) R.string.widget_short_error else null
        }?.let { context.getString(it) } ?: notice.detail.orEmpty()
        val size = if (small) 12.5f else 14.5f
        val lineHeight = if (small) 15f else 18f
        val bodyTop = cy + iconHeight / 2f + (if (small) 5f else 8f)
        val maxLines = max(1, ((h - pad - bodyTop) / lineHeight).toInt())
        val color = if (notice.kind == NoticeKind.ERROR) p.late else p.text2
        multiline(body, pad, bodyTop, w - 2f * pad, lineHeight, size, Face.MEDIUM, color, maxLines)
    }

    private fun skeleton(pad: Float, cy: Float, iconHeight: Float, small: Boolean) {
        circle(pad + iconHeight / 2f, cy, iconHeight / 2f, p.chip)
        val barX = pad + iconHeight + 8f
        val barEnd = w - pad
        if (!small) {
            roundRect(barX, cy - 9f, min(barX + 96f, barEnd), cy + 1f, 5f, p.chip)
            roundRect(barX, cy + 4f, min(barX + 72f, barEnd), cy + 13f, 5f, p.chip)
        }
        val bottom = h - pad
        roundRect(pad, bottom - 26f, pad + 54f, bottom, 8f, p.chip)
        if (!small) {
            textRight(context.getString(R.string.widget_loading), barEnd, bottom - 8f, Face.MEDIUM, 13.5f, p.text2)
        }
    }

    // --- Éléments communs ----------------------------------------------------------------------

    private fun header(
        left: Float,
        right: Float,
        top: Float,
        rowHeight: Float,
        badgeHeight: Float,
        nameSize: Float,
        timeSize: Float,
        gap: Float,
        tag: String? = null,
    ) {
        val cy = top + rowHeight / 2f
        val badgeWidth = badge(left, cy, badgeHeight)
        val textX = left + badgeWidth + gap
        val noteWidth = if (tag != null) {
            lateTag(tag, right, cy + 0.35f * timeSize, timeSize)
        } else {
            timeLabel(
                right, cy + 0.35f * timeSize, timeSize,
                stacked = right - left < WIDE_HEADER,
                room = right - textX - measure(model.stopName, Face.SEMI, nameSize) - 8f,
            )
        }
        val stop = fit(model.stopName, Face.SEMI, nameSize, right - textX - noteWidth - 8f)
        text(stop, textX, cy + 0.35f * nameSize, Face.SEMI, nameSize, p.text)
    }

    /** Le message du mode automatique ressort davantage que l'heure, qu'il remplace brièvement. */
    private fun timeColor(): Int = when {
        model.autoHint != null -> p.text
        timeAlert -> p.late
        else -> p.text3
    }

    /** L'heure, précédée de la source s'il faut la citer : « Tracéo 22:34 ». */
    /** Source citée devant l'heure : le relevé de Tracéo, ou PRIM en direct avec une clé. */
    private val shownSource: String? get() = model.sourceLabel ?: model.liveSource

    private fun sourcedTime(source: String? = shownSource): String =
        listOfNotNull(source, model.timeLabel.takeIf { it.isNotEmpty() }).joinToString(" ")

    /**
     * Heure de mise à jour ou du relevé, avec la source s'il faut la citer. Elle passe en couleur si
     * l'actualisation a échoué ou si le relevé est ancien, avec dans ce cas un triangle d'alerte.
     *
     * @param stacked source et heure sur deux lignes, pour laisser sa largeur au nom de l'arrêt
     * @param room largeur disponible sans rogner le nom de l'arrêt : si elle suffit, la mention complète
     *             « PRIM via Tracéo 22:34 » remplace « Tracéo 22:34 »
     */
    private fun timeLabel(right: Float, base: Float, size: Float, stacked: Boolean = false, room: Float = 0f): Float {
        val source = shownSource
        if (source == null && model.timeLabel.isEmpty()) return 0f
        val face = if (timeAlert || model.autoHint != null) Face.SEMI else Face.REGULAR
        val color = timeColor()
        val showsWarning = model.oldSnapshot && !model.stale
        val gap = 2f
        val warningWidth = if (showsWarning) gap + size else 0f
        var width = if (source != null && stacked) {
            max(
                textRight(source, right, base - size * 0.95f, face, size * 0.85f, color),
                textRight(model.timeLabel, right, base + size * 0.15f, face, size, color),
            )
        } else {
            val detailed = sourcedTime(model.sourceDetail ?: model.liveSource)
            val note = if (measure(detailed, face, size) + warningWidth <= room) detailed else sourcedTime()
            textRight(note, right, base, face, size, color)
        }
        if (showsWarning) {
            warning(right - width - gap - size, base - 0.35f * size, size, p.late)
            width += warningWidth
        }
        return width
    }

    /**
     * Pour les petits formats : la mention la plus complète qui tient dans [room], sinon l'heure seule,
     * sinon rien. Un relevé ancien est précédé d'un triangle d'alerte s'il reste la place.
     */
    private fun snapshotNote(right: Float, base: Float, size: Float, room: Float) {
        val face = if (model.oldSnapshot) Face.SEMI else Face.MEDIUM
        val color = if (model.oldSnapshot) p.late else p.text2
        val note = listOf(sourcedTime(model.sourceDetail), sourcedTime(), model.timeLabel)
            .firstOrNull { measure(it, face, size) <= room } ?: return
        val width = textRight(note, right, base, face, size, color)
        val gap = 2f
        if (model.oldSnapshot && width + gap + size <= room) {
            warning(right - width - gap - size, base - 0.35f * size, size, p.late)
        }
    }

    private fun destination(
        label: String,
        x: Float,
        top: Float,
        lineHeight: Float,
        size: Float,
        arrowSize: Float,
        gap: Float,
        color: Int,
        maxWidth: Float,
        strike: Boolean = false,
    ) {
        arrow(x, top + lineHeight / 2f, arrowSize, color)
        val textX = x + arrowSize + gap
        val fitted = fit(label, Face.MEDIUM, size, maxWidth - arrowSize - gap)
        text(fitted, textX, baseline(top, lineHeight, size), Face.MEDIUM, size, color, strike = strike)
    }

    private fun hero(passage: Passage, spec: HeroSpec, maxWidth: Float, muted: Boolean): Hero {
        val main = when {
            muted -> p.muted
            passage.isDelayed && !p.isLine -> p.late
            else -> p.text
        }
        val number = max(0, passage.minutes).toString()
        val built = when (passage.kind) {
            WaitKind.MINUTES -> Hero(number, Face.COND_BOLD, spec.number, main, "min", spec.unit, spec.unitGap, false, false)
            WaitKind.CLOCK -> Hero(passage.clock, Face.COND_BOLD, spec.number * 0.62f, main, "", spec.unit, spec.unitGap, false, false)
            WaitKind.IMMINENT -> if (spec.imminentAsNumber) {
                Hero("<1", Face.COND_BOLD, spec.number, main, "min", spec.unit, spec.unitGap, false, false)
            } else {
                Hero("Imminent", Face.COND_BOLD, spec.imminent, main, "", spec.unit, spec.unitGap, false, false)
            }
            WaitKind.AT_STOP -> Hero(
                passage.atStopLabel, Face.COND_BOLD, spec.atStop, main, "", spec.unit, spec.unitGap,
                spec.pillAtStop && !p.isLine && !muted, false,
            )
            WaitKind.CANCELLED -> if (spec.cancelledAsNumber) {
                Hero(number, Face.COND_BOLD, spec.number, main, "min", spec.unit, spec.unitGap, false, true)
            } else {
                Hero(
                    "Supprimé", spec.cancelledFace, spec.cancelled, if (muted) p.muted else p.cancelled,
                    "", spec.unit, spec.unitGap, false, false,
                )
            }
        }
        val width = heroWidth(built)
        if (width <= maxWidth || maxWidth <= 0f) return built
        val ratio = max(0.5f, maxWidth / width)
        return Hero(
            built.text, built.face, built.size * ratio, built.color, built.unit, built.unitSize * ratio,
            built.unitGap, built.pill, built.strike,
        )
    }

    private fun heroWidth(hero: Hero): Float {
        var width = measure(hero.text, hero.face, hero.size)
        if (hero.pill) width += 2f * pillPadding(hero)
        if (hero.unit.isNotEmpty()) width += hero.unitGap + measure(hero.unit, Face.SEMI, hero.unitSize)
        return width
    }

    private fun pillPadding(hero: Hero): Float = hero.size * 0.36f

    private fun drawHero(hero: Hero, x: Float, base: Float): Float {
        var cursor = x
        if (hero.pill) {
            val padding = pillPadding(hero)
            val textWidth = measure(hero.text, hero.face, hero.size)
            val center = base - 0.35f * hero.size
            val half = hero.size * 0.66f
            roundRect(cursor, center - half, cursor + textWidth + 2f * padding, center + half, hero.size * 0.38f, p.tileBg)
            text(hero.text, cursor + padding, base, hero.face, hero.size, p.tileFg)
            cursor += textWidth + 2f * padding
        } else {
            cursor += text(hero.text, cursor, base, hero.face, hero.size, hero.color, strike = hero.strike)
        }
        if (hero.unit.isNotEmpty()) {
            cursor += hero.unitGap
            cursor += text(hero.unit, cursor, base, Face.SEMI, hero.unitSize, hero.color)
        }
        return cursor - x
    }

    /** Ligne de base d'un texte centré verticalement sur [cy]. */
    private fun centeredBaseline(cy: Float, hero: Hero): Float = cy + 0.35f * hero.size

    /** Frise d'un bloc : le trait de la ligne et un point par passage à venir. */
    private fun rail(x: Float, width: Float, top: Float, stops: List<Passage>, span: Int, labelLineHeight: Float) {
        roundRect(x, top + 4f, x + width, top + 8f, 2f, p.rail)
        val minutes = stops.map { it.railMinutes }
        val centers = WidgetContent.railCenters(minutes, x + 16f, x + width - 9f, span, 22f)
        val nodeY = top + 6f
        centers.forEachIndexed { index, center ->
            val radius = if (index == 0) 6f else 5f
            circle(center, nodeY, radius + 2f, p.background)
            circle(center, nodeY, radius, p.node)
            if (index > 0) {
                val label = stops[index].shortWait
                val labelWidth = measure(label, Face.COND_SEMI, 20f)
                val labelX = min(center - labelWidth / 2f, x + width - labelWidth)
                text(label, labelX, baseline(top + 12f, labelLineHeight, 20f), Face.COND_SEMI, 20f, p.text2)
            }
        }
    }

    /** Étiquette de mission, dessinée seulement si elle tient avant [limit]. */
    private fun chip(label: String, x: Float, base: Float, limit: Float) {
        val size = 12f
        val spacing = 0.06f
        val textWidth = measure(label, Face.SEMI, size, spacing)
        val width = textWidth + 10f
        if (x + width > limit) return
        val center = base - 0.35f * size
        roundRect(x, center - 9f, x + width, center + 9f, 5f, p.chip)
        text(label, x + 5f, base, Face.SEMI, size, p.text2, spacing = spacing)
    }

    private fun lateTagWidth(label: String, size: Float): Float {
        val textWidth = measure(label, Face.SEMI, size)
        return if (p.isLine) textWidth + 12f else textWidth
    }

    /** Retard : texte en couleur sur fond neutre, étiquette inversée sur la couleur de ligne. */
    private fun lateTag(label: String, right: Float, base: Float, size: Float): Float {
        if (!p.isLine) return textRight(label, right, base, Face.SEMI, size, p.late)
        val width = lateTagWidth(label, size)
        val center = base - 0.35f * size
        val half = size * 0.7f
        roundRect(right - width, center - half, right, center + half, 6f, p.tagBg)
        text(label, right - width + 6f, base, Face.SEMI, size, p.tagFg)
        return width
    }

    private fun badgeWidth(height: Float, line: LineBadge? = model.line): Float {
        line ?: return 0f
        val textWidth = measure(line.label, Face.COND_BOLD, badgeTextSize(line, height))
        return when (line.shape) {
            BadgeShape.CIRCLE, BadgeShape.SQUARE -> max(height, textWidth + height * 0.45f)
            BadgeShape.BUS -> max(height * 1.4f, textWidth + height * 0.5f)
            BadgeShape.TRAM -> textWidth + 4f
        }
    }

    private fun badgeTextSize(line: LineBadge, height: Float): Float =
        height * if (line.shape == BadgeShape.TRAM) 0.66f else 0.7f

    /** Pastille de ligne à partir de [x], centrée sur [cy]. Renvoie sa largeur. */
    private fun badge(
        x: Float,
        cy: Float,
        height: Float,
        line: LineBadge? = model.line,
        background: Int = p.badgeBg,
        foreground: Int = p.badgeFg,
        rail: Int = p.rail,
    ): Float {
        line ?: return 0f
        val width = badgeWidth(height, line)
        val half = height / 2f
        when (line.shape) {
            BadgeShape.CIRCLE -> roundRect(x, cy - half, x + width, cy + half, half, background)
            BadgeShape.SQUARE -> roundRect(x, cy - half, x + width, cy + half, height * 0.25f, background)
            BadgeShape.BUS -> {
                val busHalf = height * 0.44f
                roundRect(x, cy - busHalf, x + width, cy + busHalf, height * 0.2f, background)
            }
            BadgeShape.TRAM -> {
                rect(x, cy - half, x + width, cy - half + 3f, rail)
                rect(x, cy + half - 3f, x + width, cy + half, rail)
            }
        }
        val size = badgeTextSize(line, height)
        val textWidth = measure(line.label, Face.COND_BOLD, size)
        val color = if (line.shape == BadgeShape.TRAM) p.text else foreground
        text(line.label, x + (width - textWidth) / 2f, cy + 0.35f * size, Face.COND_BOLD, size, color)
        return width
    }

    private fun arrow(x: Float, cy: Float, size: Float, color: Int) {
        val unit = size / 12f
        stroke.color = color
        stroke.strokeWidth = 1.6f * unit
        path.reset()
        path.moveTo(x + 1.5f * unit, cy)
        path.lineTo(x + 10f * unit, cy)
        path.moveTo(x + 6.5f * unit, cy - 3.5f * unit)
        path.lineTo(x + 10f * unit, cy)
        path.lineTo(x + 6.5f * unit, cy + 3.5f * unit)
        canvas.drawPath(path, stroke)
    }

    private fun warning(x: Float, cy: Float, size: Float, color: Int) {
        val unit = size / 16f
        val top = cy - 8f * unit
        stroke.color = color
        stroke.strokeWidth = 1.7f * unit
        path.reset()
        path.moveTo(x + 8f * unit, top + 2f * unit)
        path.lineTo(x + 15f * unit, top + 14f * unit)
        path.lineTo(x + 1f * unit, top + 14f * unit)
        path.close()
        path.moveTo(x + 8f * unit, top + 6.5f * unit)
        path.lineTo(x + 8f * unit, top + 9.7f * unit)
        path.moveTo(x + 8f * unit, top + 11.8f * unit)
        path.lineTo(x + 8f * unit, top + 12f * unit)
        canvas.drawPath(path, stroke)
    }

    private fun key(x: Float, cy: Float, size: Float, color: Int) {
        val unit = size / 16f
        val top = cy - 8f * unit
        stroke.color = color
        stroke.strokeWidth = 1.6f * unit
        canvas.drawCircle(x + 5.5f * unit, top + 10.5f * unit, 3f * unit, stroke)
        path.reset()
        path.moveTo(x + 7.8f * unit, top + 8.2f * unit)
        path.lineTo(x + 13.5f * unit, top + 2.5f * unit)
        path.moveTo(x + 11f * unit, top + 5f * unit)
        path.lineTo(x + 13f * unit, top + 7f * unit)
        canvas.drawPath(path, stroke)
    }

    private fun multiline(
        value: String,
        x: Float,
        top: Float,
        width: Float,
        lineHeight: Float,
        size: Float,
        face: Face,
        color: Int,
        maxLines: Int,
    ) {
        val lines = WidgetContent.wrapLines(value, width, maxLines) { measure(it, face, size) }
        lines.forEachIndexed { index, line ->
            text(line, x, baseline(top + index * lineHeight, lineHeight, size), face, size, color)
        }
    }

    // --- Primitives ----------------------------------------------------------------------------

    /** Ligne de base d'un texte dans une boîte, avec les métriques de Barlow (comme en CSS). */
    private fun baseline(top: Float, lineHeight: Float, size: Float): Float = top + lineHeight / 2f + 0.4f * size

    private fun prepare(face: Face, size: Float, color: Int, spacing: Float, strike: Boolean) {
        textPaint.typeface = fonts[face]
        textPaint.textSize = size
        textPaint.color = color
        textPaint.letterSpacing = spacing
        textPaint.isStrikeThruText = strike
    }

    private fun measure(value: String, face: Face, size: Float, spacing: Float = 0f): Float {
        prepare(face, size, 0, spacing, false)
        return textPaint.measureText(value)
    }

    private fun text(
        value: String,
        x: Float,
        base: Float,
        face: Face,
        size: Float,
        color: Int,
        spacing: Float = 0f,
        strike: Boolean = false,
    ): Float {
        prepare(face, size, color, spacing, strike)
        canvas.drawText(value, x, base, textPaint)
        return textPaint.measureText(value)
    }

    private fun textRight(value: String, right: Float, base: Float, face: Face, size: Float, color: Int): Float {
        val width = measure(value, face, size)
        text(value, right - width, base, face, size, color)
        return width
    }

    private fun fit(value: String, face: Face, size: Float, maxWidth: Float, spacing: Float = 0f): String =
        WidgetContent.ellipsize(value, maxWidth) { measure(it, face, size, spacing) }

    private fun fittedSize(value: String, face: Face, size: Float, maxWidth: Float): Float {
        val width = measure(value, face, size)
        return if (width <= maxWidth || width <= 0f) size else size * maxWidth / width
    }

    private fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        fill.color = color
        canvas.drawRect(left, top, right, bottom, fill)
    }

    private fun roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        fill.color = color
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, fill)
    }

    private fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(cx, cy, radius, fill)
    }

    private companion object {
        const val MIN_LIST_ROW = 40f
        const val MAX_LIST_ROW = 48f

        /** Largeur d'en-tête à partir de laquelle « Tracéo 22:34 » tient sur une ligne. */
        const val WIDE_HEADER = 250f

        const val PROXIMITY_BADGE = 26f
        const val PROXIMITY_PLACE = 15f
        const val PROXIMITY_DIRECTION = 24f

        /** Largeur de la colonne de l'attente. */
        const val PROXIMITY_TIME = 54f

        /** Largeur de la colonne du passage suivant, espace compris. */
        const val PROXIMITY_THEN = 46f

        /** Une ligne seule ne doit pas s'étirer sur toute la hauteur du widget. */
        const val MAX_PROXIMITY_BLOCK = 96f
    }
}
