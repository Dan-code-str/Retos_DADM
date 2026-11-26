package co.edu.unal.reto12

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.OAuthProvider

/**
 * Datos del usuario que devuelve el proveedor. Nótese que aquí NO hay contraseña:
 * la app nunca la ve; solo recibe la identidad que GitHub confirma a través de Firebase.
 */
data class UsuarioAutenticado(
    val uid: String,
    val nombre: String?,
    val correo: String?,
    val usuarioGithub: String?,
    val proveedor: String,
)

/** Motivo legible de un fallo, más el detalle técnico para depurar. */
data class FalloAutenticacion(
    val motivo: String,
    val detalle: String?,
    val cancelado: Boolean = false,
)

/**
 * Encapsula la autenticación delegada en GitHub mediante Firebase Authentication.
 *
 * Flujo:
 *  1. La app pide a Firebase iniciar sesión con el proveedor "github.com".
 *  2. Firebase abre la página de inicio de sesión de GitHub en el navegador (Custom Tab).
 *  3. GitHub solicita las credenciales al usuario (y 2FA si la tiene activa).
 *  4. GitHub redirige a Firebase con un código OAuth; Firebase lo canjea por un token.
 *  5. La app recibe el resultado (éxito o error) y lo muestra.
 */
class AutenticadorGithub {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /** Sesión guardada por Firebase de una ejecución anterior (si existe). */
    fun usuarioActual(): UsuarioAutenticado? = auth.currentUser?.let { aUsuario(it, null) }

    /**
     * Si Android destruyó la actividad mientras el usuario estaba en el navegador,
     * el resultado queda pendiente y hay que recogerlo aquí.
     */
    fun resultadoPendiente(): Task<AuthResult>? = auth.pendingAuthResult

    /** Redirige al usuario a GitHub para que se autentique allá. */
    fun iniciarSesion(activity: Activity): Task<AuthResult> {
        auth.pendingAuthResult?.let { return it }

        val proveedor = OAuthProvider.newBuilder("github.com")
            // Permisos mínimos: leer el perfil público y el correo.
            .setScopes(listOf("read:user", "user:email"))
            .build()

        return auth.startActivityForSignInWithProvider(activity, proveedor)
    }

    fun cerrarSesion() = auth.signOut()

    fun aUsuario(user: FirebaseUser, resultado: AuthResult?): UsuarioAutenticado {
        val perfil = resultado?.additionalUserInfo?.profile
        val datosGithub = user.providerData.firstOrNull { it.providerId == "github.com" }
        return UsuarioAutenticado(
            uid = user.uid,
            nombre = user.displayName ?: datosGithub?.displayName ?: perfil?.get("name") as? String,
            correo = user.email ?: datosGithub?.email,
            usuarioGithub = resultado?.additionalUserInfo?.username ?: perfil?.get("login") as? String,
            proveedor = "GitHub",
        )
    }

    companion object {
        /**
         * Firebase se inicializa solo si el proyecto se compiló con app/google-services.json.
         * Si falta, cualquier llamada a FirebaseAuth fallaría, así que se verifica antes.
         */
        fun firebaseConfigurado(context: Context): Boolean =
            FirebaseApp.getApps(context).isNotEmpty()

        /** Traduce las excepciones de Firebase a un mensaje que el usuario entienda. */
        fun describir(e: Exception): FalloAutenticacion {
            val codigo = (e as? FirebaseAuthException)?.errorCode
            return when {
                codigo == "ERROR_WEB_CONTEXT_CANCELED" -> FalloAutenticacion(
                    motivo = "Cerraste la página de GitHub antes de terminar el inicio de sesión.",
                    detalle = null,
                    cancelado = true,
                )
                e is FirebaseNetworkException -> FalloAutenticacion(
                    "No hay conexión a internet. Revisa tu red e inténtalo de nuevo.",
                    e.localizedMessage,
                )
                e is FirebaseTooManyRequestsException -> FalloAutenticacion(
                    "Demasiados intentos seguidos. Espera un momento antes de reintentar.",
                    e.localizedMessage,
                )
                e is FirebaseAuthUserCollisionException -> FalloAutenticacion(
                    "Ya existe una cuenta con ese correo usando otro método de inicio de sesión.",
                    e.localizedMessage,
                )
                codigo == "ERROR_OPERATION_NOT_ALLOWED" -> FalloAutenticacion(
                    "El inicio de sesión con GitHub no está habilitado en el proyecto de Firebase.",
                    e.localizedMessage,
                )
                codigo == "ERROR_WEB_CONTEXT_ALREADY_PRESENTED" -> FalloAutenticacion(
                    "Ya hay un inicio de sesión en curso. Termínalo o ciérralo primero.",
                    e.localizedMessage,
                )
                codigo == "ERROR_USER_DISABLED" -> FalloAutenticacion(
                    "Esta cuenta fue deshabilitada por el administrador.",
                    e.localizedMessage,
                )
                else -> FalloAutenticacion(
                    "GitHub no pudo confirmar tu identidad.",
                    listOfNotNull(codigo, e.localizedMessage).joinToString(" · ").ifBlank { null },
                )
            }
        }
    }
}
