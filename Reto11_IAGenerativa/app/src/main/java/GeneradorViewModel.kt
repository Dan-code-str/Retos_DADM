package co.edu.unal.reto11

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.edu.unal.reto11.data.Almacen
import co.edu.unal.reto11.data.CloudflareCliente
import co.edu.unal.reto11.data.ConstructorPrompt
import co.edu.unal.reto11.data.Creacion
import co.edu.unal.reto11.data.ErrorGemini
import co.edu.unal.reto11.data.GeminiCliente
import co.edu.unal.reto11.data.GeneradorImagenes
import co.edu.unal.reto11.data.Opcion
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoUi(
    val ocupado: Boolean = false,
    val mensajeCarga: String? = null,
    val error: String? = null,
    val actual: Creacion? = null,
    val comentario: String? = null,
    val historial: List<Creacion> = emptyList(),
)

sealed interface Evento {
    data class IdeaMejorada(val texto: String) : Evento
    data class Aviso(val texto: String) : Evento
}

/**
 * Mantiene el estado de la pantalla (sobrevive a rotaciones) y coordina las llamadas a Gemini.
 */
class GeneradorViewModel(
    aplicacion: Application,
    private val estadoGuardado: SavedStateHandle,
) : AndroidViewModel(aplicacion) {

    private val almacen = Almacen(aplicacion)
    private val ajustes = aplicacion.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _estado = MutableStateFlow(EstadoUi())
    val estado: StateFlow<EstadoUi> = _estado.asStateFlow()

    private val _eventos = Channel<Evento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    private var trabajo: Job? = null
    private var ultimaAccion: (() -> Unit)? = null

    /** Cloudflare si hay credenciales en local.properties; si no, el modelo de imagen de Gemini. */
    private val usaCloudflare: Boolean
        get() = BuildConfig.CLOUDFLARE_ACCOUNT_ID.isNotBlank() && BuildConfig.CLOUDFLARE_API_TOKEN.isNotBlank()

    private fun generador(): GeneradorImagenes =
        if (usaCloudflare) {
            CloudflareCliente(BuildConfig.CLOUDFLARE_ACCOUNT_ID, BuildConfig.CLOUDFLARE_API_TOKEN, BuildConfig.CLOUDFLARE_MODELO)
        } else {
            gemini()
        }

    val nombreGenerador: String get() = generador().nombre
    val admiteFormato: Boolean get() = generador().admiteFormato

    init {
        viewModelScope.launch {
            val historial = almacen.historial()
            val actual = estadoGuardado.get<String>(CLAVE_ACTUAL)?.let { almacen.buscar(it) }
            _estado.update { it.copy(historial = historial, actual = actual) }
        }
    }

    // ------------------------------------------------------------ API key

    /** La clave escrita en la app tiene prioridad; si no hay, se usa la de local.properties. */
    private val claveApi: String
        get() = ajustes.getString(PREF_CLAVE, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY

    val claveGuardadaEnApp: String get() = ajustes.getString(PREF_CLAVE, "").orEmpty()
    val hayClaveDeCompilacion: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    fun guardarClave(clave: String) {
        ajustes.edit { putString(PREF_CLAVE, clave.trim()) }
    }

    private fun gemini() = GeminiCliente(claveApi, BuildConfig.MODELO_IMAGEN, BuildConfig.MODELO_TEXTO)

    /**
     * FLUX entiende mejor el inglés: si la clave de Gemini está disponible, se traduce el prompt.
     * Si la traducción falla (sin clave, servicio saturado…), se envía en español.
     */
    private suspend fun prepararPrompt(prompt: String, destino: GeneradorImagenes): Pair<String, String?> {
        if (destino is GeminiCliente || claveApi.isBlank()) return prompt to null
        return try {
            val ingles = gemini().traducirAIngles(prompt)
            ingles to texto(R.string.comentario_traduccion, ingles)
        } catch (e: ErrorGemini) {
            prompt to null
        }
    }

    // ------------------------------------------------------------ Acciones

    fun generar(idea: String, estilo: Opcion, ambiente: Opcion?, formato: Opcion) {
        val prompt = ConstructorPrompt.construir(idea, estilo, ambiente)
        ejecutar(texto(R.string.generando)) {
            val destino = generador()
            val (enviado, nota) = prepararPrompt(prompt, destino)
            val imagen = destino.generar(enviado, formato.descripcion)
            mostrar(almacen.guardar(imagen, prompt), nota ?: imagen.comentario)
        }
    }

    /**
     * Refina la imagen actual con la instrucción del usuario:
     * - Gemini recibe la imagen y la edita directamente.
     * - FLUX no edita imágenes: se regenera con el prompt original + el cambio.
     */
    fun refinar(instruccion: String, formato: Opcion) {
        val base = _estado.value.actual ?: return
        ejecutar(texto(R.string.refinando)) {
            val destino = generador()
            if (destino.editaImagenes) {
                val prompt = ConstructorPrompt.construirCambio(instruccion)
                val imagen = destino.generar(prompt, formato.descripcion, base.leer())
                mostrar(almacen.guardar(imagen, "${base.prompt}\n↳ $instruccion"), imagen.comentario)
            } else {
                val prompt = ConstructorPrompt.combinarCambio(base.prompt, instruccion)
                val (enviado, nota) = prepararPrompt(prompt, destino)
                val imagen = destino.generar(enviado, formato.descripcion, semilla = base.semilla)
                mostrar(almacen.guardar(imagen, prompt), nota)
            }
        }
    }

    fun mejorarIdea(idea: String, estilo: Opcion) {
        ejecutar(texto(R.string.mejorando)) {
            val mejorada = gemini().mejorarIdea(idea, estilo.etiqueta)
            _eventos.send(Evento.IdeaMejorada(mejorada))
        }
    }

    fun reintentar() {
        ultimaAccion?.invoke()
    }

    fun seleccionar(creacion: Creacion) {
        estadoGuardado[CLAVE_ACTUAL] = creacion.archivo.absolutePath
        _estado.update { it.copy(actual = creacion, comentario = null, error = null) }
    }

    fun guardarEnGaleria() {
        val actual = _estado.value.actual ?: return
        viewModelScope.launch {
            val mensaje = when {
                android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q -> R.string.guardar_no_disponible
                almacen.guardarEnGaleria(actual) -> R.string.guardada_galeria
                else -> R.string.error_guardar
            }
            _eventos.send(Evento.Aviso(texto(mensaje)))
        }
    }

    fun uriParaCompartir(creacion: Creacion) = almacen.uriParaCompartir(creacion)

    // ------------------------------------------------------------ Internos

    private suspend fun mostrar(creacion: Creacion, comentario: String?) {
        estadoGuardado[CLAVE_ACTUAL] = creacion.archivo.absolutePath
        val historial = almacen.historial()
        _estado.update { it.copy(actual = creacion, comentario = comentario, historial = historial) }
    }

    private fun ejecutar(mensaje: String, bloque: suspend () -> Unit) {
        if (trabajo?.isActive == true) return
        ultimaAccion = { ejecutar(mensaje, bloque) }
        trabajo = viewModelScope.launch {
            _estado.update { it.copy(ocupado = true, mensajeCarga = mensaje, error = null) }
            try {
                bloque()
                _estado.update { it.copy(ocupado = false, mensajeCarga = null) }
            } catch (e: ErrorGemini) {
                _estado.update { it.copy(ocupado = false, mensajeCarga = null, error = mensajeDe(e)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val detalle = e.message ?: e.javaClass.simpleName
                _estado.update {
                    it.copy(ocupado = false, mensajeCarga = null, error = texto(R.string.error_generico, detalle))
                }
            }
        }
    }

    private fun mensajeDe(e: ErrorGemini): String = when (e) {
        ErrorGemini.SinClave -> texto(R.string.error_sin_clave)
        ErrorGemini.ClaveInvalida -> texto(R.string.error_clave_invalida)
        ErrorGemini.CredencialCloudflare -> texto(R.string.error_credencial_cloudflare)
        is ErrorGemini.Cuota -> texto(R.string.error_cuota, e.modelo)
        is ErrorGemini.Permiso -> texto(R.string.error_permiso, e.modelo)
        is ErrorGemini.ModeloNoEncontrado -> texto(R.string.error_modelo, e.modelo)
        is ErrorGemini.Servidor -> texto(R.string.error_servidor, e.codigo)
        ErrorGemini.Bloqueado -> texto(R.string.error_bloqueado)
        ErrorGemini.SinImagen -> texto(R.string.error_sin_imagen)
        ErrorGemini.SinRed -> texto(R.string.error_red)
        ErrorGemini.Tiempo -> texto(R.string.error_tiempo)
        is ErrorGemini.Otro -> texto(R.string.error_generico, e.message.orEmpty())
    }

    private fun texto(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    companion object {
        private const val PREF_CLAVE = "api_key"
        private const val CLAVE_ACTUAL = "ruta_actual"
    }
}
