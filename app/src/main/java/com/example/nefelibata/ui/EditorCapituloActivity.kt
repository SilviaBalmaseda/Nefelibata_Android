package com.example.nefelibata.ui

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nefelibata.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class EditorCapituloActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var etTitulo: EditText
    private lateinit var etContenido: EditText
    private lateinit var btnGuardar: MaterialButton
    private lateinit var idHistoria: String
    private var idCapitulo: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor_capitulo)

        db = FirebaseFirestore.getInstance()
        idHistoria = intent.getStringExtra("idHistoria") ?: ""
        idCapitulo = intent.getStringExtra("idCapitulo")

        etTitulo = findViewById(R.id.et_editor_cap_titulo)
        etContenido = findViewById(R.id.et_editor_cap_contenido)
        btnGuardar = findViewById(R.id.btn_editor_cap_guardar)

        findViewById<ImageView>(R.id.iv_back_editor).setOnClickListener { finish() }

        if (idCapitulo != null) {
            cargarCapitulo()
        }

        btnGuardar.setOnClickListener { guardarCapitulo() }
    }

    private fun cargarCapitulo() {
        db.collection("historias").document(idHistoria)
            .collection("capitulos").document(idCapitulo!!).get()
            .addOnSuccessListener { doc ->
                etTitulo.setText(doc.getString("tituloCap"))
                etContenido.setText(doc.getString("historiaCap"))
            }
    }

    private fun guardarCapitulo() {
        val titulo = etTitulo.text.toString().trim()
        val contenido = etContenido.text.toString().trim()

        if (contenido.isEmpty()) {
            Toast.makeText(this, getString(R.string.content_required), Toast.LENGTH_SHORT).show()
            return
        }

        // Deshabilitar botón para evitar duplicados
        btnGuardar.isEnabled = false

        if (idCapitulo == null) {
            // MODO CREACIÓN: Usar últimoNumCap + 1
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
                    // Actualizar contadores en la historia principal
                    db.collection("historias").document(idHistoria).update(
                        "contCapitulos", FieldValue.increment(1),
                        "ultimoNumCap", nuevoNum,
                        "fechaModificacionH", Timestamp.now()
                    ).addOnSuccessListener { 
                        finish() 
                    }.addOnFailureListener {
                        btnGuardar.isEnabled = true
                        Toast.makeText(this, "Error al actualizar historia", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    btnGuardar.isEnabled = true
                    Toast.makeText(this, "Error al guardar capítulo", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                btnGuardar.isEnabled = true
                Toast.makeText(this, "Error al obtener datos de la historia", Toast.LENGTH_SHORT).show()
            }
        } else {
            // MODO EDICIÓN: Solo datos de contenido y fecha
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
                            btnGuardar.isEnabled = true 
                            finish() // Cerramos igual si el cap se guardó pero falló la fecha de historia
                        }
                }.addOnFailureListener {
                    btnGuardar.isEnabled = true
                    Toast.makeText(this, "Error al actualizar capítulo", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
