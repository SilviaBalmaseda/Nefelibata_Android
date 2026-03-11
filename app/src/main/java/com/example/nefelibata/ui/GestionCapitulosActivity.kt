package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nefelibata.R
import com.example.nefelibata.adapters.CapitulosGestionAdapter
import com.example.nefelibata.models.Capitulo
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class GestionCapitulosActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var rvCapitulos: RecyclerView
    private lateinit var idHistoria: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestion_capitulos)

        db = FirebaseFirestore.getInstance()
        idHistoria = intent.getStringExtra("idHistoria") ?: ""

        if (idHistoria.isEmpty()) { finish(); return }

        rvCapitulos = findViewById(R.id.rv_gestion_capitulos)
        rvCapitulos.layoutManager = LinearLayoutManager(this)

        findViewById<ImageView>(R.id.iv_back_gestion_caps).setOnClickListener { finish() }
        
        findViewById<FloatingActionButton>(R.id.fab_nuevo_capitulo).setOnClickListener {
            val intent = Intent(this, EditorCapituloActivity::class.java)
            intent.putExtra("idHistoria", idHistoria)
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
                    val cap = d.toObject(Capitulo::class.java)
                    cap.idCapitulo = d.id
                    cap
                }
                rvCapitulos.adapter = CapitulosGestionAdapter(lista, 
                    onEdit = { cap ->
                        val intent = Intent(this, EditorCapituloActivity::class.java)
                        intent.putExtra("idHistoria", idHistoria)
                        intent.putExtra("idCapitulo", cap.idCapitulo)
                        startActivity(intent)
                    },
                    onDelete = { cap -> confirmarEliminarCapitulo(cap) }
                )
            }
    }

    private fun confirmarEliminarCapitulo(cap: Capitulo) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val nombreCap = if (cap.tituloCap.isNullOrEmpty()) "Cap. ${cap.numCapitulo}" else cap.tituloCap
        dialogView.findViewById<TextView>(R.id.tv_confirm_message).text = "¿Desea ELIMINAR el $nombreCap?"

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_cancel_delete)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btn_confirm_delete)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            db.collection("historias").document(idHistoria)
                .collection("capitulos").document(cap.idCapitulo).delete()
                .addOnSuccessListener {
                    db.collection("historias").document(idHistoria)
                        .update("contCapitulos", FieldValue.increment(-1))
                        .addOnSuccessListener {
                            Toast.makeText(this, "Capítulo eliminado", Toast.LENGTH_SHORT).show()
                            cargarCapitulos()
                            dialog.dismiss()
                        }
                }
        }
        
        dialog.show()
    }
}