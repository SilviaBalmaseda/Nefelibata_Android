package com.example.nefelibata.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nefelibata.R
import com.example.nefelibata.databinding.ActivityEditorCapituloBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class EditorCapituloActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorCapituloBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var idHistoria: String
    private var idCapitulo: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorCapituloBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        idHistoria = intent.getStringExtra("idHistoria") ?: ""
        idCapitulo = intent.getStringExtra("idCapitulo")

        binding.ivBackEditor.setOnClickListener { finish() }

        if (idCapitulo != null) {
            cargarCapitulo()
        }

        binding.btnEditorCapGuardar.setOnClickListener { guardarCapitulo() }
    }

    private fun cargarCapitulo() {
        db.collection("historias").document(idHistoria)
            .collection("capitulos").document(idCapitulo!!).get()
            .addOnSuccessListener { doc ->
                binding.etEditorCapTitulo.setText(doc.getString("tituloCap"))
                binding.etEditorCapContenido.setText(doc.getString("historiaCap"))
            }
    }

    private fun guardarCapitulo() {
        val titulo = binding.etEditorCapTitulo.text.toString().trim()
        val contenido = binding.etEditorCapContenido.text.toString().trim()

        // Solo el contenido es obligatorio según los nuevos requisitos
        if (contenido.isEmpty()) {
            Toast.makeText(this, getString(R.string.content_required), Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnEditorCapGuardar.isEnabled = false

        if (idCapitulo == null) {
            crearNuevoCapitulo(titulo, contenido)
        } else {
            actualizarCapituloExistente(titulo, contenido)
        }
    }

    private fun crearNuevoCapitulo(titulo: String, contenido: String) {
        db.collection("historias").document(idHistoria).get().addOnSuccessListener { docH ->
            val ultimoNum = docH.getLong("ultimoNumCap") ?: 0L
            val nuevoNum = ultimoNum + 1
            
            val capRef = db.collection("historias").document(idHistoria).collection("capitulos").document()
            val data = hashMapOf(
                "idCapitulo" to capRef.id,
                "numCapitulo" to nuevoNum,
                "tituloCap" to titulo,
                "historiaCap" to contenido,
                "fechaCreacionC" to Timestamp.now(),
                "fechaModificacionC" to Timestamp.now()
            )

            capRef.set(data).addOnSuccessListener {
                db.collection("historias").document(idHistoria).update(
                    "contCapitulos", FieldValue.increment(1),
                    "ultimoNumCap", nuevoNum,
                    "fechaModificacionH", Timestamp.now()
                ).addOnSuccessListener { 
                    finish() 
                }.addOnFailureListener {
                    binding.btnEditorCapGuardar.isEnabled = true
                    Toast.makeText(this, "Error updating story", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                binding.btnEditorCapGuardar.isEnabled = true
                Toast.makeText(this, "Error saving chapter", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            binding.btnEditorCapGuardar.isEnabled = true
            Toast.makeText(this, "Error fetching story data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actualizarCapituloExistente(titulo: String, contenido: String) {
        val data = mapOf(
            "tituloCap" to titulo,
            "historiaCap" to contenido,
            "fechaModificacionC" to Timestamp.now()
        )
        db.collection("historias").document(idHistoria)
            .collection("capitulos").document(idCapitulo!!)
            .update(data)
            .addOnSuccessListener {
                db.collection("historias").document(idHistoria).update("fechaModificacionH", Timestamp.now())
                    .addOnSuccessListener { finish() }
                    .addOnFailureListener { 
                        binding.btnEditorCapGuardar.isEnabled = true 
                        finish() 
                    }
            }.addOnFailureListener {
                binding.btnEditorCapGuardar.isEnabled = true
                Toast.makeText(this, "Error updating chapter", Toast.LENGTH_SHORT).show()
            }
    }
}
