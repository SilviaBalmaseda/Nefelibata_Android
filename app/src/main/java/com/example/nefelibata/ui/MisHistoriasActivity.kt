package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.adapters.MisHistoriasAdapter
import com.example.nefelibata.models.Historia
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MisHistoriasActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: MisHistoriasAdapter
    private lateinit var rvMisHistorias: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mis_historias)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_mis_historias)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        rvMisHistorias = findViewById(R.id.rv_mis_historias)
        val ivBack = findViewById<ImageView>(R.id.iv_back_mis_historias)
        val fabCrear = findViewById<FloatingActionButton>(R.id.fab_crear_historia)

        ivBack.setOnClickListener { finish() }

        setupRecyclerView()
        
        fabCrear.setOnClickListener {
            val intent = Intent(this, CrearHistoriaActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        cargarMisHistorias()
    }

    private fun setupRecyclerView() {
        rvMisHistorias.layoutManager = LinearLayoutManager(this)
        adapter = MisHistoriasAdapter(
            emptyList(),
            onEditClick = { historia ->
                val intent = Intent(this, CrearHistoriaActivity::class.java)
                intent.putExtra("idHistoria", historia.idHistoria)
                startActivity(intent)
            },
            onDeleteClick = { historia ->
                mostrarConfirmacionEliminar(historia)
            }
        )
        rvMisHistorias.adapter = adapter
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
                adapter.actualizarDatos(lista)
            }
    }

    private fun mostrarConfirmacionEliminar(historia: Historia) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar historia")
            .setMessage("¿Seguro que quieres eliminar esta historia? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("historias").document(historia.idHistoria)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Historia eliminada", Toast.LENGTH_SHORT).show()
                        cargarMisHistorias()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}