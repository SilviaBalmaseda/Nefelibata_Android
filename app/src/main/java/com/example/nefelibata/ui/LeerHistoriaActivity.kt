package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nefelibata.R
import com.example.nefelibata.databinding.ActivityLeerHistoriaBinding
import com.example.nefelibata.models.Capitulo
import com.example.nefelibata.models.Historia
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeerHistoriaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeerHistoriaBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val listaCapitulos = mutableListOf<Capitulo>()
    private var indiceActual = 0
    private lateinit var idHistoria: String
    private var idAutorActual: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeerHistoriaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        setupListeners()
        loadInitialData()
    }

    private fun setupListeners() {
        binding.ivBackLeer.setOnClickListener { finish() }

        binding.btnLeerVerAutor.setOnClickListener {
            val currentUserId = auth.currentUser?.uid
            idAutorActual?.let { id ->
                if (id == currentUserId) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    val intent = Intent(this, PerfilAutorActivity::class.java).apply {
                        putExtra("idAutor", id)
                    }
                    startActivity(intent)
                }
            }
        }

        binding.btnLeerAnterior.setOnClickListener { navigateChapter(-1) }
        binding.btnLeerSiguiente.setOnClickListener { navigateChapter(1) }
    }

    private fun loadInitialData() {
        idHistoria = intent.getStringExtra("idHistoria") ?: ""
        binding.tvLeerTituloHistoria.text = intent.getStringExtra("tituloHistoria") ?: getString(R.string.app_name)

        if (idHistoria.isNotEmpty()) {
            cargarDatosHistoria()
            cargarCapitulos()
        }
    }

    private fun cargarDatosHistoria() {
        db.collection("historias").document(idHistoria).get()
            .addOnSuccessListener { doc ->
                doc.toObject(Historia::class.java)?.let { historia ->
                    binding.tvLeerTituloHistoria.text = historia.titulo
                    binding.btnLeerVerAutor.text = historia.autor.nombre
                    idAutorActual = historia.autor.id
                }
            }
            .addOnFailureListener { showError() }
    }

    private fun cargarCapitulos() {
        db.collection("historias").document(idHistoria)
            .collection("capitulos")
            .orderBy("numCapitulo", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documentos ->
                listaCapitulos.clear()
                listaCapitulos.addAll(documentos.mapNotNull { doc ->
                    doc.toObject(Capitulo::class.java).apply { idCapitulo = doc.id }
                })
                
                if (listaCapitulos.isNotEmpty()) {
                    configurarSelector()
                    actualizarVistaCapitulo()
                } else {
                    Toast.makeText(this, getString(R.string.no_chapters), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { showError() }
    }

    private fun configurarSelector() {
        val titulos = listaCapitulos.map { 
            it.tituloCap.takeUnless { t -> t.isNullOrEmpty() } 
                ?: getString(R.string.chapter_prefix, it.numCapitulo)
        }
        
        binding.actvLeerSelectorCapitulo.apply {
            setAdapter(ArrayAdapter(this@LeerHistoriaActivity, android.R.layout.simple_dropdown_item_1line, titulos))
            setOnItemClickListener { _, _, position, _ ->
                indiceActual = position
                actualizarVistaCapitulo()
            }
        }
    }

    private fun navigateChapter(delta: Int) {
        val nuevoIndice = indiceActual + delta
        if (nuevoIndice in listaCapitulos.indices) {
            indiceActual = nuevoIndice
            actualizarVistaCapitulo()
        }
    }

    private fun actualizarVistaCapitulo() {
        if (listaCapitulos.isEmpty()) return
        
        val cap = listaCapitulos[indiceActual]
        val displayTitle = cap.tituloCap.takeUnless { it.isNullOrEmpty() } 
            ?: getString(R.string.chapter_prefix, cap.numCapitulo)

        binding.tvLeerTituloCapitulo.text = displayTitle
        binding.tvLeerContenidoCapitulo.text = cap.historiaCap
        binding.actvLeerSelectorCapitulo.setText(displayTitle, false)
        
        binding.btnLeerAnterior.isEnabled = indiceActual > 0
        binding.btnLeerSiguiente.isEnabled = indiceActual < listaCapitulos.size - 1
    }

    private fun showError() {
        Toast.makeText(this, getString(R.string.error_loading), Toast.LENGTH_SHORT).show()
    }
}
