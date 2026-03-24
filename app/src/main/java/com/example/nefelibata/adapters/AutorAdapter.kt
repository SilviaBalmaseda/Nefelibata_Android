package com.example.nefelibata.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.nefelibata.databinding.ItemAutorBinding
import com.example.nefelibata.models.Usuario

class AutorAdapter(
    private var listaAutores: List<Usuario>,
    private val onAutorClick: (Usuario) -> Unit
) : RecyclerView.Adapter<AutorAdapter.AutorViewHolder>() {

    class AutorViewHolder(val binding: ItemAutorBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutorViewHolder {
        val binding = ItemAutorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AutorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AutorViewHolder, position: Int) {
        val autor = listaAutores[position]
        with(holder.binding) {
            tvAutorNombre.text = autor.nombre
            
            ivAutorFoto.load(autor.fotoUser.takeIf { it.isNotEmpty() } ?: android.R.drawable.ic_menu_gallery) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }

            root.setOnClickListener { onAutorClick(autor) }
        }
    }

    override fun getItemCount(): Int = listaAutores.size

    fun actualizarDatos(nuevaLista: List<Usuario>) {
        listaAutores = nuevaLista
        notifyDataSetChanged()
    }
}
