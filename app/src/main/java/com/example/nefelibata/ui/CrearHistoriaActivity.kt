package com.example.nefelibata.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.nefelibata.R
import com.example.nefelibata.models.Autor
import com.example.nefelibata.models.Historia
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
    
    private var imageUri: Uri? = null
    private var cameraUri: Uri? = null

    // Lanzadores
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imageUri = it; Toast.makeText(this, "Foto de galería cargada", Toast.LENGTH_SHORT).show() }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { imageUri = cameraUri; Toast.makeText(this, "Foto de cámara cargada", Toast.LENGTH_SHORT).show() }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val galleryGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] ?: false
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        }
        
        if (cameraGranted || galleryGranted) {
            Toast.makeText(this, "Permisos concedidos. Vuelve a elegir la opción.", Toast.LENGTH_SHORT).show()
        }
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
        
        findViewById<ImageView>(R.id.iv_back_crear).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btn_subir_portada).setOnClickListener { mostrarDialogoSeleccion() }

        findViewById<LinearLayout>(R.id.ll_generos_header_crear).setOnClickListener {
            if (cgGeneros.visibility == View.VISIBLE) {
                cgGeneros.visibility = View.GONE
                ivToggle.setImageResource(android.R.drawable.arrow_down_float)
            } else {
                cgGeneros.visibility = View.VISIBLE
                ivToggle.setImageResource(android.R.drawable.arrow_up_float)
            }
        }

        setupFormulario()
        btnGuardar.setOnClickListener { publicarHistoria() }
    }

    private fun mostrarDialogoSeleccion() {
        val opciones = arrayOf("Cámara", "Galería")
        AlertDialog.Builder(this)
            .setTitle("Seleccionar Portada")
            .setItems(opciones) { _, which ->
                if (which == 0) checkCameraPermission() else checkGalleryPermission()
            }.show()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun checkGalleryPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            galleryLauncher.launch("image/*")
        } else {
            permissionLauncher.launch(arrayOf(permission))
        }
    }

    private fun abrirCamara() {
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp_portada_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", photoFile)
        cameraUri?.let { cameraLauncher.launch(it) }
    }

    private fun setupFormulario() {
        val estados = listOf("Pendiente", "En pausa", "Terminada", "Abandonada")
        actvEstado.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estados))
        actvEstado.setText("Pendiente", false)

        val listaGeneros = listOf("Acción", "Aventura", "Comedia", "Drama", "Deportes", "Fantasía", "Magia", "Musical", "Psicológico", "Romance", "Superhéroes", "Terror", "Tragedia")
        for (g in listaGeneros) {
            val chip = Chip(this)
            chip.text = g
            chip.isCheckable = true
            cgGeneros.addView(chip)
        }
    }

    private fun publicarHistoria() {
        val titulo = etTitulo.text.toString().trim()
        val contenidoCap = etContenidoCap.text.toString().trim()

        if (titulo.isEmpty() || contenidoCap.isEmpty()) {
            Toast.makeText(this, "Título e Historia son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardar.isEnabled = false
        btnGuardar.text = "PUBLICANDO..."

        lifecycleScope.launch {
            try {
                val user = auth.currentUser ?: return@launch
                
                var downloadUrl = ""
                if (imageUri != null) {
                    val ref = storage.reference.child("portadas/${UUID.randomUUID()}.jpg")
                    ref.putFile(imageUri!!).await()
                    downloadUrl = ref.path
                }

                val userDoc = db.collection("usuarios").document(user.uid).get().await()
                val nombreAutor = userDoc.getString("nombre") ?: "Anónimo"

                val generosSel = mutableListOf<String>()
                for (i in 0 until cgGeneros.childCount) {
                    val chip = cgGeneros.getChildAt(i) as Chip
                    if (chip.isChecked) generosSel.add(chip.text.toString())
                }
                if (generosSel.isEmpty()) generosSel.add("Ninguno")

                val historiaRef = db.collection("historias").document()
                val nuevaHistoria = hashMapOf(
                    "idHistoria" to historiaRef.id,
                    "titulo" to titulo,
                    "sinopsis" to etSinopsis.text.toString().trim(),
                    "imagenUrl" to downloadUrl,
                    "estado" to mapOf("es" to actvEstado.text.toString()),
                    "genero" to mapOf("es" to generosSel),
                    "autor" to Autor(id = user.uid, nombre = nombreAutor),
                    "numFavoritos" to 0,
                    "fechaCreacionH" to Timestamp.now(),
                    "contCapitulos" to 1,
                    "ultimoNumCap" to 1
                )
                historiaRef.set(nuevaHistoria).await()

                val capRef = historiaRef.collection("capitulos").document()
                val primerCapitulo = hashMapOf(
                    "idCapitulo" to capRef.id,
                    "numCapitulo" to 1L,
                    "tituloCap" to etTituloCap.text.toString().trim(),
                    "historiaCap" to contenidoCap,
                    "fechaCreacionC" to Timestamp.now()
                )
                capRef.set(primerCapitulo).await()

                Toast.makeText(this@CrearHistoriaActivity, "¡Historia publicada con éxito!", Toast.LENGTH_SHORT).show()
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@CrearHistoriaActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                btnGuardar.isEnabled = true
                btnGuardar.text = "PUBLICAR HISTORIA"
            }
        }
    }
}