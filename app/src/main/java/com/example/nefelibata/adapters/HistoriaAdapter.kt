package com.example.nefelibata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.models.Historia
import com.google.android.material.button.MaterialButton

class HistoriaAdapter(private var listaHistorias: List<Historia>) :
    RecyclerView.Adapter<HistoriaAdapter.HistoriaViewHolder>() {

    class HistoriaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tv_story_title)
        val tvAutor: TextView = view.findViewById(R.id.tv_story_author)
        val ivImagen: ImageView = view.findViewById(R.id.iv_story_image)
        // Buscamos el segundo botón dentro del LinearLayout de botones (el de la estrella)
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
        // Accedemos al objeto Autor -> campo nombre
        holder.tvAutor.text = historia.autor.nombre
        holder.btnFavoritos.text = historia.numFavoritos.toString()

        // La imagen se queda con el placeholder del XML
    }

    override fun getItemCount(): Int = listaHistorias.size

    fun actualizarDatos(nuevasHistorias: List<Historia>) {
        listaHistorias = nuevasHistorias
        notifyDataSetChanged()
    }
}