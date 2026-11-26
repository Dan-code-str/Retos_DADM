package co.edu.unal.reto12

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import co.edu.unal.reto12.databinding.ActivityMainBinding

/**
 * Reto 12 — Delegando la autenticación.
 *
 * La app no tiene campos de usuario ni contraseña: al tocar el botón se redirige a GitHub,
 * que es quien pide las credenciales. Al volver se muestra si la autenticación fue exitosa o fallida.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val vm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        b.btnGithub.setOnClickListener { vm.iniciarSesion(this) }
        b.btnReintentar.setOnClickListener { vm.iniciarSesion(this) }
        b.btnVolver.setOnClickListener { vm.volverAlInicio() }
        b.btnCerrarSesion.setOnClickListener { vm.cerrarSesion() }

        vm.estado.observe(this) { mostrar(it) }
        vm.aviso.observe(this) { texto ->
            if (texto != null) {
                Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
                vm.avisoMostrado()
            }
        }
    }

    private fun mostrar(estado: EstadoAuth) {
        val enInicio = estado is EstadoAuth.SinSesion ||
            estado is EstadoAuth.Redirigiendo ||
            estado is EstadoAuth.FirebaseSinConfigurar

        b.vistaInicio.isVisible = enInicio
        b.vistaExito.isVisible = estado is EstadoAuth.Exitoso
        b.vistaFallo.isVisible = estado is EstadoAuth.Fallido

        when (estado) {
            EstadoAuth.SinSesion -> botonGithub(cargando = false)
            EstadoAuth.Redirigiendo -> botonGithub(cargando = true)
            EstadoAuth.FirebaseSinConfigurar -> {
                botonGithub(cargando = false)
                b.txtSinConfigurar.isVisible = true
            }
            is EstadoAuth.Exitoso -> mostrarExito(estado)
            is EstadoAuth.Fallido -> mostrarFallo(estado.fallo)
        }
        if (estado !is EstadoAuth.FirebaseSinConfigurar) b.txtSinConfigurar.isVisible = false
        b.scroll.scrollTo(0, 0)
    }

    private fun botonGithub(cargando: Boolean) {
        b.btnGithub.isEnabled = !cargando
        b.btnGithub.text = getString(if (cargando) R.string.esperando_github else R.string.continuar_github)
        b.progresoGithub.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun mostrarExito(estado: EstadoAuth.Exitoso) {
        val u = estado.usuario
        b.txtTituloExito.setText(if (estado.reciente) R.string.exito_titulo else R.string.sesion_activa_titulo)
        b.txtSubtituloExito.setText(if (estado.reciente) R.string.exito_subtitulo else R.string.sesion_activa_subtitulo)

        val nombreVisible = u.nombre ?: u.usuarioGithub ?: getString(R.string.usuario_github)
        b.txtAvatar.text = nombreVisible.trim().firstOrNull()?.uppercase() ?: "?"
        b.txtNombre.text = nombreVisible
        b.txtUsuario.isVisible = u.usuarioGithub != null
        b.txtUsuario.text = getString(R.string.arroba_usuario, u.usuarioGithub ?: "")
        b.txtProveedor.text = u.proveedor
        b.txtCorreo.text = u.correo ?: getString(R.string.correo_no_compartido)
        b.txtUid.text = u.uid
    }

    private fun mostrarFallo(fallo: FalloAutenticacion) {
        val color = getColor(if (fallo.cancelado) R.color.ambar else R.color.rojo)
        val fondo = getColor(if (fallo.cancelado) R.color.ambar_suave else R.color.rojo_suave)

        b.txtIconoFallo.backgroundTintList = ColorStateList.valueOf(color)
        b.txtTituloFallo.setTextColor(color)
        b.txtMotivoFallo.text = fallo.motivo
        b.txtDetalleFallo.isVisible = fallo.detalle != null
        b.txtDetalleFallo.text = fallo.detalle
        b.txtDetalleFallo.setTextColor(color)
        b.txtDetalleFallo.backgroundTintList = ColorStateList.valueOf(fondo)
    }
}
