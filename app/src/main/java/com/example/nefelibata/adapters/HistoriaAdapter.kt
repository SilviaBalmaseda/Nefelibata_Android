package com.example.nefelibata.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.nefelibata.R
import com.example.nefelibata.models.Historia
import com.example.nefelibata.ui.LeerHistoriaActivity
import com.example.nefelibata.ui.SynopsisDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.storage.FirebaseStorage

class HistoriaAdapter(
    private var listaHistorias: List<Historia>,
    private var listaFavoritosUsuario: List<String>,
    private val onFavoritoClick: (Historia) -> Unit
) : RecyclerView.Adapter<HistoriaAdapter.HistoriaViewHolder>() {

    class HistoriaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tv_story_title)
        val tvAutor: TextView = view.findViewById(R.id.tv_story_author)
        val ivImagen: ImageView = view.findViewById(R.id.iv_story_image)
        val btnLeer: MaterialButton = view.findViewById<LinearLayout>(R.id.ll_buttons).run {
            getChildAt(0) as MaterialButton
        }
        val btnFavoritos: MaterialButton = view.findViewById<LinearLayout>(R.id.ll_buttons).run {
            getChildAt(1) as MaterialButton
        }
        val btnSinopsis: MaterialButton = view.findViewById<LinearLayout>(R.id.ll_buttons).run {
            getChildAt(2) as MaterialButton
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

        if (listaFavoritosUsuario.contains(historia.idHistoria)) {
            holder.btnFavoritos.setIconResource(android.R.drawable.star_on)
        } else {
            holder.btnFavoritos.setIconResource(android.R.drawable.star_off)
        }

        holder.btnFavoritos.setOnClickListener {
            onFavoritoClick(historia)
        }

        holder.btnLeer.setOnClickListener {
            val intent = Intent(holder.itemView.context, LeerHistoriaActivity::class.java).apply {
                putExtra("idHistoria", historia.idHistoria)
                putExtra("tituloHistoria", historia.titulo)
            }
            holder.itemView.context.startActivity(intent)
        }

        holder.btnSinopsis.setOnClickListener {
            val activity = holder.itemView.context as? AppCompatActivity
            activity?.let {
                val dialog = SynopsisDialogFragment.newInstance(
                    status = historia.obtenerEstadoValidado(),
                    genres = historia.obtenerGenerosValidados(),
                    synopsis = historia.sinopsis
                )
                dialog.show(it.supportFragmentManager, "SynopsisDialog")
            }
        }

        // --- SOLUCIÓN DEFINITIVA AL RECICLAJE DE IMÁGENES ---
        // 1. Limpiamos la imagen y ponemos el placeholder neutro inmediatamente
        holder.ivImagen.tag = historia.idHistoria
        holder.ivImagen.setImageResource(android.R.drawable.ic_menu_gallery)

        if (historia.imagenUrl.isNotEmpty()) {
            val currentId = historia.idHistoria
            val storageRef = FirebaseStorage.getInstance().getReference(historia.imagenUrl)
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                // 2. Solo cargamos la foto si la tarjeta sigue siendo para la misma historia
                if (holder.ivImagen.tag == currentId) {
                    holder.ivImagen.load(uri) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_gallery)
                    }
                }
            }.addOnFailureListener {
                if (holder.ivImagen.tag == currentId) {
                    holder.ivImagen.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }
    }

    override fun getItemCount(): Int = listaHistorias.size

    fun actualizarDatos(nuevasHistorias: List<Historia>, favoritos: List<String>) {
        listaHistorias = nuevasHistorias
        listaFavoritosUsuario = favoritos
        notifyDataSetChanged()
    }
}