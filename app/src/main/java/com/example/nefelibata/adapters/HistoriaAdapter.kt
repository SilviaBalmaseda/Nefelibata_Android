package com.example.nefelibata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.nefelibata.R
import com.example.nefelibata.models.Historia
import com.google.android.material.button.MaterialButton
import com.google.firebase.storage.FirebaseStorage

class HistoriaAdapter(private var listaHistorias: List<Historia>) :
    RecyclerView.Adapter<HistoriaAdapter.HistoriaViewHolder>() {

    class HistoriaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tv_story_title)
        val tvAutor: TextView = view.findViewById(R.id.tv_story_author)
        val ivImagen: ImageView = view.findViewById(R.id.iv_story_image)
        val btnFavoritos: MaterialButton = view.findViewById<LinearLayout>(R.id.ll_buttons).run {
            getChildAt(1) as MaterialButton
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoriaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_historia, parent, false)
        return HistoriaViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoriaViewHolder, position: Int) {
        val historia = listaHistorias[position]

        holder.tvTitulo.text = historia.titulo
        holder.tvAutor.text = historia.autor.nombre
        holder.btnFavoritos.text = historia.numFavoritos.toString()

        // Lógica con Coil y Firebase Storage
        if (historia.imagenUrl.isNotEmpty()) {
            val storageRef = FirebaseStorage.getInstance().getReference(historia.imagenUrl)
            
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                holder.ivImagen.load(uri) {
                    crossfade(true)
                    placeholder(R.drawable.logo)
                    error(android.R.drawable.stat_notify_error)
                }
            }.addOnFailureListener {
                holder.ivImagen.setImageResource(R.drawable.logo)
            }
        } else {
            holder.ivImagen.setImageResource(R.drawable.logo)
        }
    }

    override fun getItemCount(): Int = listaHistorias.size

    fun actualizarDatos(nuevasHistorias: List<Historia>) {
        listaHistorias = nuevasHistorias
        notifyDataSetChanged()
    }
}