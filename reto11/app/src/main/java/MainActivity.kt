package co.edu.unal.reto11

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.unal.reto11.data.ConstructorPrompt
import co.edu.unal.reto11.data.Creacion
import co.edu.unal.reto11.data.Opcion
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pantalla única: el usuario escribe una idea, elige estilo/ambiente/formato y Gemini genera
 * la imagen. Luego puede refinarla con instrucciones, guardarla o compartirla.
 */
class MainActivity : AppCompatActivity() {

    private val vm: GeneradorViewModel by viewModels()

    private lateinit var campoIdea: TextInputEditText
    private lateinit var layoutIdea: TextInputLayout
    private lateinit var grupoEstilo: ChipGroup
    private lateinit var grupoAmbiente: ChipGroup
    private lateinit var grupoFormato: ChipGroup
    private lateinit var botonGenerar: MaterialButton
    private lateinit var botonMejorar: MaterialButton
    private lateinit var estado: View
    private lateinit var progreso: View
    private lateinit var textoEstado: TextView
    private lateinit var botonReintentar: MaterialButton
    private lateinit var tarjetaResultado: MaterialCardView
    private lateinit var imagenResultado: ShapeableImageView
    private lateinit var textoPrompt: TextView
    private lateinit var textoComentario: TextView
    private lateinit var campoCambio: TextInputEditText
    private lateinit var layoutCambio: TextInputLayout
    private lateinit var botonRefinar: MaterialButton
    private lateinit var contenedorHistorial: LinearLayout
    private lateinit var textoHistorialVacio: TextView

