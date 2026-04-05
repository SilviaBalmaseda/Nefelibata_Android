package com.example.nefelibata.adapters

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.nefelibata.R
import com.example.nefelibata.databinding.ItemHistoriaBinding
import com.example.nefelibata.models.Historia
import com.example.nefelibata.ui.LeerHistoriaActivity
import com.example.nefelibata.ui.SettingsActivity
import com.example.nefelibata.ui.SynopsisDialogFragment
import com.example.nefelibata.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

/**
 * Adaptador para mostrar una lista de historias usando el diseño de tarjetas grandes.
 */
class HistoriaAdapter(
    private var listaHistorias: List<Historia>,
    private var listaFavoritosUsuario: List<String>,
    private val onFavoritoClick: (Historia) -> Unit 
) : RecyclerView.Adapter<HistoriaAdapter.HistoriaViewHolder>() {

    class HistoriaViewHolder(val binding: ItemHistoriaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoriaViewHolder {
        val binding = ItemHistoriaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoriaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoriaViewHolder, position: Int) {
        val historia = listaHistorias[position]
        val context = holder.itemView.context
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        with(holder.binding) {
            tvStoryTitle.text = historia.titulo
            tvStoryAuthor.text = historia.autor.nombre
            btnFavoritos.text = historia.numFavoritos.toString()

            val isFavorite = listaFavoritosUsuario.contains(historia.idHistoria)
            btnFavoritos.setIconResource(if (isFavorite) android.R.drawable.star_on else android.R.drawable.star_off)
            btnFavoritos.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(context, if (isFavorite) R.color.star_yellow else R.color.text_secondary)
            )

            btnFavoritos.setOnClickListener { onFavoritoClick(historia) }

            btnLeer.setOnClickListener {
                val intent = Intent(context, LeerHistoriaActivity::class.java).apply {
                    putExtra("idHistoria", historia.idHistoria)
                    putExtra("tituloHistoria", historia.titulo)
                }
                context.startActivity(intent)
            }

            btnSinopsis.setOnClickListener {
                (context as? AppCompatActivity)?.let {
                    SynopsisDialogFragment.newInstance(
                        status = historia.obtenerEstadoTraducido(context),
                        genres = historia.obtenerGenerosTraducidos(context),
                        synopsis = historia.sinopsis
                    ).show(it.supportFragmentManager, "SynopsisDialog")
                }
            }

            tvStoryAuthor.setOnClickListener {
                if (historia.autor.id == currentUserId) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            }

            ivStoryImage.tag = historia.idHistoria
            val defaultImg = Constants.PORTADA_DEFECTO

            if (historia.imagenUrl.isEmpty()) {
                ivStoryImage.load(defaultImg)
            } else {
                val currentId = historia.idHistoria
                FirebaseStorage.getInstance().getReference(historia.imagenUrl).downloadUrl
                    .addOnSuccessListener { uri ->
                        if (ivStoryImage.tag == currentId) {
                            ivStoryImage.load(uri) {
                                crossfade(true)
                                placeholder(defaultImg)
                                error(defaultImg)
                            }
                        }
                    }
                    .addOnFailureListener {
                        if (ivStoryImage.tag == currentId) {
                            ivStoryImage.load(defaultImg)
                        }
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
