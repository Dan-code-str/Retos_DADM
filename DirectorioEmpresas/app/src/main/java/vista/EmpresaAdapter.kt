package co.edu.unal.directorioempresas.vista

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import co.edu.unal.directorioempresas.R
import modelo.Empresa

class EmpresaAdapter(
    private val context: Context,
    private var listaEmpresas: ArrayList<Empresa>,
    private val onEdit: (Empresa) -> Unit,
    private val onDelete: (Empresa) -> Unit
) : RecyclerView.Adapter<EmpresaAdapter.EmpresaViewHolder>() {

    class EmpresaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreEmpresa)
        val tvClasificacion: TextView = itemView.findViewById(R.id.tvClasificacionEmpresa)
        val btnEditar: Button = itemView.findViewById(R.id.btnEditar)
        val btnEliminar: Button = itemView.findViewById(R.id.btnEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmpresaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_empresa, parent, false)
        return EmpresaViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmpresaViewHolder, position: Int) {
        val empresa = listaEmpresas[position]
        holder.tvNombre.text = empresa.nombre
        holder.tvClasificacion.text = empresa.clasificacion

        // Acción del botón Editar
        holder.btnEditar.setOnClickListener { onEdit(empresa) }

        // Acción del botón Eliminar con mensaje de confirmación (Requisito del reto)
        holder.btnEliminar.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Eliminar Empresa")
                .setMessage("¿Estás seguro de que deseas eliminar a ${empresa.nombre}?")
                .setPositiveButton("Sí") { _, _ -> onDelete(empresa) }
                .setNegativeButton("No", null)
                .show()
        }
    }

    override fun getItemCount(): Int = listaEmpresas.size
}