package co.edu.unal.reto10

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import co.edu.unal.reto10.data.PeriodoAccesos
import co.edu.unal.reto10.data.Periodo
import co.edu.unal.reto10.data.Repositorio
import co.edu.unal.reto10.data.ResumenMunicipio
import co.edu.unal.reto10.ui.Animaciones
import co.edu.unal.reto10.ui.BaseActivity
import co.edu.unal.reto10.ui.EvolucionView
import co.edu.unal.reto10.ui.Extras
import co.edu.unal.reto10.util.Formato
import com.google.android.material.button.MaterialButton

/** Pantalla 2: resumen del municipio con datos de tvzi-ba2g (consultas 4 y 5). */
class ResumenActivity : BaseActivity() {

    private lateinit var depCodigo: String
    private lateinit var depNombre: String
    private lateinit var munCodigo: String
    private lateinit var munNombre: String
    private lateinit var periodo: Periodo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararPantalla(R.layout.activity_resumen)

        depCodigo = intent.getStringExtra(Extras.DEP_CODIGO).orEmpty()
        depNombre = intent.getStringExtra(Extras.DEP_NOMBRE).orEmpty()
        munCodigo = intent.getStringExtra(Extras.MUN_CODIGO).orEmpty()
        munNombre = intent.getStringExtra(Extras.MUN_NOMBRE).orEmpty()
        periodo = Periodo(intent.getIntExtra(Extras.ANNO, 0), intent.getIntExtra(Extras.TRIMESTRE, 0))

        findViewById<TextView>(R.id.textoMunicipio).text = munNombre
        findViewById<TextView>(R.id.textoSubtitulo).text =
            getString(R.string.subtitulo_municipio, depNombre, periodo.toString())

        findViewById<MaterialButton>(R.id.botonDetalle).setOnClickListener { abrirDetalle() }
        findViewById<MaterialButton>(R.id.botonMapaGoogle).setOnClickListener { abrirEnGoogleMaps() }

        cargarResumen()
    }

    private fun cargarResumen() {
        findViewById<View>(R.id.contenido).visibility = View.GONE
        mostrarCargando(getString(R.string.cargando_resumen))
        cargar(
            trabajo = { Repositorio.resumen(depCodigo, munCodigo, periodo) },
            exito = { resumen -> mostrar(resumen) },
            error = { e -> mostrarError(mensajeError(e)) { cargarResumen() } }
        )
    }

    private fun mostrar(resumen: ResumenMunicipio) {
        val actual = resumen.evolucion.firstOrNull { it.periodo == periodo }
        if (actual == null) {
            mostrarError(getString(R.string.sin_datos_periodo, periodo.toString())) { cargarResumen() }
            return
        }
        ocultarEstado()
        findViewById<View>(R.id.contenido).visibility = View.VISIBLE

        Animaciones.contar(findViewById(R.id.textoAccesos), actual.accesos.toDouble()) { Formato.entero(Math.round(it)) }
        Animaciones.contar(findViewById(R.id.textoPoblacion), actual.poblacion.toDouble()) { Formato.entero(Math.round(it)) }
        Animaciones.contar(findViewById(R.id.textoPenetracion), actual.penetracion, 1100) { Formato.decimal(it) }

        val posicion = resumen.ranking.indexOfFirst { it.codigo == munCodigo }
        findViewById<TextView>(R.id.textoPosicion).apply {
            if (posicion >= 0) {
                visibility = View.VISIBLE
                text = getString(R.string.posicion_departamento, posicion + 1, resumen.ranking.size, depNombre)
            } else {
                visibility = View.GONE
            }
        }

        val evolucion = resumen.evolucion
        val grafico = findViewById<EvolucionView>(R.id.graficoEvolucion)
        val indiceActual = evolucion.indexOfFirst { it.periodo == periodo }
        grafico.setPuntos(
            evolucion.map { EvolucionView.Punto("T${it.periodo.trimestre}", it.periodo.anno, it.penetracion, Formato.decimal(it.penetracion)) },
            indiceActual
        )
        grafico.alSeleccionar = { i -> mostrarTrimestre(evolucion[i]) }
        mostrarTrimestre(actual)
    }

    private fun mostrarTrimestre(p: PeriodoAccesos) {
        findViewById<TextView>(R.id.textoEvolucionSeleccion).text = getString(
            R.string.evolucion_seleccion, p.periodo.toString(), Formato.decimal(p.penetracion), Formato.entero(p.accesos)
        )
    }

    private fun abrirDetalle() {
        val intent = Intent(this, DetalleActivity::class.java)
            .putExtra(Extras.DEP_NOMBRE, depNombre)
            .putExtra(Extras.MUN_CODIGO, munCodigo)
            .putExtra(Extras.MUN_NOMBRE, munNombre)
            .putExtra(Extras.ANNO, periodo.anno)
            .putExtra(Extras.TRIMESTRE, periodo.trimestre)
        startActivity(intent)
    }

    /** Abre el municipio en la app de mapas del teléfono (Intent implícito, sin coordenadas). */
    private fun abrirEnGoogleMaps() {
        val lugar = "$munNombre, $depNombre, Colombia"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(lugar)))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.sin_app_mapas, Toast.LENGTH_SHORT).show()
        }
    }
}
