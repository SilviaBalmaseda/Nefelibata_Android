package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nefelibata.R
import com.example.nefelibata.adapters.CapitulosGestionAdapter
import com.example.nefelibata.databinding.ActivityGestionCapitulosBinding
import com.example.nefelibata.databinding.DialogConfirmDeleteBinding
import com.example.nefelibata.models.Capitulo
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class GestionCapitulosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestionCapitulosBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var idHistoria: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestionCapitulosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        idHistoria = intent.getStringExtra("idHistoria") ?: ""

        if (idHistoria.isEmpty()) { 
            finish()
            return 
        }

        setupUI()
    }

    private fun setupUI() {
        binding.rvGestionCapitulos.layoutManager = LinearLayoutManager(this)
        binding.ivBackGestionCaps.setOnClickListener { finish() }
        
        binding.fabNuevoCapitulo.setOnClickListener {
            val intent = Intent(this, EditorCapituloActivity::class.java).apply {
                putExtra("idHistoria", idHistoria)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        cargarCapitulos()
    }

    private fun cargarCapitulos() {
        db.collection("historias").document(idHistoria).collection("capitulos")
            .orderBy("numCapitulo", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { docs ->
                val lista = docs.map { d -> 
                    d.toObject(Capitulo::class.java).apply { idCapitulo = d.id }
                }
                binding.rvGestionCapitulos.adapter = CapitulosGestionAdapter(lista, 
                    onEdit = { cap ->
                        val intent = Intent(this, EditorCapituloActivity::class.java).apply {
                            putExtra("idHistoria", idHistoria)
                            putExtra("idCapitulo", cap.idCapitulo)
                        }
                        startActivity(intent)
                    },
                    onDelete = { cap -> confirmarEliminarCapitulo(cap) }
                )
            }
    }

    private fun confirmarEliminarCapitulo(cap: Capitulo) {
        val dialogBinding = DialogConfirmDeleteBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val nombreCap = cap.tituloCap.takeIf { !it.isNullOrEmpty() } 
            ?: getString(R.string.chapter_prefix, cap.numCapitulo)
            
        dialogBinding.tvConfirmMessage.text = getString(R.string.delete_chapter_confirm_msg, nombreCap)

        dialogBinding.btnCancelDelete.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmDelete.setOnClickListener {
            eliminarCapitulo(cap.idCapitulo, dialog)
        }
        
        dialog.show()
    }

    private fun eliminarCapitulo(idCapitulo: String, dialog: AlertDialog) {
        db.collection("historias").document(idHistoria)
            .collection("capitulos").document(idCapitulo).delete()
            .addOnSuccessListener {
                db.collection("historias").document(idHistoria)
                    .update("contCapitulos", FieldValue.increment(-1))
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.chapter_deleted), Toast.LENGTH_SHORT).show()
                        cargarCapitulos()
                        dialog.dismiss()
                    }
            }
    }
}
