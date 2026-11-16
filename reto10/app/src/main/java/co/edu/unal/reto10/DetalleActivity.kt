package co.edu.unal.reto10

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import co.edu.unal.reto10.data.DetalleMunicipio
import co.edu.unal.reto10.data.ItemConteo
import co.edu.unal.reto10.data.Periodo
import co.edu.unal.reto10.data.Repositorio
import co.edu.unal.reto10.ui.Animaciones
import co.edu.unal.reto10.ui.BaseActivity
import co.edu.unal.reto10.ui.Barra
import co.edu.unal.reto10.ui.Barras
import co.edu.unal.reto10.ui.Extras
import co.edu.unal.reto10.util.Formato

/** Pantalla 3: detalle por estrato, tecnología y operador con datos de hwrp-3su3 (consultas 6 a 9). */
class DetalleActivity : BaseActivity() {

    private lateinit var munCodigo: String
    private lateinit var periodo: Periodo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararPantalla(R.layout.activity_detalle)

        munCodigo = intent.getStringExtra(Extras.MUN_CODIGO).orEmpty()
        val munNombre = intent.getStringExtra(Extras.MUN_NOMBRE).orEmpty()
        periodo = Periodo(intent.getIntExtra(Extras.ANNO, 0), intent.getIntExtra(Extras.TRIMESTRE, 0))

        findViewById<TextView>(R.id.textoMunicipio).text = munNombre
        findViewById<TextView>(R.id.textoSubtitulo).text =
            getString(R.string.subtitulo_municipio, getString(R.string.titulo_detalle), periodo.toString())

        cargarDetalle()
    }

    private fun cargarDetalle() {
        findViewById<View>(R.id.contenido).visibility = View.GONE
        mostrarCargando(getString(R.string.cargando_resumen))
        cargar(
            trabajo = { Repositorio.detalle(munCodigo, periodo) },
            exito = { detalle -> mostrar(detalle) },
            error = { e -> mostrarError(mensajeError(e)) { cargarDetalle() } }
        )
    }

    private fun mostrar(detalle: DetalleMunicipio) {
        ocultarEstado()
        findViewById<View>(R.id.contenido).visibility = View.VISIBLE

        val total = detalle.total
        Animaciones.contar(findViewById(R.id.textoTotal), total.toDouble()) { Formato.entero(Math.round(it)) }
        val velocidad = detalle.velocidadPromedio
        val textoVelocidad = findViewById<TextView>(R.id.textoVelocidad)
        if (velocidad == null) {
            textoVelocidad.text = getString(R.string.sin_datos)
        } else {
            Animaciones.contar(textoVelocidad, velocidad) { getString(R.string.valor_mbps, Formato.entero(Math.round(it))) }
        }

        llenar(R.id.contenedorEstratos, detalle.estratos, total)
        llenar(R.id.contenedorTecnologias, detalle.tecnologias, total)
        llenar(R.id.contenedorOperadores, detalle.operadores, total)
    }

    /** Barras con el valor absoluto y su porcentaje sobre el total del municipio. */
    private fun llenar(idContenedor: Int, items: List<ItemConteo>, total: Long) {
        val contenedor = findViewById<LinearLayout>(idContenedor)
        if (items.isEmpty()) {
            Barras.llenar(contenedor, listOf(Barra(getString(R.string.sin_datos), 0.0, "")))
            return
        }
        Barras.llenar(contenedor, items.map {
            val porcentaje = if (total > 0) it.valor * 100.0 / total else 0.0
            Barra(
                etiqueta = it.etiqueta,
                valor = it.valor.toDouble(),
                texto = getString(R.string.nota_porcentaje, Formato.entero(it.valor), Formato.decimal(porcentaje))
            )
        })
    }
}
