package co.edu.unal.reto11.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Imagen devuelta por el modelo, con el texto que a veces la acompaña y la semilla usada. */
class ImagenGenerada(
    val bytes: ByteArray,
    val mimeType: String,
    val comentario: String? = null,
    val semilla: Long? = null,
)

/** Errores del servicio, traducidos a mensajes en [co.edu.unal.reto11.GeneradorViewModel]. */
sealed class ErrorGemini(mensaje: String) : Exception(mensaje) {
    object SinClave : ErrorGemini("Sin API key")
    object ClaveInvalida : ErrorGemini("API key inválida")
    object CredencialCloudflare : ErrorGemini("Token o Account ID de Cloudflare inválido")
    class Cuota(val modelo: String) : ErrorGemini("Cuota agotada para $modelo")
    class Permiso(val modelo: String) : ErrorGemini("Sin permiso para $modelo")
    class ModeloNoEncontrado(val modelo: String) : ErrorGemini("Modelo no encontrado: $modelo")
    class Servidor(val codigo: Int) : ErrorGemini("Error del servidor $codigo")
    object Bloqueado : ErrorGemini("Contenido bloqueado")
    object SinImagen : ErrorGemini("Respuesta sin imagen")
    object SinRed : ErrorGemini("Sin conexión")
    object Tiempo : ErrorGemini("Tiempo de espera agotado")
    class Otro(detalle: String) : ErrorGemini(detalle)
}

/**
 * Cliente mínimo de la API REST de Gemini (generativelanguage.googleapis.com) usando solo
 * HttpURLConnection y org.json, sin librerías adicionales.
 *
 * - [generar]: modelo de imagen nativo de Gemini ("Nano Banana"). Si se le pasa una
 *   imagen base, la edita según la instrucción (refinamiento conversacional).
 * - [mejorarIdea]: modelo de texto que reescribe la idea del usuario como un prompt detallado.
 */
