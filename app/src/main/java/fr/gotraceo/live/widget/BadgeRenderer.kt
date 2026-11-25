package fr.gotraceo.live.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import fr.gotraceo.live.R
import fr.gotraceo.live.data.Line
import fr.gotraceo.live.data.Networks
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dessine la pastille (couleur officielle + indice) d'une ligne. Le rond du métro et du RER et les
 * filets du tramway sont le code de formes de l'Île-de-France : ailleurs, la pastille est la même
 * pour tous les modes, à la couleur que le réseau donne à sa ligne, ou à celle de son mode s'il n'en
 * donne pas. Les couleurs sont celles des widgets ([Palettes.lineColors]).
 */
object BadgeRenderer {

    fun render(context: Context, line: Line, heightDp: Float = 28f): Bitmap {
        val density = context.resources.displayMetrics.density
        val h = max(1, (heightDp * density).roundToInt())
        val idfm = Networks.isIdfm(line.networkId)
        // En Île-de-France, rien ne change ; ailleurs, un code trop long est coupé de façon visible.
        val (label, textScale) = BadgeLabels.forList(line)

        val (bg, fg) = Palettes.lineColors(line)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            typeface = labelFace(context)
            textAlign = Paint.Align.CENTER
            textSize = h * textScale
        }
        val textWidth = textPaint.measureText(label)
        val w = max(h, (textWidth + h * 0.6f).roundToInt())

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        val rounded = idfm && (line.mode == "metro" || line.isRer())
        val radius = if (rounded) h / 2f else h * 0.22f
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, bgPaint)

        if (idfm && line.mode == "tram") {
            // Rappel du pictogramme tramway : deux filets horizontaux.
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; strokeWidth = h * 0.06f }
            val inset = h * 0.14f
            canvas.drawLine(inset, h * 0.12f, w - inset, h * 0.12f, bar)
            canvas.drawLine(inset, h * 0.88f, w - inset, h * 0.88f, bar)
        }

        val y = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, w / 2f, y, textPaint)
        return bmp
    }

    private fun labelFace(context: Context): Typeface = try {
        context.resources.getFont(R.font.barlow_condensed_bold)
    } catch (e: Resources.NotFoundException) {
        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

}
