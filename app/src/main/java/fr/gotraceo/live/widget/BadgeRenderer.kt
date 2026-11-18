package fr.gotraceo.live.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * pour tous les modes, à la couleur que le réseau donne à sa ligne.
 */
object BadgeRenderer {

    fun render(context: Context, line: Line, heightDp: Float = 28f): Bitmap {
        val density = context.resources.displayMetrics.density
        val h = max(1, (heightDp * density).roundToInt())
        val label = line.shortName.trim().ifEmpty { line.name.trim() }.take(7)

        val bg = parseColor(line.color) ?: defaultColor(line.mode)
        val fg = parseColor(line.textColor) ?: if (luminance(bg) > 0.6) Color.BLACK else Color.WHITE

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            typeface = labelFace(context)
            textAlign = Paint.Align.CENTER
            textSize = h * if (label.length <= 3) 0.64f else 0.54f
        }
        val textWidth = textPaint.measureText(label)
        val w = max(h, (textWidth + h * 0.6f).roundToInt())

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        val idfm = Networks.isIdfm(line.networkId)
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

    fun parseColor(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return try {
            Color.parseColor("#" + hex.trim().removePrefix("#"))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun defaultColor(mode: String): Int = when (mode) {
        "metro" -> Color.parseColor("#003CA6")
        "rail" -> Color.parseColor("#7A2E8C")
        "tram" -> Color.parseColor("#0B7A75")
        "bus" -> Color.parseColor("#2E7D32")
        else -> Color.parseColor("#546E7A")
    }

    private fun luminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
