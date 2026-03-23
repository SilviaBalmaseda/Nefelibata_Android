package com.example.nefelibata.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nefelibata.R
import com.example.nefelibata.models.Capitulo
import com.example.nefelibata.models.Historia
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Actividad encargada de la lectura de capítulos de una historia.
 * Refactorizada para maximizar la legibilidad y el uso de Kotlin idiomático.
 */
class LeerHistoriaActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var tvTituloHistoria: TextView
    private lateinit var btnNombreAutor: MaterialButton 
    private lateinit var tvTituloCapitulo: TextView
    private lateinit var tvContenidoCapitulo: TextView
    private lateinit var btnAnterior: MaterialButton
    private lateinit var btnSiguiente: MaterialButton
    private lateinit var selectorCapitulo: AutoCompleteTextView
    private lateinit var ivBack: ImageView

    private val listaCapitulos = mutableListOf<Capitulo>()
    private var indiceActual = 0
    private lateinit var idHistoria: String
    private var idAutorActual: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leer_historia)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        initViews()
        setupListeners()
        loadInitialData()
    }

    /**
     * Inicializa las referencias a los componentes de la interfaz.
     */
    private fun initViews() {
        tvTituloHistoria = findViewById(R.id.tv_leer_titulo_historia)
        btnNombreAutor = findViewById(R.id.btn_leer_ver_autor) 
        tvTituloCapitulo = findViewById(R.id.tv_leer_titulo_capitulo)
        tvContenidoCapitulo = findViewById(R.id.tv_leer_contenido_capitulo)
        btnAnterior = findViewById(R.id.btn_leer_anterior)
        btnSiguiente = findViewById(R.id.btn_leer_siguiente)
        selectorCapitulo = findViewById(R.id.actv_leer_selector_capitulo)
        ivBack = findViewById(R.id.iv_back_leer)
    }

    /**
     * Configura los listeners de clics para la navegación y acciones de usuario.
     */
    private fun setupListeners() {
        ivBack.setOnClickListener { finish() }

        btnNombreAutor.setOnClickListener {
            val currentUserId = auth.currentUser?.uid
            idAutorActual?.let { id ->
                if (id == currentUserId) {
                    // Si el autor soy yo, voy a Ajustes
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                } else {
                    // Si es otro autor, voy a su perfil público
                    val intent = Intent(this, PerfilAutorActivity::class.java).apply {
                        putExtra("idAutor", id)
                    }
                    startActivity(intent)
                }
            }
        }

        btnAnterior.setOnClickListener { navigateChapter(-1) }
        btnSiguiente.setOnClickListener { navigateChapter(1) }
    }

    /**
     * Carga los datos iniciales y dispara las peticiones a Firestore.
     */
    private fun loadInitialData() {
        idHistoria = intent.getStringExtra("idHistoria") ?: ""
        tvTituloHistoria.text = intent.getStringExtra("tituloHistoria") ?: getString(R.string.app_name)

        if (idHistoria.isNotEmpty()) {
            cargarDatosHistoria()
            cargarCapitulos()
        }
    }

    /**
     * Recupera los detalles de la historia (título y autor) desde Firestore.
     */
    private fun cargarDatosHistoria() {
        db.collection("historias").document(idHistoria).get()
            .addOnSuccessListener { doc ->
                doc.toObject(Historia::class.java)?.let { historia ->
                    tvTituloHistoria.text = historia.titulo
                    btnNombreAutor.text = historia.autor.nombre
                    idAutorActual = historia.autor.id
                }
            }
            .addOnFailureListener { showError() }
    }

    /**
     * Recupera y ordena la lista de capítulos asociados a la historia.
     */
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

    /**
     * Configura el adaptador y evento de selección para el dropdown de capítulos.
     */
    private fun configurarSelector() {
        val titulos = listaCapitulos.map { 
            it.tituloCap.takeUnless { t -> t.isNullOrEmpty() } 
                ?: getString(R.string.chapter_prefix, it.numCapitulo)
        }
        
        selectorCapitulo.apply {
            setAdapter(ArrayAdapter(this@LeerHistoriaActivity, android.R.layout.simple_dropdown_item_1line, titulos))
            setOnItemClickListener { _, _, position, _ ->
                indiceActual = position
                actualizarVistaCapitulo()
            }
        }
    }

    /**
     * Navega al capítulo anterior o siguiente según el delta proporcionado.
     */
    private fun navigateChapter(delta: Int) {
        val nuevoIndice = indiceActual + delta
        if (nuevoIndice in listaCapitulos.indices) {
            indiceActual = nuevoIndice
            actualizarVistaCapitulo()
        }
    }

    /**
     * Refresca la interfaz de usuario con la información del capítulo actual.
     */
    private fun actualizarVistaCapitulo() {
        if (listaCapitulos.isEmpty()) return
        
        val cap = listaCapitulos[indiceActual]
        val displayTitle = cap.tituloCap.takeUnless { it.isNullOrEmpty() } 
            ?: getString(R.string.chapter_prefix, cap.numCapitulo)

        tvTituloCapitulo.text = displayTitle
        tvContenidoCapitulo.text = cap.historiaCap
        selectorCapitulo.setText(displayTitle, false)
        
        btnAnterior.isEnabled = indiceActual > 0
        btnSiguiente.isEnabled = indiceActual < listaCapitulos.size - 1
    }

    private fun showError() {
        Toast.makeText(this, getString(R.string.error_loading), Toast.LENGTH_SHORT).show()
    }
}
