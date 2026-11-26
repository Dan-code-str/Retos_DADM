package co.edu.unal.reto10.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * Mapa coroplético dibujado con Canvas: cada zona (departamento o municipio) se pinta con el
 * color que asigne la Activity y responde al toque.
 * En el mapa nacional ([separarIslas] = true), San Andrés y Providencia van en dos recuadros
 * aparte, cada uno con su propio zoom, para que las islas se vean.
 */
class MapaColombiaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Caja(val lonMin: Double, val lonMax: Double, val latMin: Double, val latMax: Double) {
        val ancho get() = (lonMax - lonMin) * FACTOR_LON
        val alto get() = latMax - latMin
    }

    var alTocarZona: ((Int) -> Unit)? = null

    var seleccionado: Int? = null
        set(valor) {
            field = valor
            invalidate()
        }

    var separarIslas = true

    private var zonas: List<Zona> = emptyList()
    private var colores: Map<Int, Int> = emptyMap()
    private var coloresPrevios: Map<Int, Int> = emptyMap()
    private var transicion = 1f
    private var animador: ValueAnimator? = null
    private val evaluador = ArgbEvaluator()
    private var cajaPrincipal: Caja? = null
    private val caminos = LinkedHashMap<Int, Path>()
    private val regiones = HashMap<Int, Region>()
    private val recuadros = ArrayList<Pair<RectF, String>>()

    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.7f)
        color = Estilo.FONDO
        strokeJoin = Paint.Join.ROUND
    }
    /** Resplandor de la selección: varias pasadas cada vez más finas y opacas (funciona con aceleración por hardware). */
    private val pasadasBrillo = listOf(12f to 35, 7f to 70, 4f to 140, 2f to 255).map { (ancho, alfa) ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(ancho)
            color = Estilo.LIMA
            alpha = alfa
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }
    private val marcoRecuadro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.GRAY
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(3f)), 0f)
    }
    private val textoRecuadro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = dp(10f)
    }

    fun setZonas(lista: List<Zona>) {
        zonas = lista
        cajaPrincipal = calcularCaja(lista.filterNot { esArchipielago(it) }.flatMap { it.poligonos })
        requestLayout()
        construirCaminos()
        invalidate()
    }

    /** Asigna los colores; con [animar] cada zona pasa suavemente de su color anterior al nuevo. */
    fun setColores(mapa: Map<Int, Int>, animar: Boolean = true, duracion: Long = 550) {
        animador?.cancel()
        coloresPrevios = if (animar) coloresActuales() else mapa
        colores = mapa
        if (!animar) {
            transicion = 1f
            invalidate()
            return
        }
        transicion = 0f
        animador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = duracion
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                transicion = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Colores tal como se ven ahora mismo (a mitad de una transición, el color intermedio). */
    private fun coloresActuales(): Map<Int, Int> =
        zonas.associate { it.codigo to colorDe(it.codigo) }

    private fun colorDe(codigo: Int): Int {
        val destino = colores[codigo] ?: COLOR_SIN_DATO
        if (transicion >= 1f) return destino
        val origen = coloresPrevios[codigo] ?: COLOR_SIN_DATO
        return evaluador.evaluate(transicion, origen, destino) as Int
    }

    private fun esArchipielago(z: Zona) = separarIslas && z.codigo == CODIGO_SAN_ANDRES

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ancho = MeasureSpec.getSize(widthMeasureSpec)
        val caja = cajaPrincipal
        val relacion = if (caja == null || caja.ancho <= 0.0) 1.35 else (caja.alto / caja.ancho).coerceIn(0.5, 1.6)
        setMeasuredDimension(ancho, resolveSize((ancho * relacion).toInt(), heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        construirCaminos()
    }

    private fun construirCaminos() {
        caminos.clear()
        regiones.clear()
        recuadros.clear()
        val caja = cajaPrincipal ?: return
        if (width == 0 || height == 0) return

        val margen = dp(8f)
        val principal = RectF(margen, margen, width - margen, height - margen)
        val recorte = Region(0, 0, width, height)

        // Recuadros del archipiélago (arriba a la izquierda, sobre el mar Caribe).
        val anchoRecuadro = width * 0.10f
        val sanAndres = RectF(margen, margen, margen + anchoRecuadro, margen + width * 0.18f)
        val providencia = RectF(sanAndres.right + dp(6f), margen, sanAndres.right + dp(6f) + anchoRecuadro, margen + width * 0.12f)

        for (z in zonas) {
            val camino = Path()
            if (esArchipielago(z)) {
                // San Andrés (sur del paralelo 13) y Providencia + Santa Catalina (norte), cada uno con su zoom.
                val (sur, norte) = z.poligonos.partition { latitudMedia(it) < 13.0 }
                if (sur.isNotEmpty()) {
                    agregar(camino, sur, calcularCaja(sur) ?: caja, sanAndres)
                    recuadros.add(sanAndres to "San Andrés")
                }
                if (norte.isNotEmpty()) {
                    agregar(camino, norte, calcularCaja(norte) ?: caja, providencia)
                    recuadros.add(providencia to "Providencia")
                }
            } else {
                agregar(camino, z.poligonos, caja, principal)
            }
            caminos[z.codigo] = camino
            regiones[z.codigo] = Region().apply { setPath(camino, recorte) }
        }
    }

    /** Proyección equirectangular centrada en [destino]; descarta puntos a menos de ~1 px del anterior. */
    private fun agregar(camino: Path, poligonos: List<List<DoubleArray>>, origen: Caja, destino: RectF) {
        val anchoGeo = origen.ancho.coerceAtLeast(1e-9)
        val altoGeo = origen.alto.coerceAtLeast(1e-9)
        val escala = min(destino.width() / anchoGeo, destino.height() / altoGeo)
        val dx = destino.left + (destino.width() - anchoGeo * escala) / 2.0
        val dy = destino.top + (destino.height() - altoGeo * escala) / 2.0
        val distanciaMinima = dp(1f)

        for (poligono in poligonos) {
            for (anillo in poligono) {
                var ultimoX = Float.NaN
                var ultimoY = Float.NaN
                var i = 0
                while (i + 1 < anillo.size) {
                    val x = (dx + (anillo[i] - origen.lonMin) * FACTOR_LON * escala).toFloat()
                    val y = (dy + (origen.latMax - anillo[i + 1]) * escala).toFloat()
                    if (ultimoX.isNaN()) {
                        camino.moveTo(x, y)
                        ultimoX = x; ultimoY = y
                    } else if (hypot(x - ultimoX, y - ultimoY) >= distanciaMinima || i + 2 >= anillo.size) {
                        camino.lineTo(x, y)
                        ultimoX = x; ultimoY = y
                    }
                    i += 2
                }
                camino.close()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((rect, nombre) in recuadros) {
            canvas.drawRect(rect, marcoRecuadro)
            canvas.drawText(nombre, rect.left, rect.bottom + textoRecuadro.textSize + dp(2f), textoRecuadro)
        }
        for ((codigo, camino) in caminos) {
            relleno.color = colorDe(codigo)
            canvas.drawPath(camino, relleno)
            canvas.drawPath(camino, borde)
        }
        seleccionado?.let { codigo ->
            caminos[codigo]?.let { camino -> pasadasBrillo.forEach { canvas.drawPath(camino, it) } }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val x = event.x.toInt()
                val y = event.y.toInt()
                val tocado = regiones.entries.firstOrNull { it.value.contains(x, y) }?.key
                    ?: recuadroTocado(event.x, event.y)
                if (tocado != null) {
                    seleccionado = tocado
                    alTocarZona?.invoke(tocado)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Las islas son diminutas: un toque en cualquier parte de su recuadro también cuenta. */
    private fun recuadroTocado(x: Float, y: Float): Int? =
        if (recuadros.any { it.first.contains(x, y) }) CODIGO_SAN_ANDRES else null

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        animador?.cancel()
        super.onDetachedFromWindow()
    }

    private fun latitudMedia(poligono: List<DoubleArray>): Double {
        val anillo = poligono.firstOrNull() ?: return 0.0
        var suma = 0.0
        var n = 0
        var i = 1
        while (i < anillo.size) { suma += anillo[i]; n++; i += 2 }
        return if (n == 0) 0.0 else suma / n
    }

    private fun calcularCaja(poligonos: List<List<DoubleArray>>): Caja? {
        var lonMin = Double.MAX_VALUE
        var lonMax = -Double.MAX_VALUE
        var latMin = Double.MAX_VALUE
        var latMax = -Double.MAX_VALUE
        for (p in poligonos) for (anillo in p) {
            var i = 0
            while (i + 1 < anillo.size) {
                lonMin = minOf(lonMin, anillo[i]); lonMax = maxOf(lonMax, anillo[i])
                latMin = minOf(latMin, anillo[i + 1]); latMax = maxOf(latMax, anillo[i + 1])
                i += 2
            }
        }
        return if (lonMin == Double.MAX_VALUE) null else Caja(lonMin, lonMax, latMin, latMax)
    }

    private fun dp(valor: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, resources.displayMetrics)

    companion object {
        const val CODIGO_SAN_ANDRES = 88
        val COLOR_SIN_DATO: Int = Color.rgb(42, 53, 56)

        /** Corrige el ancho de los grados de longitud a la latitud media de Colombia (~4° N). */
        private val FACTOR_LON = cos(Math.toRadians(4.0))
    }
}
