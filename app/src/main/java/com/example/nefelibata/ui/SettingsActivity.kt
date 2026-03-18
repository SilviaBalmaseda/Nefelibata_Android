package com.example.nefelibata.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import coil.transform.CircleCropTransformation
import com.example.nefelibata.R
import com.example.nefelibata.models.Usuario
import com.example.nefelibata.ui.auth.LoginActivity
import com.example.nefelibata.utils.Constants
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.UUID

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var tvName: TextView
    private lateinit var ivProfile: ShapeableImageView
    private lateinit var sharedPreferences: SharedPreferences
    
    private var cameraUri: Uri? = null
    private var fotoUrlActual: String? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { subirFotoAFirebase(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { subirFotoAFirebase(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) abrirCamara()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val modeSaved = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(modeSaved)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_settings)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvName = findViewById(R.id.tv_settings_username)
        ivProfile = findViewById(R.id.iv_settings_profile)
        val fabEditPhoto = findViewById<FloatingActionButton>(R.id.fab_edit_photo)
        val btnLogout = findViewById<MaterialButton>(R.id.btn_logout)
        val ivBack = findViewById<ImageView>(R.id.iv_back)
        val toggleTheme = findViewById<MaterialButtonToggleGroup>(R.id.toggle_theme)
        val toggleLang = findViewById<MaterialButtonToggleGroup>(R.id.toggle_language)

        cargarDatosUsuario(toggleLang)

        ivBack.setOnClickListener { finish() }
        fabEditPhoto.setOnClickListener { mostrarOpcionesImagen() }
        ivProfile.setOnClickListener { mostrarImagenAmpliada() }
        
        val editNameListener = View.OnClickListener { mostrarDialogoEdicion() }
        tvName.setOnClickListener(editNameListener)
        findViewById<ImageView>(R.id.iv_edit_name).setOnClickListener(editNameListener)

        // Configuración tema
        when (modeSaved) {
            AppCompatDelegate.MODE_NIGHT_NO -> toggleTheme.check(R.id.btn_theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> toggleTheme.check(R.id.btn_theme_dark)
            else -> toggleTheme.check(R.id.btn_theme_system)
        }

        toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val temaStr = when (checkedId) {
                    R.id.btn_theme_light -> "claro"
                    R.id.btn_theme_dark -> "oscuro"
                    else -> "sistema"
                }
                guardarPreferenciaTema(temaStr)
            }
        }

        // Configuración idioma
        toggleLang.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val langCode = if (checkedId == R.id.btn_lang_es) Constants.LANG_ES else Constants.LANG_EN
                cambiarIdioma(langCode)
            }
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun cargarDatosUsuario(toggleLang: MaterialButtonToggleGroup) {
        val user = auth.currentUser ?: return
        db.collection("usuarios").document(user.uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val usuario = doc.toObject(Usuario::class.java)
                usuario?.let {
                    tvName.text = it.nombre
                    fotoUrlActual = it.fotoUser
                    if (!it.fotoUser.isNullOrEmpty()) {
                        ivProfile.load(it.fotoUser) { transformations(CircleCropTransformation()) }
                    }
                    
                    // Cargar preferencia de idioma
                    val preferencias = it.preferencias
                    val currentLang = preferencias["leng"] ?: Constants.DEFAULT_LANG
                    if (currentLang == Constants.LANG_ES) toggleLang.check(R.id.btn_lang_es)
                    else toggleLang.check(R.id.btn_lang_en)
                }
            }
        }
    }

    private fun cambiarIdioma(langCode: String) {
        val androidLangCode = if (langCode == Constants.LANG_ES) "es" else "en"
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.toLanguageTags() == androidLangCode) return

        // 1. Guardar en Firestore
        val user = auth.currentUser
        if (user != null) {
            val data = hashMapOf("preferencias" to hashMapOf("leng" to langCode))
            db.collection("usuarios").document(user.uid).set(data, SetOptions.merge())
        }

        // 2. Aplicar cambio sutil
        val appLocales: LocaleListCompat = LocaleListCompat.forLanguageTags(androidLangCode)
        AppCompatDelegate.setApplicationLocales(appLocales)
        
        // Evitar el parpadeo brusco forzando una recreación suave si es necesario
        // Aunque setApplicationLocales ya recrea la actividad, a veces un pequeño delay ayuda
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun mostrarImagenAmpliada() {
        if (fotoUrlActual.isNullOrEmpty()) return
        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_viewer, null)
        val ivFull = dialogView.findViewById<ImageView>(R.id.iv_full_image)
        ivFull.load(fotoUrlActual)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarOpcionesImagen() {
        val opciones = arrayOf(getString(R.string.option_take_photo), getString(R.string.option_gallery))
        AlertDialog.Builder(this).setItems(opciones) { _, which ->
            if (which == 0) gestionarPermisosCamara() else galleryLauncher.launch("image/*")
        }.show()
    }

    private fun gestionarPermisosCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun abrirCamara() {
        val photoFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "perfil_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        cameraLauncher.launch(cameraUri!!)
    }

    private fun subirFotoAFirebase(uri: Uri) {
        val user = auth.currentUser ?: return
        val ref = storage.reference.child("fotos_perfil/${user.uid}.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUri ->
                fotoUrlActual = downloadUri.toString()
                db.collection("usuarios").document(user.uid).update("fotoUser", fotoUrlActual).addOnSuccessListener {
                    ivProfile.load(fotoUrlActual) { transformations(CircleCropTransformation()) }
                    Toast.makeText(this, getString(R.string.photo_ready), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun guardarPreferenciaTema(tema: String) {
        val mode = when (tema) {
            "claro" -> AppCompatDelegate.MODE_NIGHT_NO
            "oscuro" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        if (currentMode == mode) return

        sharedPreferences.edit().putInt("theme_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
        
        val user = auth.currentUser
        if (user != null) {
            val data = hashMapOf("preferencias" to hashMapOf("tema" to tema))
            db.collection("usuarios").document(user.uid).set(data, SetOptions.merge())
        }
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun mostrarDialogoEdicion() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.username_label))
        val input = TextInputEditText(this).apply {
            setPadding(50, 40, 50, 40)
            setText(tvName.text)
        }
        builder.setView(input)
        builder.setPositiveButton(getString(R.string.save_changes_button)) { _, _ ->
            val nuevoNombre = input.text.toString().trim()
            if (nuevoNombre.length >= Constants.MIN_NAME_LENGTH) {
                db.collection("usuarios").whereEqualTo("nombre", nuevoNombre).get().addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        db.collection("usuarios").document(auth.currentUser!!.uid).update("nombre", nuevoNombre)
                            .addOnSuccessListener {
                                tvName.text = nuevoNombre
                                Toast.makeText(this, getString(R.string.name_updated), Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, getString(R.string.name_in_use), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        builder.setNegativeButton(getString(R.string.cancel_button), null)
        builder.show()
    }
}