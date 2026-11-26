package co.edu.unal.reto12

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult

sealed interface EstadoAuth {
    data object SinSesion : EstadoAuth
    data object Redirigiendo : EstadoAuth
    data class Exitoso(val usuario: UsuarioAutenticado, val reciente: Boolean) : EstadoAuth
    data class Fallido(val fallo: FalloAutenticacion) : EstadoAuth
    data object FirebaseSinConfigurar : EstadoAuth
}

/**
 * Guarda el estado de la pantalla para que sobreviva a rotaciones y a que
 * Android recree la actividad mientras el usuario está en el navegador.
 */
class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val autenticador = AutenticadorGithub()
    private val configurado = AutenticadorGithub.firebaseConfigurado(app)

    private val _estado = MutableLiveData<EstadoAuth>()
    val estado: LiveData<EstadoAuth> = _estado

    /** Mensaje corto (Toast) que se muestra una sola vez al terminar el proceso. */
    private val _aviso = MutableLiveData<String?>()
    val aviso: LiveData<String?> = _aviso

    init {
        if (!configurado) {
            _estado.value = EstadoAuth.FirebaseSinConfigurar
        } else {
            _estado.value = autenticador.usuarioActual()
                ?.let { EstadoAuth.Exitoso(it, reciente = false) }
                ?: EstadoAuth.SinSesion
            autenticador.resultadoPendiente()?.let { escuchar(it) }
        }
    }

    fun iniciarSesion(activity: Activity) {
        if (!configurado) {
            _estado.value = EstadoAuth.FirebaseSinConfigurar
            return
        }
        if (_estado.value is EstadoAuth.Redirigiendo) return
        escuchar(autenticador.iniciarSesion(activity))
    }

    private fun escuchar(tarea: Task<AuthResult>) {
        _estado.value = EstadoAuth.Redirigiendo
        tarea
            .addOnSuccessListener { resultado ->
                val user = resultado.user
                if (user == null) {
                    _estado.value = EstadoAuth.Fallido(
                        FalloAutenticacion("Firebase no devolvió un usuario.", null)
                    )
                    _aviso.value = "Autenticación fallida"
                } else {
                    _estado.value = EstadoAuth.Exitoso(autenticador.aUsuario(user, resultado), reciente = true)
                    _aviso.value = "¡Autenticación exitosa!"
                }
            }
            .addOnFailureListener { e ->
                _estado.value = EstadoAuth.Fallido(AutenticadorGithub.describir(e))
                _aviso.value = "Autenticación fallida"
            }
    }

    fun cerrarSesion() {
        if (configurado) autenticador.cerrarSesion()
        _estado.value = EstadoAuth.SinSesion
        _aviso.value = "Sesión cerrada"
    }

    fun volverAlInicio() {
        _estado.value = if (configurado) EstadoAuth.SinSesion else EstadoAuth.FirebaseSinConfigurar
    }

    fun avisoMostrado() {
        _aviso.value = null
    }
}