    /** Evita volver a decodificar la imagen si el estado se emite de nuevo sin cambios. */
    private var rutaMostrada: String? = null
    private var historialMostrado: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        aplicarMargenesDeSistema()
        enlazarVistas()
        llenarChips(grupoEstilo, ConstructorPrompt.estilos, preseleccionar = true)
        llenarChips(grupoAmbiente, ConstructorPrompt.ambientes, preseleccionar = false)
        llenarChips(grupoFormato, ConstructorPrompt.formatos, preseleccionar = true)
        configurarAcciones()
        findViewById<TextView>(R.id.textoPie).text = getString(R.string.pie, vm.nombreGenerador)
        if (!vm.admiteFormato) {
            // FLUX entrega siempre imágenes cuadradas: no se ofrece un formato que no se respetaría
            findViewById<View>(R.id.textoSeccionFormato).visibility = View.GONE
            grupoFormato.visibility = View.GONE
        }
        observar()
    }

    private fun aplicarMargenesDeSistema() {
        val raiz = findViewById<View>(R.id.main)
        val contenido = findViewById<View>(R.id.contenido)
        val base = contenido.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { _, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            contenido.updatePadding(
                left = base + barras.left,
                top = base + barras.top,
                right = base + barras.right,
                bottom = base + barras.bottom,
            )
            insets
        }
    }

    private fun enlazarVistas() {
        campoIdea = findViewById(R.id.campoIdea)
        layoutIdea = findViewById(R.id.layoutIdea)
        grupoEstilo = findViewById(R.id.grupoEstilo)
        grupoAmbiente = findViewById(R.id.grupoAmbiente)
        grupoFormato = findViewById(R.id.grupoFormato)
        botonGenerar = findViewById(R.id.botonGenerar)
        botonMejorar = findViewById(R.id.botonMejorar)
        estado = findViewById(R.id.estado)
        progreso = findViewById(R.id.progresoCarga)
        textoEstado = findViewById(R.id.textoEstado)
        botonReintentar = findViewById(R.id.botonReintentar)
        tarjetaResultado = findViewById(R.id.tarjetaResultado)
        imagenResultado = findViewById(R.id.imagenResultado)
        textoPrompt = findViewById(R.id.textoPrompt)
        textoComentario = findViewById(R.id.textoComentario)
        campoCambio = findViewById(R.id.campoCambio)
        layoutCambio = findViewById(R.id.layoutCambio)
        botonRefinar = findViewById(R.id.botonRefinar)
        contenedorHistorial = findViewById(R.id.contenedorHistorial)
        textoHistorialVacio = findViewById(R.id.textoHistorialVacio)
    }

    private fun llenarChips(grupo: ChipGroup, opciones: List<Opcion>, preseleccionar: Boolean) {
        opciones.forEachIndexed { i, opcion ->
            val chip = layoutInflater.inflate(R.layout.item_chip, grupo, false) as Chip
            chip.id = View.generateViewId()
            chip.text = opcion.etiqueta
            chip.tag = opcion
            grupo.addView(chip)
            if (preseleccionar && i == 0) grupo.check(chip.id)
        }
    }

    private fun seleccion(grupo: ChipGroup): Opcion? =
        grupo.checkedChipId.takeIf { it != View.NO_ID }?.let { grupo.findViewById<Chip>(it)?.tag as? Opcion }

    private fun configurarAcciones() {
        botonGenerar.setOnClickListener {
            val idea = campoIdea.text?.toString().orEmpty()
            if (idea.isBlank()) {
                layoutIdea.error = getString(R.string.falta_idea)
                return@setOnClickListener
            }
            layoutIdea.error = null
            ocultarTeclado()
            vm.generar(idea, seleccion(grupoEstilo)!!, seleccion(grupoAmbiente), seleccion(grupoFormato)!!)
        }

        botonMejorar.setOnClickListener {
            val idea = campoIdea.text?.toString().orEmpty()
            if (idea.isBlank()) {
                layoutIdea.error = getString(R.string.falta_idea)
                return@setOnClickListener
            }
            layoutIdea.error = null
            ocultarTeclado()
            vm.mejorarIdea(idea, seleccion(grupoEstilo)!!)
        }

        campoIdea.setOnFocusChangeListener { _, _ -> layoutIdea.error = null }

        botonRefinar.setOnClickListener { refinar() }
        campoCambio.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEND) { refinar(); true } else false
        }

        botonReintentar.setOnClickListener { vm.reintentar() }
        findViewById<MaterialButton>(R.id.botonGuardar).setOnClickListener { vm.guardarEnGaleria() }
        findViewById<MaterialButton>(R.id.botonCompartir).setOnClickListener { compartir() }
        findViewById<MaterialButton>(R.id.botonClave).setOnClickListener { mostrarDialogoClave() }
    }

    private fun refinar() {
        val instruccion = campoCambio.text?.toString().orEmpty()
        if (instruccion.isBlank()) {
            layoutCambio.error = getString(R.string.falta_cambio)
            return
        }
        layoutCambio.error = null
        ocultarTeclado()
        vm.refinar(instruccion, seleccion(grupoFormato)!!)
        campoCambio.text = null
    }

    // ------------------------------------------------------------ Estado

    private fun observar() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.estado.collect { pintar(it) } }
                launch {
                    vm.eventos.collect { evento ->
                        when (evento) {
                            is Evento.IdeaMejorada -> {
                                campoIdea.setText(evento.texto)
                                campoIdea.setSelection(campoIdea.text?.length ?: 0)
                                Toast.makeText(this@MainActivity, R.string.idea_mejorada, Toast.LENGTH_SHORT).show()
                            }
                            is Evento.Aviso ->
                                Toast.makeText(this@MainActivity, evento.texto, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun pintar(e: EstadoUi) {
        // Bloque de carga / error
        val hayError = e.error != null
        estado.visibility = if (e.ocupado || hayError) View.VISIBLE else View.GONE
        progreso.visibility = if (e.ocupado) View.VISIBLE else View.GONE
        textoEstado.text = if (e.ocupado) e.mensajeCarga else e.error
        textoEstado.setTextColor(
            if (hayError && !e.ocupado) getColor(R.color.error) else getColor(R.color.texto)
        )
        botonReintentar.visibility = if (hayError && !e.ocupado) View.VISIBLE else View.GONE

        listOf(botonGenerar, botonMejorar, botonRefinar).forEach { it.isEnabled = !e.ocupado }

        // Resultado
        val actual = e.actual
        tarjetaResultado.visibility = if (actual != null) View.VISIBLE else View.GONE
        if (actual != null) {
            textoPrompt.text = actual.prompt
            textoComentario.visibility = if (e.comentario != null) View.VISIBLE else View.GONE
            textoComentario.text = e.comentario?.let { getString(R.string.comentario_modelo, it) }
            if (rutaMostrada != actual.archivo.absolutePath) {
                rutaMostrada = actual.archivo.absolutePath
                cargarImagen(actual.archivo, 1600) { imagenResultado.setImageBitmap(it) }
            }
        }

        // Historial
        val rutas = e.historial.map { it.archivo.absolutePath }
        if (rutas != historialMostrado) {
            historialMostrado = rutas
            pintarHistorial(e.historial)
        }
    }

    private fun pintarHistorial(historial: List<Creacion>) {
        contenedorHistorial.removeAllViews()
        textoHistorialVacio.visibility = if (historial.isEmpty()) View.VISIBLE else View.GONE
        historial.forEach { creacion ->
            val miniatura = layoutInflater.inflate(
                R.layout.item_miniatura, contenedorHistorial, false
            ) as ShapeableImageView
            miniatura.contentDescription = getString(R.string.desc_miniatura, creacion.prompt.take(60))
            miniatura.setOnClickListener {
                vm.seleccionar(creacion)
                findViewById<View>(R.id.main).let { raiz ->
                    (raiz as? android.widget.ScrollView)?.smoothScrollTo(0, tarjetaResultado.top)
                }
            }
            contenedorHistorial.addView(miniatura)
            cargarImagen(creacion.archivo, 256) { miniatura.setImageBitmap(it) }
        }
    }

    /** Decodifica la imagen en segundo plano reduciéndola para no gastar memoria de más. */
    private fun cargarImagen(archivo: File, ladoMaximo: Int, alTerminar: (Bitmap) -> Unit) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(archivo.absolutePath, limites)
                var escala = 1
                while (limites.outWidth / (escala * 2) >= ladoMaximo || limites.outHeight / (escala * 2) >= ladoMaximo) {
                    escala *= 2
                }
                BitmapFactory.decodeFile(
                    archivo.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = escala }
                )
            }
            if (bitmap != null) alTerminar(bitmap)
        }
    }

    // ------------------------------------------------------------ Acciones de la imagen

    private fun compartir() {
        val actual = vm.estado.value.actual ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = actual.mimeType
            putExtra(Intent.EXTRA_STREAM, vm.uriParaCompartir(actual))
            putExtra(Intent.EXTRA_TEXT, actual.prompt)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.compartir_titulo)))
    }

    private fun mostrarDialogoClave() {
        val margen = (20 * resources.displayMetrics.density).toInt()
        val layout = TextInputLayout(this).apply {
            hint = getString(R.string.pista_clave)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val campo = TextInputEditText(layout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(vm.claveGuardadaEnApp)
        }
        layout.addView(campo)
        val contenedor = FrameLayout(this).apply {
            setPadding(margen, margen / 2, margen, 0)
            addView(layout)
        }

        var mensaje = getString(R.string.dialogo_clave_mensaje)
        if (vm.hayClaveDeCompilacion) mensaje += "\n\n" + getString(R.string.clave_desde_build)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialogo_clave_titulo)
            .setMessage(mensaje)
            .setView(contenedor)
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.guardar) { _, _ ->
                vm.guardarClave(campo.text?.toString().orEmpty())
                Toast.makeText(this, R.string.clave_guardada, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun ocultarTeclado() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(currentFocus?.windowToken ?: return, 0)
        currentFocus?.clearFocus()
    }
}
