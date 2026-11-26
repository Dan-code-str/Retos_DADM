package co.edu.unal.reto10

import android.animation.ArgbEvaluator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import co.edu.unal.reto10.data.ModoMapa
import co.edu.unal.reto10.data.Periodo
import co.edu.unal.reto10.data.Repositorio
import co.edu.unal.reto10.data.SerieMapa
import co.edu.unal.reto10.data.ValorZona
import co.edu.unal.reto10.ui.BaseActivity
import co.edu.unal.reto10.ui.Estilo
import co.edu.unal.reto10.ui.Extras
import co.edu.unal.reto10.ui.GeoCache
import co.edu.unal.reto10.ui.MapaColombiaView
import co.edu.unal.reto10.ui.SparklineView
import co.edu.unal.reto10.util.Formato
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider

/**
 * Pantalla 4: mapa coroplético interactivo.
 * - Sin DEP_CODIGO: Colombia por departamentos. Con DEP_CODIGO: municipios de ese departamento.
 * - Trae todos los trimestres en una consulta; el slider cambia de periodo al instante y el botón
 *   de reproducir anima la evolución con transición de colores (escala fija para poder comparar).
 */
class MapaActivity : BaseActivity() {

    companion object {
        const val RESULTADO_DEP_CODIGO = "resultado_dep_codigo"
        const val EXTRA_MODO = "modo_mapa"
        private const val PASO_REPRODUCCION_MS = 1100L

        private val COLOR_BAJO = Color.rgb(18, 58, 56)
        private val COLOR_MEDIO = Estilo.AGUAMARINA
        private val COLOR_ALTO = Color.rgb(198, 255, 77)

        private val COLORES_OPERADOR = linkedMapOf(
            "Claro" to Color.rgb(255, 90, 95),
            "Tigo" to Color.rgb(63, 169, 255),
            "Movistar" to Estilo.AGUAMARINA,
            "ETB" to Color.rgb(167, 139, 250),
            "WOM" to Color.rgb(255, 110, 199)
        )
        private val COLORES_OTROS = listOf(
            Color.rgb(255, 181, 71), Estilo.LIMA, Color.rgb(255, 138, 80), Color.rgb(124, 131, 255),
            Color.rgb(77, 208, 160), Color.rgb(232, 121, 249), Color.rgb(148, 163, 184)
        )
    }

    private lateinit var mapa: MapaColombiaView
    private lateinit var slider: Slider
    private lateinit var botonReproducir: MaterialButton
    private lateinit var leyenda: LinearLayout

    /** Departamento que se está viendo por municipios; null = mapa nacional. */
    private var depCodigo: String? = null
    private var depNombre: String = ""
    private val porMunicipios get() = depCodigo != null

    private var modo = ModoMapa.PENETRACION
    private var periodoInicial: Periodo? = null
    private var nombres: Map<Int, String> = emptyMap()

    private val series = HashMap<ModoMapa, SerieMapa>()
    private var serie: SerieMapa? = null
    private var indice = 0
    private var minimoGlobal = 0.0
    private var maximoGlobal = 0.0
    private var coloresOperador: Map<String, Int> = emptyMap()
    private var seleccionado: Int? = null
    private var solicitud = 0

