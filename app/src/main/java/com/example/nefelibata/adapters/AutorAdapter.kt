package com.example.nefelibata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.nefelibata.R
import com.example.nefelibata.models.Usuario
import com.google.android.material.imageview.ShapeableImageView

class AutorAdapter(
    private var listaAutores: List<Usuario>,
    private val onAutorClick: (Usuario) -> Unit
) : RecyclerView.Adapter<AutorAdapter.AutorViewHolder>() {

    class AutorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ShapeableImageView = view.findViewById(R.id.iv_autor_foto)
        val tvNombre: TextView = view.findViewById(R.id.tv_autor_nombre)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_autor, parent, false)
        return AutorViewHolder(view)
    }

    override fun onBindViewHolder(holder: AutorViewHolder, position: Int) {
        val autor = listaAutores[position]
        holder.tvNombre.text = autor.nombre
        
        if (autor.fotoUser.isNotEmpty()) {
            holder.ivFoto.load(autor.fotoUser) {
                crossfade(true)
                transformations(CircleCropTransformation())
                placeholder(android.R.drawable.ic_menu_gallery)
            }
        } else {
            holder.ivFoto.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener { onAutorClick(autor) }
    }

    override fun getItemCount(): Int = listaAutores.size

    fun actualizarDatos(nuevaLista: List<Usuario>) {
        listaAutores = nuevaLista
        notifyDataSetChanged()
    }
}