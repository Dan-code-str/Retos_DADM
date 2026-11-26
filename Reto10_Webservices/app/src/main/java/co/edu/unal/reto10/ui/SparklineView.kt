package co.edu.unal.reto10.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Mini gráfico de línea con resplandor; marca el periodo actual con un punto brillante. */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var valores: List<Double?> = emptyList()
    private var actual = -1

    private val linea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Estilo.dp(context, 2.2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Estilo.dp(context, 7f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        alpha = 55
    }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val punto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Estilo.LIMA }
    private val puntoHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Estilo.LIMA; alpha = 70 }

    fun setValores(lista: List<Double?>, indiceActual: Int) {
        valores = lista
        actual = indiceActual
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gradiente = LinearGradient(0f, 0f, w.toFloat(), 0f, Estilo.AGUAMARINA, Estilo.LIMA, Shader.TileMode.CLAMP)
        linea.shader = gradiente
        halo.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Estilo.AGUAMARINA, Estilo.LIMA, Shader.TileMode.CLAMP)
        area.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            (Estilo.AGUAMARINA and 0x00FFFFFF) or (0x55 shl 24), Estilo.AGUAMARINA and 0x00FFFFFF,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val conDato = valores.withIndex().filter { it.value != null }
        if (conDato.size < 2) return
        val minimo = conDato.minOf { it.value!! }
        val maximo = conDato.maxOf { it.value!! }
        val rango = (maximo - minimo).takeIf { it > 0 } ?: 1.0
        val margen = Estilo.dp(context, 6f)
        val ancho = width - 2 * margen
        val alto = height - 2 * margen
        fun x(i: Int) = margen + ancho * i / (valores.size - 1).coerceAtLeast(1)
        fun y(v: Double) = (margen + alto * (1 - (v - minimo) / rango)).toFloat()

        val camino = Path()
        conDato.forEachIndexed { k, (i, v) ->
            if (k == 0) camino.moveTo(x(i), y(v!!)) else camino.lineTo(x(i), y(v!!))
        }
        val relleno = Path(camino).apply {
            lineTo(x(conDato.last().index), height.toFloat())
            lineTo(x(conDato.first().index), height.toFloat())
            close()
        }
        canvas.drawPath(relleno, area)
        canvas.drawPath(camino, halo)
        canvas.drawPath(camino, linea)

        valores.getOrNull(actual)?.let { v ->
            canvas.drawCircle(x(actual), y(v), Estilo.dp(context, 8f), puntoHalo)
            canvas.drawCircle(x(actual), y(v), Estilo.dp(context, 4f), punto)
        }
    }
}
