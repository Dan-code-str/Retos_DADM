package co.edu.unal.directorioempresas.vista

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.directorioempresas.R
import db.DatabaseHelper
import modelo.Empresa
import java.text.Normalizer // Importación necesaria para remover tildes

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: EmpresaAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var etFiltro: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        recyclerView = findViewById(R.id.recyclerViewEmpresas)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val btnNuevaEmpresa = findViewById<Button>(R.id.btnNuevaEmpresa)
        etFiltro = findViewById(R.id.etFiltro)
        val btnBuscar = findViewById<Button>(R.id.btnBuscar)

        btnNuevaEmpresa.setOnClickListener {
            startActivity(Intent(this, FormularioActivity::class.java))
        }

        btnBuscar.setOnClickListener {
            actualizarLista(etFiltro.text.toString())
        }

        // Búsqueda en tiempo real mientras el usuario escribe
        etFiltro.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                actualizarLista(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        actualizarLista(etFiltro.text.toString())
    }

    // Función que elimina los acentos ortográficos de un texto
    private fun quitarTildes(texto: String): String {
        val normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
        return normalizado.replace("\\p{M}".toRegex(), "")
    }

    private fun actualizarLista(criterio: String) {
        // Obtenemos todas las empresas de SQLite
        val listaCompleta = dbHelper.obtenerTodasLasEmpresas()

        // Realizamos el filtrado en Kotlin para manejar correctamente las tildes
        val listaFiltrada = if (criterio.isEmpty()) {
            listaCompleta
        } else {
            val criterioLimpio = quitarTildes(criterio.lowercase())

            listaCompleta.filter { empresa ->
                val nombreLimpio = quitarTildes(empresa.nombre.lowercase())
                val clasificacionLimpia = quitarTildes(empresa.clasificacion.lowercase())

                // Retorna true si el criterio coincide con el nombre o la clasificación
                nombreLimpio.contains(criterioLimpio) || clasificacionLimpia.contains(criterioLimpio)
            } as ArrayList<Empresa>
        }

        adapter = EmpresaAdapter(this, listaFiltrada,
            onEdit = { empresa ->
                val intent = Intent(this, FormularioActivity::class.java)
                intent.putExtra("ID_EMPRESA", empresa.id)
                startActivity(intent)
            },
            onDelete = { empresa ->
                dbHelper.eliminarEmpresa(empresa.id)
                actualizarLista(etFiltro.text.toString())
            }
        )
        recyclerView.adapter = adapter
    }
}