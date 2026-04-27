package com.example.nefelibata.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.nefelibata.R
import com.example.nefelibata.databinding.ActivityCrearHistoriaBinding
import com.example.nefelibata.models.Autor
import com.example.nefelibata.models.Historia
import com.example.nefelibata.utils.Constants
import com.google.android.material.chip.Chip
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class CrearHistoriaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrearHistoriaBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    
    private var imageUri: Uri? = null
    private var cameraUri: Uri? = null
    private var idHistoriaEdicion: String? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            imageUri = it
            Toast.makeText(this, getString(R.string.photo_ready), Toast.LENGTH_SHORT).show() 
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) { 
            imageUri = cameraUri
            Toast.makeText(this, getString(R.string.photo_ready), Toast.LENGTH_SHORT).show() 
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) abrirCamara()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrearHistoriaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupUI()
        setupFormulario()

        idHistoriaEdicion = intent.getStringExtra("idHistoria")
        if (idHistoriaEdicion != null) {
            configurarModoEdicion(idHistoriaEdicion!!)
        } else {
            binding.btnSubirPortada.text = getString(R.string.add_cover_button)
            binding.btnGuardarHistoria.text = getString(R.string.publish_story_button)
        }
    }

    private fun setupUI() {
        binding.ivBackCrear.setOnClickListener { finish() }
        binding.btnSubirPortada.setOnClickListener { mostrarDialogoFoto() }

        binding.llGenerosHeaderCrear.setOnClickListener {
            binding.cgCrearGeneros.isVisible = !binding.cgCrearGeneros.isVisible
            binding.ivToggleGenerosCrear.setImageResource(
                if (binding.cgCrearGeneros.isVisible) android.R.drawable.arrow_up_float 
                else android.R.drawable.arrow_down_float
            )
        }

        binding.btnGuardarHistoria.setOnClickListener { guardarCambios() }
        
        binding.btnGestionarCapitulos.setOnClickListener {
            val intent = Intent(this, GestionCapitulosActivity::class.java).apply {
                putExtra("idHistoria", idHistoriaEdicion)
            }
            startActivity(intent)
        }
    }

    private fun configurarModoEdicion(id: String) {
        binding.tvCrearHistoriaTitle.text = getString(R.string.edit_story_title)
        binding.btnGuardarHistoria.text = getString(R.string.update_story_button)
        binding.btnSubirPortada.text = getString(R.string.edit_cover_button)
        binding.llSeccionPrimerCapitulo.isVisible = false
        binding.btnGestionarCapitulos.visibility = View.VISIBLE

        db.collection("historias").document(id).get().addOnSuccessListener { doc ->
            val historia = doc.toObject(Historia::class.java)
            historia?.let { h ->
                binding.etCrearTitulo.setText(h.titulo)
                binding.etCrearSinopsis.setText(h.sinopsis)
                
                val estadoValor = h.estado["es"] ?: "Pendiente"
                val resId = Constants.ESTADOS_MAP[estadoValor] ?: R.string.status_pendiente
                binding.actvCrearEstado.setText(getString(resId), false)
                
                if (h.imagenUrl.isNotEmpty()) {
                    binding.cvPortadaPrevia.visibility = View.VISIBLE
                    storage.getReference(h.imagenUrl).downloadUrl.addOnSuccessListener { uri ->
                        binding.ivCrearPortadaPrevia.load(uri)
                    }
                }

                val generosActuales = h.genero["es"] ?: emptyList()
                for (i in 0 until binding.cgCrearGeneros.childCount) {
                    val chip = binding.cgCrearGeneros.getChildAt(i) as Chip
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
        val estadosTraducidos = Constants.getEstadosTraducidos(this)
        binding.actvCrearEstado.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, estadosTraducidos))
        binding.actvCrearEstado.setText(estadosTraducidos.first(), false)

        val generosTraducidos = Constants.getGenerosTraducidos(this)
        for (g in generosTraducidos) {
            val chip = Chip(this)
            chip.text = g
            chip.isCheckable = true
            binding.cgCrearGeneros.addView(chip)
        }
    }

    private fun guardarCambios() {
        val titulo = binding.etCrearTitulo.text.toString().trim()
        val sinopsis = binding.etCrearSinopsis.text.toString().trim()
        
        // 1. Título de la historia: OBLIGATORIO
        if (titulo.length < Constants.MIN_STORY_TITLE_LENGTH) {
            Toast.makeText(this, getString(R.string.min_story_title_chars, Constants.MIN_STORY_TITLE_LENGTH), Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Contenido del capítulo: OBLIGATORIO (Solo al crear nueva historia)
        if (idHistoriaEdicion == null) {
            val contenidoCap = binding.etCrearContenidoCap.text.toString().trim()
            if (contenidoCap.isEmpty()) {
                Toast.makeText(this, getString(R.string.content_required), Toast.LENGTH_SHORT).show()
                return
            }
        }

        binding.btnGuardarHistoria.isEnabled = false
        binding.btnGuardarHistoria.text = getString(R.string.saving_msg)

        // Obtener géneros seleccionados (aunque no sean obligatorios)
        val generosSel = mutableListOf<String>()
        for (i in 0 until binding.cgCrearGeneros.childCount) {
            val chip = binding.cgCrearGeneros.getChildAt(i) as Chip
            if (chip.isChecked) {
                generosSel.add(Constants.GENEROS_DB[i])
            }
        }

        lifecycleScope.launch {
            try {
                val user = auth.currentUser ?: return@launch
                var downloadUrl = ""
                if (imageUri != null) {
                    val ref = storage.reference.child("portadas/${UUID.randomUUID()}.jpg")
                    ref.putFile(imageUri!!).await()
                    downloadUrl = ref.path
                }

                val estadoSelTraducido = binding.actvCrearEstado.text.toString()
                val estadosTraducidos = Constants.getEstadosTraducidos(this@CrearHistoriaActivity)
                val indiceEstado = estadosTraducidos.indexOf(estadoSelTraducido)
                val estadoDB = if (indiceEstado != -1) Constants.ESTADOS_DB[indiceEstado] else "Pendiente"

                val data = mutableMapOf<String, Any>(
                    "titulo" to titulo,
                    "sinopsis" to sinopsis,
                    "estado" to mapOf("es" to estadoDB),
                    "genero" to mapOf("es" to if (generosSel.isEmpty()) listOf("Ninguno") else generosSel),
                    "fechaModificacionH" to Timestamp.now()
                )
                if (downloadUrl.isNotEmpty()) data["imagenUrl"] = downloadUrl

                if (idHistoriaEdicion == null) {
                    val ref = db.collection("historias").document()
                    val userName = db.collection("usuarios").document(user.uid).get().await().getString("nombre") ?: "Anónimo"
                    
                    data["idHistoria"] = ref.id
                    data["autor"] = Autor(id = user.uid, nombre = userName)
                    data["numFavoritos"] = 0
                    data["fechaCreacionH"] = Timestamp.now()
                    data["contCapitulos"] = 1L
                    data["ultimoNumCap"] = 1L
                    ref.set(data).await()

                    val capRef = ref.collection("capitulos").document()
                    capRef.set(hashMapOf(
                        "idCapitulo" to capRef.id,
                        "numCapitulo" to 1L,
                        "tituloCap" to binding.etCrearTituloCap.text.toString().trim(),
                        "historiaCap" to binding.etCrearContenidoCap.text.toString().trim(),
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
                binding.btnGuardarHistoria.isEnabled = true
                binding.btnGuardarHistoria.text = if (idHistoriaEdicion == null) getString(R.string.publish_story_button) else getString(R.string.update_story_button)
            }
        }
    }
}