class GeminiCliente(
    private val apiKey: String,
    private val modeloImagen: String,
    private val modeloTexto: String,
) : GeneradorImagenes {

    override val nombre: String get() = "Google Gemini · $modeloImagen"
    override val admiteFormato = true
    override val editaImagenes = true

    override suspend fun generar(
        prompt: String,
        relacionAspecto: String,
        imagenBase: ImagenGenerada?,
        semilla: Long?,
    ): ImagenGenerada = withContext(Dispatchers.IO) {
        val partes = JSONArray()
        if (imagenBase != null) {
            partes.put(
                JSONObject().put(
                    "inlineData", JSONObject()
                        .put("mimeType", imagenBase.mimeType)
                        .put("data", Base64.encodeToString(imagenBase.bytes, Base64.NO_WRAP))
                )
            )
        }
        partes.put(JSONObject().put("text", prompt))

        val cuerpo = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", partes)))
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                    .put("imageConfig", JSONObject().put("aspectRatio", relacionAspecto))
            )

        val respuesta = enviar(modeloImagen, cuerpo)
        extraerImagen(respuesta)
    }

    suspend fun mejorarIdea(idea: String, estilo: String): String = withContext(Dispatchers.IO) {
        val instrucciones = """
            Eres un director de arte experto en escribir prompts para modelos de generación de imágenes.
            Reescribe la idea del usuario como UN solo prompt en español, vívido y concreto: sujeto principal,
            acción, entorno, composición, iluminación y detalles visuales. Respeta la intención original y el
            estilo indicado. Máximo 60 palabras. Responde únicamente con el prompt, sin comillas ni títulos.
        """.trimIndent()

        val cuerpo = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instrucciones))))
            .put(
                "contents", JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts", JSONArray().put(JSONObject().put("text", "Idea: $idea\nEstilo deseado: $estilo"))
                    )
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0.9))

        val respuesta = enviarTexto(cuerpo)
        val texto = extraerTexto(respuesta)?.trim()?.trim('"', '“', '”')
        if (texto.isNullOrBlank()) throw ErrorGemini.Otro("Gemini no devolvió una idea mejorada.") else texto
    }

    /** Traduce el prompt al inglés (FLUX entiende mejor el inglés). */
    suspend fun traducirAIngles(prompt: String): String = withContext(Dispatchers.IO) {
        val instrucciones = "Translate the following image-generation prompt into natural English. " +
            "Keep every visual detail. Reply with the translated prompt only."
        val cuerpo = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instrucciones))))
            .put(
                "contents", JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0.2))
        val texto = extraerTexto(enviarTexto(cuerpo, tiempoLectura = 25_000))?.trim()
        if (texto.isNullOrBlank()) throw ErrorGemini.Otro("Traducción vacía") else texto
    }

    // ---------------------------------------------------------------- HTTP

    /**
     * Para las llamadas de texto: si Gemini responde 5xx (saturado) o se corta la conexión,
     * reintenta y luego prueba con un modelo de respaldo más liviano.
     */
    private fun enviarTexto(cuerpo: JSONObject, tiempoLectura: Int = 120_000): JSONObject {
        var ultimoError: ErrorGemini = ErrorGemini.Servidor(503)
        for (modelo in listOf(modeloTexto, MODELO_TEXTO_RESPALDO).distinct()) {
            repeat(2) { intento ->
                try {
                    return enviar(modelo, cuerpo, tiempoLectura)
                } catch (e: ErrorGemini.Servidor) {
                    ultimoError = e
                } catch (e: ErrorGemini.Otro) {
                    ultimoError = e
                }
                Thread.sleep(1_500L * (intento + 1))
            }
        }
        throw ultimoError
    }

    private fun enviar(modelo: String, cuerpo: JSONObject, tiempoLectura: Int = 120_000): JSONObject {
        if (apiKey.isBlank()) throw ErrorGemini.SinClave
        val url = URL("$BASE_URL/models/$modelo:generateContent")
        val conexion = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = tiempoLectura // generar una imagen puede tardar
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (codigo !in 200..299) throw traducirError(codigo, texto, modelo)
            return JSONObject(texto)
        } catch (e: ErrorGemini) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw ErrorGemini.Tiempo
        } catch (e: IOException) {
            throw ErrorGemini.Otro("Sin conexión con Gemini (${e.javaClass.simpleName}: ${e.message.orEmpty()})")
        } finally {
            conexion.disconnect()
        }
    }

    private fun traducirError(codigo: Int, cuerpo: String, modelo: String): ErrorGemini {
        val error = runCatching { JSONObject(cuerpo).optJSONObject("error") }.getOrNull()
        val mensaje = error?.optString("message").orEmpty()
        val estado = error?.optString("status").orEmpty()
        return when {
            codigo == 400 && (mensaje.contains("API key", ignoreCase = true) || cuerpo.contains("API_KEY_INVALID")) ->
                ErrorGemini.ClaveInvalida
            codigo == 401 -> ErrorGemini.ClaveInvalida
            codigo == 429 || estado == "RESOURCE_EXHAUSTED" -> ErrorGemini.Cuota(modelo)
            codigo == 403 -> ErrorGemini.Permiso(modelo)
            codigo == 404 -> ErrorGemini.ModeloNoEncontrado(modelo)
            codigo >= 500 -> ErrorGemini.Servidor(codigo)
            else -> ErrorGemini.Otro(mensaje.ifBlank { "HTTP $codigo" })
        }
    }

    // ------------------------------------------------------------ Respuesta

    private fun extraerImagen(respuesta: JSONObject): ImagenGenerada {
        if (respuesta.optJSONObject("promptFeedback")?.has("blockReason") == true) throw ErrorGemini.Bloqueado

        val candidato = respuesta.optJSONArray("candidates")?.optJSONObject(0) ?: throw ErrorGemini.SinImagen
        val partes = candidato.optJSONObject("content")?.optJSONArray("parts")
        var imagen: ByteArray? = null
        var mime = "image/png"
        val comentario = StringBuilder()

        if (partes != null) {
            for (i in 0 until partes.length()) {
                val parte = partes.optJSONObject(i) ?: continue
                if (parte.optBoolean("thought", false)) continue
                val datos = parte.optJSONObject("inlineData") ?: parte.optJSONObject("inline_data")
                if (datos != null && imagen == null) {
                    imagen = Base64.decode(datos.optString("data"), Base64.DEFAULT)
                    mime = datos.optString("mimeType", datos.optString("mime_type", mime))
                } else if (parte.has("text")) {
                    comentario.append(parte.optString("text")).append(' ')
                }
            }
        }

        if (imagen == null) {
            val motivo = candidato.optString("finishReason")
            if (motivo in MOTIVOS_BLOQUEO) throw ErrorGemini.Bloqueado
            throw ErrorGemini.SinImagen
        }
        return ImagenGenerada(imagen, mime, comentario.toString().trim().ifBlank { null })
    }

    private fun extraerTexto(respuesta: JSONObject): String? {
        val partes = respuesta.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        val texto = StringBuilder()
        for (i in 0 until partes.length()) {
            val parte = partes.optJSONObject(i) ?: continue
            if (!parte.optBoolean("thought", false)) texto.append(parte.optString("text"))
        }
        return texto.toString()
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        // El modelo principal de texto es el "lite" (sin razonamiento, ~1-2 s);
        // si está saturado se usa el Flash completo, que razona y tarda ~7 s.
        private const val MODELO_TEXTO_RESPALDO = "gemini-flash-latest"
        private val MOTIVOS_BLOQUEO = setOf(
            "SAFETY", "IMAGE_SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "RECITATION", "IMAGE_PROHIBITED_CONTENT"
        )
    }
}