    private val manejador = Handler(Looper.getMainLooper())
    private var reproduciendo = false
    private val paso = object : Runnable {
        override fun run() {
            val s = serie ?: return detener()
            if (indice >= s.periodos.lastIndex) return detener()
            slider.value = (indice + 1).toFloat()
            manejador.postDelayed(this, PASO_REPRODUCCION_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararPantalla(R.layout.activity_mapa)

        depCodigo = intent.getStringExtra(Extras.DEP_CODIGO)
        depNombre = intent.getStringExtra(Extras.DEP_NOMBRE).orEmpty()
        modo = ModoMapa.entries.getOrNull(intent.getIntExtra(EXTRA_MODO, 0)) ?: ModoMapa.PENETRACION
        val anno = intent.getIntExtra(Extras.ANNO, 0)
        if (anno > 0) periodoInicial = Periodo(anno, intent.getIntExtra(Extras.TRIMESTRE, 0))

        mapa = findViewById(R.id.mapa)
        slider = findViewById(R.id.sliderPeriodo)
        botonReproducir = findViewById(R.id.botonReproducir)
        leyenda = findViewById(R.id.leyenda)

        mapa.separarIslas = !porMunicipios
        mapa.alTocarZona = { codigo -> mostrarSeleccion(codigo) }
        findViewById<TextView>(R.id.textoTituloMapa).text =
            if (porMunicipios) depNombre else getString(R.string.mapa_titulo)

        configurarModos()
        configurarLineaDeTiempo()
        configurarBotonesTarjeta()
        actualizarDescripcion()
        cargarInicial()
    }

    override fun onPause() {
        detener()
        super.onPause()
    }

    // --- Controles ---

    private fun configurarModos() {
        val grupo = findViewById<MaterialButtonToggleGroup>(R.id.grupoModo)
        grupo.check(
            when (modo) {
                ModoMapa.FIBRA -> R.id.botonModoFibra
                ModoMapa.OPERADOR -> R.id.botonModoOperador
                ModoMapa.PENETRACION -> R.id.botonModoPenetracion
            }
        )
        grupo.addOnButtonCheckedListener { _, id, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            modo = when (id) {
                R.id.botonModoFibra -> ModoMapa.FIBRA
                R.id.botonModoOperador -> ModoMapa.OPERADOR
                else -> ModoMapa.PENETRACION
            }
            detener()
            actualizarDescripcion()
            cargarSerie()
        }
    }

    private fun configurarLineaDeTiempo() {
        slider.isEnabled = false
        slider.setLabelFormatter { valor -> serie?.periodos?.getOrNull(valor.toInt())?.toString() ?: "" }
        slider.addOnChangeListener { _, valor, delUsuario ->
            if (delUsuario) detener()
            val nuevo = valor.toInt()
            if (nuevo != indice) {
                indice = nuevo
                mostrarPeriodo(animar = true)
            }
        }
        botonReproducir.setOnClickListener { if (reproduciendo) detener() else reproducir() }
    }

    private fun reproducir() {
        val s = serie ?: return
        if (s.periodos.size < 2) return
        reproduciendo = true
        botonReproducir.setIconResource(R.drawable.ic_pause)
        botonReproducir.contentDescription = getString(R.string.pausar)
        if (indice >= s.periodos.lastIndex) {
            slider.value = 0f
        }
        manejador.postDelayed(paso, PASO_REPRODUCCION_MS / 2)
    }

    private fun detener() {
        reproduciendo = false
        manejador.removeCallbacks(paso)
        if (::botonReproducir.isInitialized) {
            botonReproducir.setIconResource(R.drawable.ic_play)
            botonReproducir.contentDescription = getString(R.string.reproducir)
        }
    }

    private fun configurarBotonesTarjeta() {
        val principal = findViewById<MaterialButton>(R.id.botonAccionPrincipal)
        val secundaria = findViewById<MaterialButton>(R.id.botonAccionSecundaria)
        if (porMunicipios) {
            principal.setText(R.string.boton_ver_resumen_municipio)
            principal.setOnClickListener { abrirResumenMunicipio() }
            secundaria.visibility = View.GONE
        } else {
            principal.setText(R.string.boton_ver_municipios)
            principal.setOnClickListener { abrirMapaDepartamento() }
            secundaria.setText(R.string.boton_consultar_departamento)
            secundaria.setOnClickListener {
                val codigo = seleccionado ?: return@setOnClickListener
                setResult(RESULT_OK, Intent().putExtra(RESULTADO_DEP_CODIGO, codigo.toString()))
                finish()
            }
        }
    }

    // --- Carga de datos ---

    private fun cargarInicial() {
        mostrarCargando(getString(R.string.cargando_mapa))
        val contexto = applicationContext
        val departamento = depCodigo?.toIntOrNull()
        cargar(
            trabajo = {
                if (departamento != null) GeoCache.municipiosDe(contexto, departamento)
                else GeoCache.departamentos(contexto)
            },
            exito = { zonas ->
                mapa.setZonas(zonas)
                nombres = zonas.associate { it.codigo to it.nombre }
                cargarSerie()
            },
            error = { e -> mostrarError(mensajeError(e)) { cargarInicial() } }
        )
    }

    private fun cargarSerie() {
        series[modo]?.let {
            ocultarEstado()
            aplicarSerie(it)
            return
        }
        val numero = ++solicitud
        val modoPedido = modo
        val departamento = depCodigo
        slider.isEnabled = false
        mostrarCargando(getString(R.string.cargando_serie))
        cargar(
            trabajo = { Repositorio.mapaSerie(modoPedido, departamento) },
            exito = { resultado ->
                series[modoPedido] = resultado
                if (numero != solicitud) return@cargar
                ocultarEstado()
                aplicarSerie(resultado)
            },
            error = { e ->
                if (numero != solicitud) return@cargar
                mostrarError(mensajeError(e)) { cargarSerie() }
            }
        )
    }

    private fun aplicarSerie(nueva: SerieMapa) {
        val periodoActual = serie?.periodos?.getOrNull(indice) ?: periodoInicial
        serie = nueva
        if (nueva.periodos.isEmpty()) return

        // Escala fija para toda la serie: así la animación compara periodos de verdad.
        val todos = nueva.valores.values.flatMap { it.values }
        minimoGlobal = todos.minOfOrNull { it.valor } ?: 0.0
        maximoGlobal = todos.maxOfOrNull { it.valor } ?: 0.0
        coloresOperador = asignarColoresOperador(todos.mapNotNull { it.categoria })

        indice = nueva.periodos.indexOf(periodoActual).takeIf { it >= 0 } ?: nueva.periodos.lastIndex
        slider.valueFrom = 0f
        slider.valueTo = maxOf(1, nueva.periodos.lastIndex).toFloat()
        slider.value = indice.toFloat()
        slider.isEnabled = nueva.periodos.size > 1
        botonReproducir.isEnabled = nueva.periodos.size > 1

        if (modo == ModoMapa.OPERADOR) leyendaCategorias() else leyendaGradiente()
        mostrarPeriodo(animar = true)
    }

    // --- Pintar un periodo ---

    private fun mostrarPeriodo(animar: Boolean) {
        val s = serie ?: return
        val periodo = s.periodos.getOrNull(indice) ?: return
        val valores = s.valores[periodo].orEmpty()

        val colores = if (modo == ModoMapa.OPERADOR) {
            valores.mapValues { (_, v) -> coloresOperador[v.categoria] ?: MapaColombiaView.COLOR_SIN_DATO }
        } else {
            valores.mapValues { (_, v) -> colorGradiente(normalizar(v.valor)) }
        }
        mapa.setColores(colores, animar, if (reproduciendo) 700 else 450)

        findViewById<TextView>(R.id.textoPeriodo).text = periodo.toString()
        findViewById<TextView>(R.id.textoTotal).text = textoTotal(s.totales[periodo])
        findViewById<TextView>(R.id.textoExtremos).text = textoExtremos(valores)
        if (modo == ModoMapa.OPERADOR) leyendaCategorias(valores)
        seleccionado?.let { mostrarSeleccion(it) }
    }

    private fun textoTotal(total: ValorZona?): String {
        val zona = if (porMunicipios) depNombre else getString(R.string.colombia)
        if (total == null) return zona
        return when (modo) {
            ModoMapa.PENETRACION -> getString(
                R.string.total_penetracion, zona, Formato.decimal(total.valor), Formato.compacto(total.accesos)
            )
            ModoMapa.FIBRA -> getString(R.string.total_fibra, zona, Formato.decimal(total.valor))
            ModoMapa.OPERADOR -> getString(
                R.string.total_operador, zona, total.categoria ?: "—", Formato.decimal(total.valor)
            )
        }
    }

    private fun textoExtremos(valores: Map<Int, ValorZona>): String {
        if (valores.isEmpty()) return ""
        val ordenados = valores.values.sortedByDescending { it.valor }
        fun lista(items: List<ValorZona>) = items.joinToString(" · ") { v ->
            val nombre = nombres[v.codigo] ?: v.codigo.toString()
            if (modo == ModoMapa.OPERADOR) "$nombre ${v.categoria} ${Formato.decimal(v.valor, 0)} %"
            else "$nombre ${Formato.decimal(v.valor)}"
        }
        return if (modo == ModoMapa.OPERADOR) {
            getString(R.string.extremos_operador, lista(ordenados.take(3)))
        } else {
            getString(R.string.extremos_altos, lista(ordenados.take(3))) + "\n" +
                getString(R.string.extremos_bajos, lista(ordenados.takeLast(3).reversed()))
        }
    }

    // --- Tarjeta de la zona seleccionada ---

    private fun mostrarSeleccion(codigo: Int) {
        seleccionado = codigo
        mapa.seleccionado = codigo
        val s = serie ?: return
        val periodo = s.periodos.getOrNull(indice) ?: return
        val valores = s.valores[periodo].orEmpty()
        val valor = valores[codigo]

        findViewById<View>(R.id.tarjetaSeleccion).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textoZona).text = nombres[codigo] ?: codigo.toString()
        findViewById<TextView>(R.id.textoValorZona).text = if (valor == null) {
            getString(R.string.sin_datos_departamento)
        } else {
            val principal = when (modo) {
                ModoMapa.PENETRACION -> getString(R.string.valor_penetracion, Formato.decimal(valor.valor))
                ModoMapa.FIBRA -> getString(R.string.valor_fibra, Formato.decimal(valor.valor))
                ModoMapa.OPERADOR -> getString(
                    R.string.valor_operador, valor.categoria ?: getString(R.string.sin_datos), Formato.decimal(valor.valor)
                )
            }
            principal + "\n" + getString(R.string.valor_accesos, Formato.entero(valor.accesos))
        }

        // Puesto dentro del país o del departamento
        val textoPosicion = findViewById<TextView>(R.id.textoPosicion)
        val puesto = valores.values.sortedByDescending { it.valor }.indexOfFirst { it.codigo == codigo }
        if (valor != null && puesto >= 0 && modo != ModoMapa.OPERADOR) {
            textoPosicion.visibility = View.VISIBLE
            textoPosicion.text = getString(
                R.string.posicion_zona, puesto + 1, valores.size,
                getString(if (porMunicipios) R.string.zonas_municipios else R.string.zonas_departamentos)
            )
        } else {
            textoPosicion.visibility = View.GONE
        }

        // Mini evolución y variación desde el primer periodo
        val historia = s.periodos.map { s.valores[it]?.get(codigo) }
        findViewById<SparklineView>(R.id.sparkline).setValores(historia.map { it?.valor }, indice)
        findViewById<TextView>(R.id.textoVariacion).text = textoVariacion(s, historia, valor)
    }

    private fun textoVariacion(s: SerieMapa, historia: List<ValorZona?>, actual: ValorZona?): String {
        val primeroIndice = historia.indexOfFirst { it != null }
        if (actual == null || primeroIndice < 0 || primeroIndice == indice) return ""
        val primero = historia[primeroIndice] ?: return ""
        val desde = s.periodos[primeroIndice].toString()
        return when (modo) {
            ModoMapa.PENETRACION -> getString(R.string.variacion_penetracion, Formato.conSigno(actual.valor - primero.valor), desde)
            ModoMapa.FIBRA -> getString(R.string.variacion_fibra, Formato.conSigno(actual.valor - primero.valor), desde)
            ModoMapa.OPERADOR -> if (primero.categoria == actual.categoria) getString(R.string.operador_igual, desde)
            else getString(R.string.operador_cambio, desde, primero.categoria ?: "—")
        }
    }

    // --- Navegación ---

    private fun abrirMapaDepartamento() {
        val codigo = seleccionado ?: return
        val periodo = serie?.periodos?.getOrNull(indice)
        val intent = Intent(this, MapaActivity::class.java)
            .putExtra(Extras.DEP_CODIGO, codigo.toString())
            .putExtra(Extras.DEP_NOMBRE, nombres[codigo] ?: codigo.toString())
            .putExtra(EXTRA_MODO, modo.ordinal)
        if (periodo != null) {
            intent.putExtra(Extras.ANNO, periodo.anno).putExtra(Extras.TRIMESTRE, periodo.trimestre)
        }
        startActivity(intent)
    }

    private fun abrirResumenMunicipio() {
        val codigo = seleccionado ?: return
        val periodo = serie?.periodos?.getOrNull(indice) ?: return
        val intent = Intent(this, ResumenActivity::class.java)
            .putExtra(Extras.DEP_CODIGO, depCodigo)
            .putExtra(Extras.DEP_NOMBRE, depNombre)
            .putExtra(Extras.MUN_CODIGO, codigo.toString())
            .putExtra(Extras.MUN_NOMBRE, nombres[codigo] ?: codigo.toString())
            .putExtra(Extras.ANNO, periodo.anno)
            .putExtra(Extras.TRIMESTRE, periodo.trimestre)
        startActivity(intent)
    }

    private fun actualizarDescripcion() {
        val zona = getString(if (porMunicipios) R.string.zona_municipio else R.string.zona_departamento)
        findViewById<TextView>(R.id.textoDescripcionModo).text = getString(
            when (modo) {
                ModoMapa.PENETRACION -> R.string.descripcion_penetracion
                ModoMapa.FIBRA -> R.string.descripcion_fibra
                ModoMapa.OPERADOR -> R.string.descripcion_operador
            },
            zona
        )
    }

    // --- Colores ---

    private val evaluador = ArgbEvaluator()

    private fun normalizar(valor: Double): Float =
        if (maximoGlobal > minimoGlobal) ((valor - minimoGlobal) / (maximoGlobal - minimoGlobal)).toFloat() else 0.5f

    private fun colorGradiente(t: Float): Int = if (t < 0.5f) {
        evaluador.evaluate(t * 2f, COLOR_BAJO, COLOR_MEDIO) as Int
    } else {
        evaluador.evaluate((t - 0.5f) * 2f, COLOR_MEDIO, COLOR_ALTO) as Int
    }

    /** Colores fijos para los operadores grandes; el resto toma colores de reserva. */
    private fun asignarColoresOperador(operadores: List<String>): Map<String, Int> {
        val resultado = LinkedHashMap<String, Int>()
        var siguiente = 0
        for (nombre in operadores.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }) {
            resultado[nombre] = COLORES_OPERADOR[nombre] ?: COLORES_OTROS[siguiente++ % COLORES_OTROS.size]
        }
        return resultado
    }

