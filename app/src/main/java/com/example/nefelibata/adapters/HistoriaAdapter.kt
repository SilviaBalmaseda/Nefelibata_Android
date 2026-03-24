package com.example.nefelibata.adapters

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

/**
 * Adaptador para mostrar una lista de historias en un RecyclerView.
 */
class HistoriaAdapter(
    private var listaHistorias: List<Historia>,
    private var listaFavoritosUsuario: List<String>,
    private val onFavoritoClick: (Historia) -> Unit // Callback para gestionar el clic en el botón de favorito
) : RecyclerView.Adapter<HistoriaAdapter.HistoriaViewHolder>() {

    /**
     * ViewHolder que contiene la referencia a la vista del item de historia.
     */
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
            // Configuración de textos básicos
            tvStoryTitle.text = historia.titulo
            tvStoryAuthor.text = historia.autor.nombre
            btnFavoritos.text = historia.numFavoritos.toString()

            // Estado visual del botón de favorito según la lista del usuario
            val isFavorite = listaFavoritosUsuario.contains(historia.idHistoria)
            btnFavoritos.setIconResource(if (isFavorite) android.R.drawable.star_on else android.R.drawable.star_off)
            btnFavoritos.iconTint = ColorStateList.valueOf(
                ContextCompat.getColor(context, if (isFavorite) R.color.star_yellow else R.color.text_secondary)
            )

            // Acción de marcar/desmarcar favorito
            btnFavoritos.setOnClickListener { onFavoritoClick(historia) }

            // Navegación a la pantalla de lectura
            btnLeer.setOnClickListener {
                val intent = Intent(context, LeerHistoriaActivity::class.java).apply {
                    putExtra("idHistoria", historia.idHistoria)
                    putExtra("tituloHistoria", historia.titulo)
                }
                context.startActivity(intent)
            }

            // Mostrar diálogo con la sinopsis, géneros y estado
            btnSinopsis.setOnClickListener {
                (context as? AppCompatActivity)?.let {
                    SynopsisDialogFragment.newInstance(
                        status = historia.obtenerEstadoTraducido(context),
                        genres = historia.obtenerGenerosTraducidos(context),
                        synopsis = historia.sinopsis
                    ).show(it.supportFragmentManager, "SynopsisDialog")
                }
            }

            // Navegación al perfil del autor (o ajustes si es el propio usuario)
            tvStoryAuthor.setOnClickListener {
                if (historia.autor.id == currentUserId) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            }

            // Carga de imagen de portada desde Firebase Storage usando Coil
            ivStoryImage.tag = historia.idHistoria // Evita errores de reciclaje de vista
            ivStoryImage.setImageResource(android.R.drawable.ic_menu_gallery)

            if (historia.imagenUrl.isNotEmpty()) {
                val currentId = historia.idHistoria
                FirebaseStorage.getInstance().getReference(historia.imagenUrl).downloadUrl
                    .addOnSuccessListener { uri ->
                        // Solo cargar si la vista sigue asignada a la misma historia
                        if (ivStoryImage.tag == currentId) {
                            ivStoryImage.load(uri) {
                                crossfade(true)
                                placeholder(android.R.drawable.ic_menu_gallery)
                                error(android.R.drawable.ic_menu_gallery)
                            }
                        }
                    }
                    .addOnFailureListener {
                        if (ivStoryImage.tag == currentId) {
                            ivStoryImage.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    }
            }
        }
    }

    override fun getItemCount(): Int = listaHistorias.size

    /**
     * Actualiza los datos del adaptador y refresca la vista.
     */
    fun actualizarDatos(nuevasHistorias: List<Historia>, favoritos: List<String>) {
        listaHistorias = nuevasHistorias
        listaFavoritosUsuario = favoritos
        notifyDataSetChanged()
    }
}
