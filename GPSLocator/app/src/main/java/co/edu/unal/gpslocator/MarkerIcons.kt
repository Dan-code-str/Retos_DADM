package co.edu.unal.gpslocator

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.TypedValue
import kotlin.math.ceil
import kotlin.math.max

/** Dibuja marcadores con un círculo de color y el nombre del lugar siempre visible debajo. */
object MarkerIcons {

    class Icon(val drawable: BitmapDrawable, val anchorV: Float)

    private const val MAX_LABEL_CHARS = 28

    fun labeled(res: Resources, label: String, pinColor: Int, pinRadiusDp: Float = 8f): Icon {
        val dm = res.displayMetrics
        val density = dm.density
        val radius = pinRadiusDp * density
        val border = 2.5f * density
        val gap = 2f * density
        val pad = 4f * density

        val text = if (label.length > MAX_LABEL_CHARS) label.take(MAX_LABEL_CHARS - 1) + "…" else label

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, dm)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            color = 0xFF212121.toInt()
        }
        val haloPaint = Paint(textPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeJoin = Paint.Join.ROUND
            color = 0xFFFFFFFF.toInt()
        }

        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val pinSize = 2 * (radius + border)
        val width = ceil(max(pinSize, textPaint.measureText(text) + 2 * pad)).toInt()
        val height = ceil(pinSize + gap + textHeight + pad).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = radius + border

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, radius + border, fill)
        fill.color = pinColor
        canvas.drawCircle(cx, cy, radius, fill)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, radius * 0.35f, fill)

        val baseline = pinSize + gap - fm.ascent
        canvas.drawText(text, cx, baseline, haloPaint)
        canvas.drawText(text, cx, baseline, textPaint)

        return Icon(BitmapDrawable(res, bitmap), cy / height)
    }
}