    // --- Leyenda ---

    private fun leyendaGradiente() {
        leyenda.removeAllViews()
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sufijo = if (modo == ModoMapa.FIBRA) " %" else ""
        fila.addView(texto(Formato.decimal(minimoGlobal) + sufijo))
        fila.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(COLOR_BAJO, COLOR_MEDIO, COLOR_ALTO)
            ).apply { cornerRadius = Estilo.dp(context, 5f) }
            layoutParams = LinearLayout.LayoutParams(0, Estilo.dp(context, 10f).toInt(), 1f).apply {
                marginStart = Estilo.dp(context, 8f).toInt()
                marginEnd = Estilo.dp(context, 8f).toInt()
            }
        })
        fila.addView(texto(Formato.decimal(maximoGlobal) + sufijo))
        leyenda.addView(fila)
        leyenda.addView(muestra(MapaColombiaView.COLOR_SIN_DATO, getString(R.string.leyenda_sin_datos)))
    }

    /** Operadores presentes en el periodo actual, con cuántas zonas lidera cada uno. */
    private fun leyendaCategorias(valores: Map<Int, ValorZona> = emptyMap()) {
        leyenda.removeAllViews()
        val conteo = valores.values.mapNotNull { it.categoria }.groupingBy { it }.eachCount()
        for ((nombre, color) in coloresOperador) {
            val n = conteo[nombre] ?: 0
            if (valores.isNotEmpty() && n == 0) continue
            leyenda.addView(muestra(color, "$nombre ($n)"))
        }
        leyenda.addView(muestra(MapaColombiaView.COLOR_SIN_DATO, getString(R.string.leyenda_sin_datos)))
    }

    private fun muestra(color: Int, etiqueta: String): View {
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Estilo.dp(context, 3f).toInt(), 0, Estilo.dp(context, 3f).toInt())
        }
        fila.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = Estilo.dp(context, 3f)
            }
            layoutParams = LinearLayout.LayoutParams(Estilo.dp(context, 14f).toInt(), Estilo.dp(context, 14f).toInt()).apply {
                marginEnd = Estilo.dp(context, 8f).toInt()
            }
        })
        fila.addView(texto(etiqueta))
        return fila
    }

    private fun texto(valor: String) = TextView(this).apply {
        text = valor
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        typeface = Estilo.fuente(context)
    }
}
