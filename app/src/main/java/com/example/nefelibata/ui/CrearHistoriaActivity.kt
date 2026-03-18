package com.example.nefelibata.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.nefelibata.R
import com.example.nefelibata.models.Autor
import com.example.nefelibata.models.Historia
import com.example.nefelibata.utils.Constants
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class CrearHistoriaActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private lateinit var etTitulo: TextInputEditText
    private lateinit var etSinopsis: TextInputEditText
    private lateinit var actvEstado: AutoCompleteTextView
    private lateinit var cgGeneros: ChipGroup
    private lateinit var etTituloCap: TextInputEditText
    private lateinit var etContenidoCap: TextInputEditText
    private lateinit var btnGuardar: MaterialButton
    private lateinit var ivToggle: ImageView
    private lateinit var capSection: View
    private lateinit var ivPortadaPrevia: ImageView
    private lateinit var cvPortadaPrevia: View
    private lateinit var btnSubirPortada: MaterialButton
    
    private var imageUri: Uri? = null
    private var cameraUri: Uri? = null
    private var idHistoriaEdicion: String? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imageUri = it; Toast.makeText(this, getString(R.string.photo_ready), Toast.LENGTH_SHORT).show() }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { imageUri = cameraUri; Toast.makeText(this, getString(R.string.photo_ready), Toast.LENGTH_SHORT).show() }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) abrirCamara()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crear_historia)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        etTitulo = findViewById(R.id.et_crear_titulo)
        etSinopsis = findViewById(R.id.et_crear_sinopsis)
        actvEstado = findViewById(R.id.actv_crear_estado)
        cgGeneros = findViewById(R.id.cg_crear_generos)
        etTituloCap = findViewById(R.id.et_crear_titulo_cap)
        etContenidoCap = findViewById(R.id.et_crear_contenido_cap)
        btnGuardar = findViewById(R.id.btn_guardar_historia)
        ivToggle = findViewById(R.id.iv_toggle_generos_crear)
        capSection = findViewById(R.id.ll_seccion_primer_capitulo)
        ivPortadaPrevia = findViewById(R.id.iv_crear_portada_previa)
        cvPortadaPrevia = findViewById(R.id.cv_portada_previa)
        btnSubirPortada = findViewById(R.id.btn_subir_portada)

        findViewById<ImageView>(R.id.iv_back_crear).setOnClickListener { finish() }
        btnSubirPortada.setOnClickListener { mostrarDialogoFoto() }

        val btnGestionarCaps = findViewById<MaterialButton>(R.id.btn_gestionar_capitulos)

        findViewById<LinearLayout>(R.id.ll_generos_header_crear).setOnClickListener {
            cgGeneros.isVisible = !cgGeneros.isVisible
            ivToggle.setImageResource(if (cgGeneros.isVisible) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float)
        }

        setupFormulario()

        idHistoriaEdicion = intent.getStringExtra("idHistoria")
        if (idHistoriaEdicion != null) {
            configurarModoEdicion(idHistoriaEdicion!!)
            btnGestionarCaps.visibility = View.VISIBLE
            btnGestionarCaps.setOnClickListener {
                val intent = Intent(this, GestionCapitulosActivity::class.java)
                intent.putExtra("idHistoria", idHistoriaEdicion)
                startActivity(intent)
            }
        } else {
            btnSubirPortada.text = getString(R.string.add_cover_button)
            btnGuardar.text = getString(R.string.publish_story_button)
        }

        btnGuardar.setOnClickListener { guardarCambios() }
    }

    private fun configurarModoEdicion(id: String) {
        findViewById<TextView>(R.id.tv_crear_historia_title).text = getString(R.string.edit_story_title)
        btnGuardar.text = getString(R.string.update_story_button)
        btnSubirPortada.text = getString(R.string.edit_cover_button)
        capSection.isVisible = false

        db.collection("historias").document(id).get().addOnSuccessListener { doc ->
            val historia = doc.toObject(Historia::class.java)
            historia?.let {
                etTitulo.setText(it.titulo)
                etSinopsis.setText(it.sinopsis)
                
                // Cargar estado traducido
                val estadoValor = it.estado["es"] ?: "Pendiente"
                val resId = Constants.ESTADOS_MAP[estadoValor] ?: R.string.status_pendiente
                actvEstado.setText(getString(resId), false)
                
                if (it.imagenUrl.isNotEmpty()) {
                    cvPortadaPrevia.visibility = View.VISIBLE
                    storage.getReference(it.imagenUrl).downloadUrl.addOnSuccessListener { uri ->
                        ivPortadaPrevia.load(uri)
                    }
                }

                val generosActuales = it.genero["es"] ?: emptyList()
                val generosTraducidos = Constants.getGenerosTraducidos(this)
                for (i in 0 until cgGeneros.childCount) {
                    val chip = cgGeneros.getChildAt(i) as Chip
                    // Comprobamos si el texto del chip (ya traducido) coincide con el nombre en la DB traducido
                    if (generosActuales.contains(Constants.GENEROS_DB[i])) {
                        chip.isChecked = true
                    }
                }
            }
        }
    }

    private fun mostrarDialogoFoto() {
        val opciones = arrayOf(getString(R.string.option_camera), getString(R.string.option_gallery))
        AlertDialog.Builder(this).setItems(opciones) { _, which ->
            if (which == 0) checkCameraPermission() else galleryLauncher.launch("image/*")
        }.show()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun abrirCamara() {
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        cameraUri?.let { cameraLauncher.launch(it) }
    }

    private fun setupFormulario() {
        // Selector de Estados TRADUCIDO
        val estadosTraducidos = Constants.getEstadosTraducidos(this)
        actvEstado.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosTraducidos))
        actvEstado.setText(estadosTraducidos.first(), false)

        // Chips de Géneros TRADUCIDOS
        val generosTraducidos = Constants.getGenerosTraducidos(this)
        for (g in generosTraducidos) {
            val chip = Chip(this)
            chip.text = g
            chip.isCheckable = true
            cgGeneros.addView(chip)
        }
    }

    private fun guardarCambios() {
        val titulo = etTitulo.text.toString().trim()
        
        if (titulo.length < Constants.MIN_STORY_TITLE_LENGTH) {
            Toast.makeText(this, getString(R.string.min_story_title_chars, Constants.MIN_STORY_TITLE_LENGTH), Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardar.isEnabled = false
        btnGuardar.text = getString(R.string.saving_msg)

        lifecycleScope.launch {
            try {
                val user = auth.currentUser ?: return@launch
                var downloadUrl = ""
                if (imageUri != null) {
                    val ref = storage.reference.child("portadas/${UUID.randomUUID()}.jpg")
                    ref.putFile(imageUri!!).await()
                    downloadUrl = ref.path
                }

                // Obtener géneros seleccionados (Guardamos el nombre original de la BBDD)
                val generosSel = mutableListOf<String>()
                val generosTraducidos = Constants.getGenerosTraducidos(this@CrearHistoriaActivity)
                for (i in 0 until cgGeneros.childCount) {
                    val chip = cgGeneros.getChildAt(i) as Chip
                    if (chip.isChecked) {
                        generosSel.add(Constants.GENEROS_DB[i])
                    }
                }

                // Obtener estado seleccionado (Mapeamos de la traducción al valor DB)
                val estadoSelTraducido = actvEstado.text.toString()
                val estadosTraducidos = Constants.getEstadosTraducidos(this@CrearHistoriaActivity)
                val indiceEstado = estadosTraducidos.indexOf(estadoSelTraducido)
                val estadoDB = if (indiceEstado != -1) Constants.ESTADOS_DB[indiceEstado] else "Pendiente"

                val data = mutableMapOf<String, Any>(
                    "titulo" to titulo,
                    "sinopsis" to etSinopsis.text.toString().trim(),
                    "estado" to mapOf("es" to estadoDB),
                    "genero" to mapOf("es" to if(generosSel.isEmpty()) listOf("Ninguno") else generosSel),
                    "fechaModificacionH" to Timestamp.now()
                )
                if (downloadUrl.isNotEmpty()) data["imagenUrl"] = downloadUrl

                if (idHistoriaEdicion == null) {
                    val ref = db.collection("historias").document()
                    data["idHistoria"] = ref.id
                    data["autor"] = Autor(id = user.uid, nombre = db.collection("usuarios").document(user.uid).get().await().getString("nombre") ?: "Anónimo")
                    data["numFavoritos"] = 0
                    data["fechaCreacionH"] = Timestamp.now()
                    data["contCapitulos"] = 1L
                    data["ultimoNumCap"] = 1L
                    ref.set(data).await()

                    val capRef = ref.collection("capitulos").document()
                    capRef.set(hashMapOf(
                        "idCapitulo" to capRef.id,
                        "numCapitulo" to 1L,
                        "tituloCap" to etTituloCap.text.toString().trim(),
                        "historiaCap" to etContenidoCap.text.toString().trim(),
                        "fechaCreacionC" to Timestamp.now(),
                        "fechaModificacionC" to Timestamp.now()
                    )).await()
                    Toast.makeText(this@CrearHistoriaActivity, getString(R.string.story_published_msg), Toast.LENGTH_SHORT).show()
                } else {
                    db.collection("historias").document(idHistoriaEdicion!!).update(data).await()
                    Toast.makeText(this@CrearHistoriaActivity, getString(R.string.success_msg), Toast.LENGTH_SHORT).show()
                }

                finish()
            } catch (e: Exception) {
                Toast.makeText(this@CrearHistoriaActivity, "${getString(R.string.error_loading)}: ${e.message}", Toast.LENGTH_LONG).show()
                btnGuardar.isEnabled = true
                btnGuardar.text = getString(R.string.update_story_button)
            }
        }
    }
}