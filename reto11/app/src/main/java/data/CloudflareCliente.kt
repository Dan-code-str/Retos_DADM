package co.edu.unal.reto11.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.random.Random

/**
 * Generación de imágenes con Cloudflare Workers AI (modelo FLUX.1 [schnell] por defecto).
 * El plan gratuito da 10.000 "neurons" diarios; cada imagen 1024×1024 con 4 pasos gasta ~58 (~170 al día).
 *
 * POST https://api.cloudflare.com/client/v4/accounts/{cuenta}/ai/run/{modelo}
 * Respuesta: {"result": {"image": "<jpeg en base64>"}, "success": true, "errors": []}
 */
class CloudflareCliente(
    private val cuenta: String,
    private val token: String,
    private val modelo: String,
) : GeneradorImagenes {

    override val nombre: String get() = "Cloudflare Workers AI · ${modelo.substringAfterLast('/')}"
    override val admiteFormato = false // FLUX schnell siempre entrega 1024×1024
    override val editaImagenes = false

    override suspend fun generar(
        prompt: String,
        relacionAspecto: String,
        imagenBase: ImagenGenerada?,
        semilla: Long?,
    ): ImagenGenerada = withContext(Dispatchers.IO) {
        if (cuenta.isBlank() || token.isBlank()) throw ErrorGemini.CredencialCloudflare
        val semillaUsada = semilla ?: Random.nextInt(1, Int.MAX_VALUE).toLong()
        val cuerpo = JSONObject()
            .put("prompt", prompt.take(2048))
            .put("steps", PASOS)

        val conexion = (URL("$BASE_URL/accounts/$cuenta/ai/run/$modelo").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conexion.outputStream.use { it.write(cuerpo.toString().toByteArray(Charsets.UTF_8)) }
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (codigo !in 200..299) throw traducirError(codigo, texto)

            val base64 = JSONObject(texto).optJSONObject("result")?.optString("image").orEmpty()
            if (base64.isBlank()) throw ErrorGemini.SinImagen
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            ImagenGenerada(bytes, detectarMime(bytes), semilla = semillaUsada)
        } catch (e: ErrorGemini) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw ErrorGemini.Tiempo
        } catch (e: IOException) {
            throw ErrorGemini.SinRed
        } finally {
            conexion.disconnect()
        }
    }

    private fun traducirError(codigo: Int, cuerpo: String): ErrorGemini {
        val mensaje = runCatching {
            JSONObject(cuerpo).optJSONArray("errors")?.optJSONObject(0)?.optString("message")
        }.getOrNull().orEmpty()
        return when {
            codigo == 401 || codigo == 403 -> ErrorGemini.CredencialCloudflare
            codigo == 429 -> ErrorGemini.Cuota("Cloudflare $modelo")
            mensaje.contains("NSFW", ignoreCase = true) -> ErrorGemini.Bloqueado
            codigo == 404 -> ErrorGemini.ModeloNoEncontrado(modelo)
            codigo >= 500 -> ErrorGemini.Servidor(codigo)
            else -> ErrorGemini.Otro(mensaje.ifBlank { "HTTP $codigo" })
        }
    }

    private fun detectarMime(bytes: ByteArray): String =
        if (bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"

    companion object {
        private const val BASE_URL = "https://api.cloudflare.com/client/v4"
        // FLUX schnell está optimizado para 4 pasos: ~2,5 s por imagen (con 6 tardaba ~5,5 s)
        private const val PASOS = 4
    }
}
