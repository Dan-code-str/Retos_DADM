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
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Gráfico de barras verticales de la evolución trimestre a trimestre.
 * Las barras crecen en cascada al aparecer; tocar una barra la selecciona.
 */
class EvolucionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    class Punto(val etiqueta: String, val anno: Int, val valor: Double, val textoValor: String)

    var alSeleccionar: ((Int) -> Unit)? = null

    private var puntos: List<Punto> = emptyList()
    private var seleccionado = -1
    private var tiempo = 1f
    private var animador: ValueAnimator? = null

    private val fuente = Estilo.fuente(context)
    private val barra = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brillo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pista = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Estilo.PISTA }
    private val textoValor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fuente
        textAlign = Paint.Align.CENTER
        textSize = Estilo.dp(context, 10f)
        color = Estilo.TEXTO_SUAVE
    }
    private val textoEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fuente
        textAlign = Paint.Align.CENTER
        textSize = Estilo.dp(context, 11f)
        color = Estilo.TEXTO_SUAVE
    }
    private val textoAnno = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fuente
        textAlign = Paint.Align.LEFT
        textSize = Estilo.dp(context, 11f)
        color = Estilo.TEXTO
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val desenfoqueSuave = BlurMaskFilter(Estilo.dp(context, 6f), BlurMaskFilter.Blur.NORMAL)
    private val desenfoqueFuerte = BlurMaskFilter(Estilo.dp(context, 12f), BlurMaskFilter.Blur.NORMAL)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setPuntos(lista: List<Punto>, seleccion: Int, animar: Boolean = true) {
        puntos = lista
        seleccionado = seleccion
        animador?.cancel()
        if (!animar) {
            tiempo = 1f
            invalidate()
            return
        }
        tiempo = 0f
        animador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700L + 70L * lista.size
            interpolator = LinearInterpolator()
            addUpdateListener {
                tiempo = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Progreso de la barra [i] (cascada de izquierda a derecha, con desaceleración). */
    private fun progresoBarra(i: Int): Float {
        val n = puntos.size.coerceAtLeast(1)
        val inicio = i.toFloat() / (n + 4)
        val fin = inicio + 5f / (n + 4)
        val t = ((tiempo - inicio) / (fin - inicio)).coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t) * (1f - t)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (puntos.isEmpty()) return
        val arriba = Estilo.dp(context, 22f)
        val abajo = height - Estilo.dp(context, 40f)
        val alturaUtil = abajo - arriba
        val ranura = width.toFloat() / puntos.size
        val anchoBarra = min(ranura * 0.62f, Estilo.dp(context, 30f))
        val radio = anchoBarra / 2.5f
        val maximo = puntos.maxOf { it.valor }.takeIf { it > 0 } ?: 1.0
        val mostrarTodos = ranura >= Estilo.dp(context, 34f)

        var annoAnterior = Int.MIN_VALUE
        for ((i, p) in puntos.withIndex()) {
            val centro = ranura * (i + 0.5f)
            val izquierda = centro - anchoBarra / 2
            val esSeleccion = i == seleccionado

            // Pista tenue a toda altura
            rect.set(izquierda, arriba, izquierda + anchoBarra, abajo)
            canvas.drawRoundRect(rect, radio, radio, pista)

            val alto = (alturaUtil * (p.valor / maximo).toFloat() * progresoBarra(i)).coerceAtLeast(0f)
            if (alto > 0f) {
                rect.set(izquierda, abajo - alto, izquierda + anchoBarra, abajo)
                val degradado = LinearGradient(0f, abajo, 0f, abajo - alto, Estilo.AGUAMARINA, Estilo.LIMA, Shader.TileMode.CLAMP)
                brillo.shader = degradado
                brillo.maskFilter = if (esSeleccion) desenfoqueFuerte else desenfoqueSuave
                brillo.alpha = if (esSeleccion) 230 else 90
                canvas.drawRoundRect(rect, radio, radio, brillo)
                barra.shader = degradado
                barra.alpha = if (seleccionado < 0 || esSeleccion) 255 else 150
                canvas.drawRoundRect(rect, radio, radio, barra)
            }

            if (mostrarTodos || esSeleccion) {
                textoValor.color = if (esSeleccion) Estilo.LIMA else Estilo.TEXTO_SUAVE
                textoValor.isFakeBoldText = esSeleccion
                textoValor.alpha = (255 * progresoBarra(i)).toInt()
                canvas.drawText(p.textoValor, centro, abajo - alto - Estilo.dp(context, 6f), textoValor)
            }

            textoEtiqueta.color = if (esSeleccion) Estilo.LIMA else Estilo.TEXTO_SUAVE
            canvas.drawText(p.etiqueta, centro, abajo + Estilo.dp(context, 15f), textoEtiqueta)
            if (p.anno != annoAnterior) {
                canvas.drawText(p.anno.toString(), izquierda, abajo + Estilo.dp(context, 32f), textoAnno)
                annoAnterior = p.anno
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (puntos.isEmpty()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val indice = (event.x / (width.toFloat() / puntos.size)).toInt().coerceIn(0, puntos.size - 1)
                seleccionado = indice
                invalidate()
                alSeleccionar?.invoke(indice)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        animador?.cancel()
        super.onDetachedFromWindow()
    }
}
