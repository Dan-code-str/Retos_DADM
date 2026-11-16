package co.edu.unal.reto10.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Barra horizontal con gradiente aguamarina → lima, resplandor y animación de llenado. */
class BarraGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var fraccion = 0f
    private var progresoAnimacion = 1f
    private var animador: ValueAnimator? = null

    private val alto = Estilo.dp(context, 8f)
    private val radio = alto / 2f
    private val pista = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Estilo.PISTA }
    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brillo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(Estilo.dp(context, 6f), BlurMaskFilter.Blur.NORMAL)
        alpha = 170
    }
    private val rect = RectF()

    init {
        // BlurMaskFilter solo se dibuja con capa por software.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** [valor] entre 0 y 1. Con [retraso] >= 0 la barra se llena con animación. */
    fun mostrar(valor: Float, retraso: Long = -1) {
        fraccion = valor.coerceIn(0f, 1f)
        animador?.cancel()
        if (retraso < 0) {
            progresoAnimacion = 1f
            invalidate()
            return
        }
        progresoAnimacion = 0f
        animador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750
            startDelay = retraso
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                progresoAnimacion = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gradiente = LinearGradient(0f, 0f, w.toFloat(), 0f, Estilo.AGUAMARINA, Estilo.LIMA, Shader.TileMode.CLAMP)
        relleno.shader = gradiente
        brillo.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Estilo.AGUAMARINA, Estilo.LIMA, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val margen = Estilo.dp(context, 6f)
        val arriba = (height - alto) / 2f
        val ancho = width - 2 * margen
        rect.set(margen, arriba, margen + ancho, arriba + alto)
        canvas.drawRoundRect(rect, radio, radio, pista)

        val lleno = ancho * fraccion * progresoAnimacion
        if (lleno <= 0f) return
        rect.set(margen, arriba, margen + maxOf(lleno, alto), arriba + alto)
        canvas.drawRoundRect(rect, radio, radio, brillo)
        canvas.drawRoundRect(rect, radio, radio, relleno)
    }

    override fun onDetachedFromWindow() {
        animador?.cancel()
        super.onDetachedFromWindow()
    }
}
