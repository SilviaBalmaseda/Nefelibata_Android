package com.example.nefelibata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.nefelibata.R
import com.example.nefelibata.models.Historia
import com.google.firebase.storage.FirebaseStorage

class MisHistoriasAdapter(
    private var listaHistorias: List<Historia>,
    private val onEditClick: (Historia) -> Unit,
    private val onDeleteClick: (Historia) -> Unit
) : RecyclerView.Adapter<MisHistoriasAdapter.MiHistoriaViewHolder>() {

    class MiHistoriaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPortada: ImageView = view.findViewById(R.id.iv_mi_historia_portada)
        val tvTitulo: TextView = view.findViewById(R.id.tv_mi_historia_titulo)
        val tvEstado: TextView = view.findViewById(R.id.tv_mi_historia_estado)
        val ivEdit: ImageView = view.findViewById(R.id.iv_mi_historia_edit)
        val ivDelete: ImageView = view.findViewById(R.id.iv_mi_historia_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MiHistoriaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mi_historia, parent, false)
        return MiHistoriaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MiHistoriaViewHolder, position: Int) {
        val historia = listaHistorias[position]
        holder.tvTitulo.text = historia.titulo
        holder.tvEstado.text = "Estado: ${historia.obtenerEstadoValidado()}"

        // --- SOLUCIÓN AL ERROR DE IMÁGENES RECICLADAS Y PLACEHOLDER ---
        holder.ivPortada.setImageResource(android.R.drawable.ic_menu_gallery)
        val currentId = historia.idHistoria
        holder.ivPortada.tag = currentId

        if (historia.imagenUrl.isNotEmpty()) {
            val storageRef = FirebaseStorage.getInstance().getReference(historia.imagenUrl)
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                if (holder.ivPortada.tag == currentId) {
                    holder.ivPortada.load(uri) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_gallery)
                    }
                }
            }.addOnFailureListener {
                if (holder.ivPortada.tag == currentId) {
                    holder.ivPortada.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }

        holder.ivEdit.setOnClickListener { onEditClick(historia) }
        holder.ivDelete.setOnClickListener { onDeleteClick(historia) }
    }

    override fun getItemCount(): Int = listaHistorias.size

    fun actualizarDatos(nuevaLista: List<Historia>) {
        listaHistorias = nuevaLista
        notifyDataSetChanged()
    }
}