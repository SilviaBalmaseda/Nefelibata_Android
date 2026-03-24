package com.example.nefelibata.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
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
import com.example.nefelibata.databinding.ActivitySettingsBinding
import com.example.nefelibata.databinding.DialogImageViewerBinding
import com.example.nefelibata.models.Usuario
import com.example.nefelibata.ui.auth.LoginActivity
import com.example.nefelibata.utils.Constants
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var sharedPreferences: SharedPreferences
    
    private var cameraUri: Uri? = null
    private var fotoUrlActual: String? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { mostrarConfirmacionCambioFoto(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { mostrarConfirmacionCambioFoto(it) }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) abrirCamara()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val modeSaved = sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(modeSaved)

        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainSettings) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI(modeSaved)
        cargarDatosUsuario()
    }

    private fun setupUI(modeSaved: Int) {
        binding.ivBack.setOnClickListener { finish() }
        binding.fabEditPhoto.setOnClickListener { mostrarOpcionesImagen() }
        binding.ivSettingsProfile.setOnClickListener { mostrarImagenAmpliada() }
        
        val editNameListener = { mostrarDialogoEdicion() }
        binding.tvSettingsUsername.setOnClickListener { editNameListener() }
        binding.ivEditName.setOnClickListener { editNameListener() }

        when (modeSaved) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.toggleTheme.check(R.id.btn_theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.toggleTheme.check(R.id.btn_theme_dark)
            else -> binding.toggleTheme.check(R.id.btn_theme_system)
        }

        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val temaStr = when (checkedId) {
                    R.id.btn_theme_light -> "claro"
                    R.id.btn_theme_dark -> "oscuro"
                    else -> "sistema"
                }
                binding.toggleTheme.postDelayed({ guardarPreferenciaTema(temaStr) }, 200)
            }
        }

        binding.toggleLanguage.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val langCode = if (checkedId == R.id.btn_lang_es) Constants.LANG_ES else Constants.LANG_EN
                binding.toggleLanguage.postDelayed({ cambiarIdioma(langCode) }, 200)
            }
        }

        binding.btnLogout.setOnClickListener { mostrarConfirmacionCerrarSesion() }
    }

    private fun cargarDatosUsuario() {
        val user = auth.currentUser ?: return
        db.collection("usuarios").document(user.uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val usuario = doc.toObject(Usuario::class.java)
                usuario?.let {
                    binding.tvSettingsUsername.text = it.nombre
                    binding.tvSettingsFollowers.text = getString(R.string.followers_count, it.numSeguidor)
                    fotoUrlActual = it.fotoUser
                    if (!it.fotoUser.isNullOrEmpty()) {
                        binding.ivSettingsProfile.load(it.fotoUser) { transformations(CircleCropTransformation()) }
                    } else {
                        binding.ivSettingsProfile.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                    
                    val preferencias = it.preferencias
                    val currentLang = preferencias["leng"] ?: Constants.DEFAULT_LANG
                    if (currentLang == Constants.LANG_ES) binding.toggleLanguage.check(R.id.btn_lang_es)
                    else binding.toggleLanguage.check(R.id.btn_lang_en)
                }
            }
        }
    }

    private fun cambiarIdioma(langCode: String) {
        val androidLangCode = if (langCode == Constants.LANG_ES) "es" else "en"
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.toLanguageTags() == androidLangCode) return

        val user = auth.currentUser
        if (user != null) {
            val data = hashMapOf("preferencias" to hashMapOf("leng" to langCode))
            db.collection("usuarios").document(user.uid).set(data, SetOptions.merge())
        }

        val appLocales: LocaleListCompat = LocaleListCompat.forLanguageTags(androidLangCode)
        AppCompatDelegate.setApplicationLocales(appLocales)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
        
        val user = auth.currentUser
        if (user != null) {
            val data = hashMapOf("preferencias" to hashMapOf("tema" to tema))
            db.collection("usuarios").document(user.uid).set(data, SetOptions.merge())
        }
        
        AppCompatDelegate.setDefaultNightMode(mode)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun mostrarImagenAmpliada() {
        if (fotoUrlActual.isNullOrEmpty()) return
        val dialogBinding = DialogImageViewerBinding.inflate(layoutInflater)
        dialogBinding.ivFullImage.load(fotoUrlActual)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogBinding.root.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun mostrarOpcionesImagen() {
        val camaraStr = getString(R.string.option_take_photo)
        val galeriaStr = getString(R.string.option_gallery)
        val eliminarStr = getString(R.string.option_remove_photo)
        val tieneFoto = !fotoUrlActual.isNullOrEmpty()

        val eliminarSpannable = SpannableString(eliminarStr).apply {
            val color = if (tieneFoto) Color.RED else Color.LTGRAY
            setSpan(ForegroundColorSpan(color), 0, length, 0)
        }

        val opciones = arrayOf<CharSequence>(camaraStr, galeriaStr, eliminarSpannable)
        AlertDialog.Builder(this).setItems(opciones) { _, which ->
            when (which) {
                0 -> gestionarPermisosCamara()
                1 -> galleryLauncher.launch("image/*")
                2 -> if (tieneFoto) mostrarConfirmacionEliminarFoto()
            }
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

    private fun mostrarConfirmacionCambioFoto(uri: Uri) {
        val dialogBinding = DialogImageViewerBinding.inflate(layoutInflater)
        dialogBinding.ivFullImage.load(uri) { crossfade(true) }
        
        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setTitle(getString(R.string.change_photo_confirm_title))
            .setMessage(getString(R.string.change_photo_confirm_msg))
            .setPositiveButton(getString(R.string.yes_button)) { _, _ -> subirFotoAFirebase(uri) }
            .setNegativeButton(getString(R.string.no_button), null)
            .show()
    }

    private fun mostrarConfirmacionEliminarFoto() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_photo_confirm_title))
            .setMessage(getString(R.string.delete_photo_confirm_msg))
            .setPositiveButton(getString(R.string.yes_button)) { _, _ -> eliminarFotoDePerfil() }
            .setNegativeButton(getString(R.string.no_button), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
        }
        dialog.show()
    }

    private fun eliminarFotoDePerfil() {
        val user = auth.currentUser ?: return
        db.collection("usuarios").document(user.uid).update("fotoUser", "").addOnSuccessListener {
            fotoUrlActual = ""
            binding.ivSettingsProfile.setImageResource(android.R.drawable.ic_menu_gallery)
            Toast.makeText(this, getString(R.string.photo_removed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun subirFotoAFirebase(uri: Uri) {
        val user = auth.currentUser ?: return
        val ref = storage.reference.child("fotos_perfil/${user.uid}.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { downloadUri ->
                fotoUrlActual = downloadUri.toString()
                db.collection("usuarios").document(user.uid).update("fotoUser", fotoUrlActual).addOnSuccessListener {
                    binding.ivSettingsProfile.load(fotoUrlActual) { transformations(CircleCropTransformation()) }
                    Toast.makeText(this, getString(R.string.photo_ready), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun mostrarConfirmacionCerrarSesion() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_confirm_title))
            .setMessage(getString(R.string.logout_confirm_msg))
            .setPositiveButton(getString(R.string.logout_confirm_ok)) { _, _ -> 
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
        }
        dialog.show()
    }

    private fun mostrarDialogoEdicion() {
        val input = TextInputEditText(this).apply {
            setPadding(50, 40, 50, 40)
            setText(binding.tvSettingsUsername.text)
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.username_label))
            .setView(input)
            .setPositiveButton(getString(R.string.save_changes_button)) { _, _ ->
                val nuevoNombre = input.text.toString().trim()
                if (nuevoNombre.length >= Constants.MIN_NAME_LENGTH) {
                    actualizarNombre(nuevoNombre)
                }
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun actualizarNombre(nuevoNombre: String) {
        db.collection("usuarios").whereEqualTo("nombre", nuevoNombre).get().addOnSuccessListener { documents ->
            if (documents.isEmpty) {
                db.collection("usuarios").document(auth.currentUser!!.uid).update("nombre", nuevoNombre)
                    .addOnSuccessListener {
                        binding.tvSettingsUsername.text = nuevoNombre
                        Toast.makeText(this, getString(R.string.name_updated), Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, getString(R.string.name_in_use), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
