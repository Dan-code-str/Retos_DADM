package co.edu.unal.directorioempresas.vista

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.edu.unal.directorioempresas.R
import db.DatabaseHelper
import modelo.Empresa

class FormularioActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var idEmpresa: Int = 0

    // Definimos las clasificaciones permitidas por el reto
    private val opcionesClasificacion = arrayOf(
        "Consultoría",
        "Desarrollo a la medida",
        "Fábrica de software",
        "Consultoría y Desarrollo", // Agregadas combinaciones por el "y/o" del enunciado
        "Todas las anteriores"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_formulario)

        dbHelper = DatabaseHelper(this)

        val etNombre = findViewById<EditText>(R.id.etNombre)
        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etTelefono = findViewById<EditText>(R.id.etTelefono)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etProductos = findViewById<EditText>(R.id.etProductos)
        val spClasificacion = findViewById<Spinner>(R.id.spClasificacion) // Cambiado a Spinner
        val btnGuardar = findViewById<Button>(R.id.btnGuardar)

        // Configurar el Spinner (Dropdown)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opcionesClasificacion)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spClasificacion.adapter = adapter

        idEmpresa = intent.getIntExtra("ID_EMPRESA", 0)

        if (idEmpresa > 0) {
            btnGuardar.text = "Actualizar Empresa"
            val empresaAEditar = dbHelper.obtenerTodasLasEmpresas().find { it.id == idEmpresa }

            empresaAEditar?.let {
                etNombre.setText(it.nombre)
                etUrl.setText(it.url)
                etTelefono.setText(it.telefono)
                etEmail.setText(it.email)
                etProductos.setText(it.productos)

                // Buscar la posición de la clasificación actual en el arreglo y seleccionarla
                val posicion = opcionesClasificacion.indexOf(it.clasificacion)
                if (posicion > -1) {
                    spClasificacion.setSelection(posicion)
                }
            }
        }

        btnGuardar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()
            val url = etUrl.text.toString().trim()
            val telefono = etTelefono.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val productos = etProductos.text.toString().trim()
            // Obtener el valor seleccionado en el Spinner
            val clasificacion = spClasificacion.selectedItem.toString()

            if (nombre.isEmpty()) {
                Toast.makeText(this, "El nombre de la empresa es obligatorio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val empresa = Empresa(
                id = idEmpresa,
                nombre = nombre,
                url = url,
                telefono = telefono,
                email = email,
                productos = productos,
                clasificacion = clasificacion
            )

            if (idEmpresa > 0) {
                dbHelper.actualizarEmpresa(empresa)
                Toast.makeText(this, "Empresa actualizada", Toast.LENGTH_SHORT).show()
            } else {
                dbHelper.insertarEmpresa(empresa)
                Toast.makeText(this, "Empresa guardada", Toast.LENGTH_SHORT).show()
            }

            finish()
        }
    }
}