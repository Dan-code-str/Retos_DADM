package co.edu.unal.reto10

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import co.edu.unal.reto10.data.Opcion
import co.edu.unal.reto10.data.Periodo
import co.edu.unal.reto10.data.Repositorio
import co.edu.unal.reto10.ui.BaseActivity
import co.edu.unal.reto10.ui.Extras
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

/** Pantalla 1: el usuario escoge departamento, municipio y periodo (parámetros del servicio). */
class ConsultaActivity : BaseActivity() {

    private lateinit var spinnerDepartamento: Spinner
    private lateinit var spinnerMunicipio: Spinner
    private lateinit var sliderPeriodo: Slider
    private lateinit var textoPeriodo: TextView
    private lateinit var textoCargandoMunicipios: TextView

    /** Periodos en orden cronológico; el slider elige uno por posición. */
    private var periodos: List<Periodo> = emptyList()

    /** Departamento cuyos municipios se pidieron por última vez (descarta respuestas viejas). */
    private var departamentoSolicitado: String? = null

    /** Departamento escogido en el mapa antes de que la lista estuviera cargada. */
    private var departamentoPendiente: String? = null

    private val lanzadorMapa = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != RESULT_OK) return@registerForActivityResult
        r.data?.getStringExtra(MapaActivity.RESULTADO_DEP_CODIGO)?.let { seleccionarDepartamento(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepararPantalla(R.layout.activity_consulta)

        spinnerDepartamento = findViewById(R.id.spinnerDepartamento)
        spinnerMunicipio = findViewById(R.id.spinnerMunicipio)
        sliderPeriodo = findViewById(R.id.sliderPeriodoConsulta)
        textoPeriodo = findViewById(R.id.textoPeriodoConsulta)
        textoCargandoMunicipios = findViewById(R.id.textoCargandoMunicipios)

        sliderPeriodo.setLabelFormatter { valor -> periodos.getOrNull(valor.toInt())?.toString() ?: "" }
        sliderPeriodo.addOnChangeListener { _, valor, _ ->
            textoPeriodo.text = periodos.getOrNull(valor.toInt())?.toString().orEmpty()
        }

        spinnerDepartamento.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val departamento = spinnerDepartamento.selectedItem as? Opcion ?: return
                cargarMunicipios(departamento.codigo)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<MaterialButton>(R.id.botonConsultar).setOnClickListener { consultar() }
        findViewById<MaterialButton>(R.id.botonMapa).setOnClickListener {
            lanzadorMapa.launch(Intent(this, MapaActivity::class.java))
        }

        cargarDatosIniciales()
    }

    private fun cargarDatosIniciales() {
        mostrarCargando(getString(R.string.cargando_inicial))
        cargar(
            trabajo = { Repositorio.datosIniciales() },
            exito = { datos ->
                ocultarEstado()
                spinnerDepartamento.adapter = adaptador(datos.departamentos)
                // El slider arranca en el periodo más reciente.
                periodos = datos.periodos
                if (periodos.isNotEmpty()) {
                    sliderPeriodo.valueFrom = 0f
                    sliderPeriodo.valueTo = maxOf(1, periodos.lastIndex).toFloat()
                    sliderPeriodo.value = periodos.lastIndex.toFloat()
                    sliderPeriodo.isEnabled = periodos.size > 1
                    textoPeriodo.text = periodos.last().toString()
                }
                departamentoPendiente?.let { seleccionarDepartamento(it) }
            },
            error = { e -> mostrarError(mensajeError(e)) { cargarDatosIniciales() } }
        )
    }

    /** Selecciona un departamento por código DANE ("5" y "05" se consideran iguales). */
    private fun seleccionarDepartamento(codigo: String) {
        val adaptador = spinnerDepartamento.adapter
        if (adaptador == null || adaptador.isEmpty) {
            departamentoPendiente = codigo
            return
        }
        departamentoPendiente = null
        val buscado = codigo.toIntOrNull()
        for (i in 0 until adaptador.count) {
            val opcion = adaptador.getItem(i) as? Opcion ?: continue
            if (opcion.codigo.toIntOrNull() == buscado) {
                spinnerDepartamento.setSelection(i)
                return
            }
        }
    }

    private fun cargarMunicipios(codigoDepartamento: String) {
        departamentoSolicitado = codigoDepartamento
        spinnerMunicipio.adapter = adaptador(emptyList<Opcion>())
        textoCargandoMunicipios.visibility = View.VISIBLE
        cargar(
            trabajo = { Repositorio.municipios(codigoDepartamento) },
            exito = { municipios ->
                if (codigoDepartamento != departamentoSolicitado) return@cargar
                textoCargandoMunicipios.visibility = View.GONE
                spinnerMunicipio.adapter = adaptador(municipios)
            },
            error = { e ->
                if (codigoDepartamento != departamentoSolicitado) return@cargar
                textoCargandoMunicipios.visibility = View.GONE
                mostrarError(mensajeError(e)) {
                    ocultarEstado()
                    cargarMunicipios(codigoDepartamento)
                }
            }
        )
    }

    private fun consultar() {
        val departamento = spinnerDepartamento.selectedItem as? Opcion
        val municipio = spinnerMunicipio.selectedItem as? Opcion
        val periodo = periodos.getOrNull(sliderPeriodo.value.toInt())
        if (departamento == null || municipio == null || periodo == null) {
            Toast.makeText(this, R.string.espera_municipios, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, ResumenActivity::class.java)
            .putExtra(Extras.DEP_CODIGO, departamento.codigo)
            .putExtra(Extras.DEP_NOMBRE, departamento.nombre)
            .putExtra(Extras.MUN_CODIGO, municipio.codigo)
            .putExtra(Extras.MUN_NOMBRE, municipio.nombre)
            .putExtra(Extras.ANNO, periodo.anno)
            .putExtra(Extras.TRIMESTRE, periodo.trimestre)
        startActivity(intent)
    }

    private fun <T> adaptador(items: List<T>): ArrayAdapter<T> =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
}
