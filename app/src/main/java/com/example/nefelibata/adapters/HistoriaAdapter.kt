package com.example.nefelibata.adapters

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.nefelibata.R
import com.example.nefelibata.models.Historia
import com.example.nefelibata.ui.LeerHistoriaActivity
import com.example.nefelibata.ui.SettingsActivity
import com.example.nefelibata.ui.SynopsisDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
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
        val context = holder.itemView.context
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        holder.tvTitulo.text = historia.titulo
        holder.tvAutor.text = historia.autor.nombre
        holder.btnFavoritos.text = historia.numFavoritos.toString()

        if (listaFavoritosUsuario.contains(historia.idHistoria)) {
            holder.btnFavoritos.setIconResource(android.R.drawable.star_on)
            holder.btnFavoritos.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.star_yellow))
        } else {
            holder.btnFavoritos.setIconResource(android.R.drawable.star_off)
            holder.btnFavoritos.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
        }

        holder.btnFavoritos.setOnClickListener {
            onFavoritoClick(historia)
        }

        holder.btnLeer.setOnClickListener {
            val intent = Intent(context, LeerHistoriaActivity::class.java).apply {
                putExtra("idHistoria", historia.idHistoria)
                putExtra("tituloHistoria", historia.titulo)
            }
            context.startActivity(intent)
        }

        holder.btnSinopsis.setOnClickListener {
            val activity = context as? AppCompatActivity
            activity?.let {
                val dialog = SynopsisDialogFragment.newInstance(
                    status = historia.obtenerEstadoTraducido(context),
                    genres = historia.obtenerGenerosTraducidos(context),
                    synopsis = historia.sinopsis
                )
                dialog.show(it.supportFragmentManager, "SynopsisDialog")
            }
        }

        // Si el autor de la historia es el usuario actual, redirigir a Ajustes al pulsar el nombre
        holder.tvAutor.setOnClickListener {
            if (historia.autor.id == currentUserId) {
                val intent = Intent(context, SettingsActivity::class.java)
                context.startActivity(intent)
            }
        }

        // --- SOLUCIÓN DEFINITIVA AL RECICLAJE DE IMÁGENES ---
        holder.ivImagen.tag = historia.idHistoria
        holder.ivImagen.setImageResource(android.R.drawable.ic_menu_gallery)

        if (historia.imagenUrl.isNotEmpty()) {
            val currentId = historia.idHistoria
            val storageRef = FirebaseStorage.getInstance().getReference(historia.imagenUrl)
            storageRef.downloadUrl.addOnSuccessListener { uri ->
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
