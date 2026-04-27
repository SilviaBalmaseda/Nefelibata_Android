package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nefelibata.R
import com.example.nefelibata.adapters.MisHistoriasAdapter
import com.example.nefelibata.databinding.ActivityMisHistoriasBinding
import com.example.nefelibata.databinding.DialogConfirmDeleteBinding
import com.example.nefelibata.models.Historia
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch

class MisHistoriasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMisHistoriasBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: MisHistoriasAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMisHistoriasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainMisHistorias) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.ivBackMisHistorias.setOnClickListener { finish() }
        binding.fabCrearHistoria.setOnClickListener {
            startActivity(Intent(this, CrearHistoriaActivity::class.java))
        }

        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        cargarMisHistorias()
    }

    private fun setupRecyclerView() {
        binding.rvMisHistorias.layoutManager = LinearLayoutManager(this)
        adapter = MisHistoriasAdapter(
            emptyList(),
            onEditClick = { historia ->
                val intent = Intent(this, CrearHistoriaActivity::class.java).apply {
                    putExtra("idHistoria", historia.idHistoria)
                }
                startActivity(intent)
            },
            onDeleteClick = { historia ->
                mostrarConfirmacionEliminar(historia)
            }
        )
        binding.rvMisHistorias.adapter = adapter
    }

    private fun cargarMisHistorias() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("historias")
            .whereEqualTo("autor.id", userId)
            .get()
            .addOnSuccessListener { documentos ->
                val lista = documentos.map { doc ->
                    val h = doc.toObject(Historia::class.java)
                    h.idHistoria = doc.id
                    h
                }
                
                if (lista.isEmpty()) {
                    binding.tvEmptyMisHistorias.visibility = View.VISIBLE
                    binding.rvMisHistorias.visibility = View.GONE
                } else {
                    binding.tvEmptyMisHistorias.visibility = View.GONE
                    binding.rvMisHistorias.visibility = View.VISIBLE
                    adapter.actualizarDatos(lista)
                }
            }
    }

    private fun mostrarConfirmacionEliminar(historia: Historia) {
        val dialogBinding = DialogConfirmDeleteBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogBinding.tvConfirmMessage.text = getString(R.string.delete_story_msg)

        dialogBinding.btnCancelDelete.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmDelete.setOnClickListener {
            eliminarHistoriaCompleta(historia.idHistoria)
            dialog.dismiss()
        }
        
        dialog.show()
    }

    /**
     * Elimina una historia, sus capítulos asociados y las referencias en favoritos de otros usuarios.
     */
    private fun eliminarHistoriaCompleta(idHistoria: String) {
        // 1. Obtener todos los capítulos de la historia
        db.collection("historias").document(idHistoria).collection("capitulos").get()
            .addOnSuccessListener { capitulosSnapshot ->
                val batch = db.batch()
                
                // 2. Añadir eliminación de cada capítulo al batch
                for (capDoc in capitulosSnapshot.documents) {
                    batch.delete(capDoc.reference)
                }
                
                // 3. Añadir eliminación del documento principal de la historia al batch
                val historiaRef = db.collection("historias").document(idHistoria)
                batch.delete(historiaRef)
                
                // 4. Ejecutar el borrado masivo
                batch.commit().addOnSuccessListener {
                    // 5. Por último, limpiar la historia de las listas de favoritos de los usuarios
                    limpiarFavoritosDeUsuarios(idHistoria)
                }.addOnFailureListener {
                    // Si falla el batch, intentamos al menos borrar la historia y favoritos
                    db.collection("historias").document(idHistoria).delete()
                    limpiarFavoritosDeUsuarios(idHistoria)
                }
            }
            .addOnFailureListener {
                // Si falla al obtener capítulos, intentamos borrar solo la historia y favoritos
                db.collection("historias").document(idHistoria).delete()
                limpiarFavoritosDeUsuarios(idHistoria)
            }
    }

    private fun limpiarFavoritosDeUsuarios(idHistoria: String) {
        db.collection("usuarios")
            .whereArrayContains("idFavoritas", idHistoria)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batchFavoritos = db.batch()
                for (doc in querySnapshot.documents) {
                    batchFavoritos.update(doc.reference, "idFavoritas", FieldValue.arrayRemove(idHistoria))
                }
                batchFavoritos.commit().addOnCompleteListener {
                    cargarMisHistorias()
                }
            }
            .addOnFailureListener {
                cargarMisHistorias()
            }
    }
}
