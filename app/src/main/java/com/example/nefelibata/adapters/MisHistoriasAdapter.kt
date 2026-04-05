package com.example.nefelibata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.nefelibata.R
import com.example.nefelibata.databinding.ItemMiHistoriaBinding
import com.example.nefelibata.models.Historia
import com.example.nefelibata.utils.Constants
import com.google.firebase.storage.FirebaseStorage

/**
 * Adaptador para el listado de "Mis Historias" del autor.
 */
class MisHistoriasAdapter(
    private var listaHistorias: List<Historia>,
    private val onEditClick: (Historia) -> Unit,
    private val onDeleteClick: (Historia) -> Unit
) : RecyclerView.Adapter<MisHistoriasAdapter.MiHistoriaViewHolder>() {

    class MiHistoriaViewHolder(val binding: ItemMiHistoriaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MiHistoriaViewHolder {
        val binding = ItemMiHistoriaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MiHistoriaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MiHistoriaViewHolder, position: Int) {
        val historia = listaHistorias[position]
        val context = holder.itemView.context
        
        with(holder.binding) {
            tvMiHistoriaTitulo.text = historia.titulo
            
            // Traducción del estado de la historia
            val estadoValor = historia.obtenerEstadoValidado()
            val resId = Constants.ESTADOS_MAP[estadoValor] ?: R.string.status_pendiente
            tvMiHistoriaEstado.text = context.getString(R.string.status_prefix, context.getString(resId))

            // --- GESTIÓN DE IMAGEN POR DEFECTO ---
            ivMiHistoriaPortada.tag = historia.idHistoria
            ivMiHistoriaPortada.visibility = View.VISIBLE
            
            val defaultImg = Constants.PORTADA_DEFECTO

            if (historia.imagenUrl.isEmpty()) {
                // Si no hay imagen, forzamos la carga del icono por defecto y limpiamos Coil
                ivMiHistoriaPortada.load(defaultImg)
            } else {
                // Si hay imagen, cargamos la de Firebase Storage
                val currentId = historia.idHistoria
                FirebaseStorage.getInstance().getReference(historia.imagenUrl).downloadUrl
                    .addOnSuccessListener { uri ->
                        if (ivMiHistoriaPortada.tag == currentId) {
                            ivMiHistoriaPortada.load(uri) {
                                crossfade(true)
                                placeholder(defaultImg)
                                error(defaultImg)
                            }
                        }
                    }
                    .addOnFailureListener {
                        if (ivMiHistoriaPortada.tag == currentId) {
                            ivMiHistoriaPortada.load(defaultImg)
                        }
                    }
            }

            ivMiHistoriaEdit.setOnClickListener { onEditClick(historia) }
            ivMiHistoriaDelete.setOnClickListener { onDeleteClick(historia) }
        }
    }

    override fun getItemCount(): Int = listaHistorias.size

    fun actualizarDatos(nuevaLista: List<Historia>) {
        listaHistorias = nuevaLista
        notifyDataSetChanged()
    }
}
