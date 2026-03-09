package com.example.nefelibata.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.nefelibata.MainActivity
import com.example.nefelibata.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var credentialManager: CredentialManager

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = Firebase.auth
        db = FirebaseFirestore.getInstance()
        credentialManager = CredentialManager.create(this)

        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggle_auth_mode)
        val tilNombre = findViewById<TextInputLayout>(R.id.til_nombre)
        val etNombre = findViewById<TextInputEditText>(R.id.et_nombre)
        val etEmail = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword = findViewById<TextInputEditText>(R.id.et_password)
        val btnAction = findViewById<MaterialButton>(R.id.btn_action)
        val btnGoogle = findViewById<MaterialButton>(R.id.btn_google)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_tab_login -> {
                        isLoginMode = true
                        tilNombre.visibility = View.GONE
                        btnAction.text = "INICIAR SESIÓN"
                    }
                    R.id.btn_tab_register -> {
                        isLoginMode = false
                        tilNombre.visibility = View.VISIBLE
                        btnAction.text = "REGISTRARSE"
                    }
                }
            }
        }

        btnAction.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val nombre = etNombre.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || (!isLoginMode && nombre.isEmpty())) {
                Toast.makeText(this, "Por favor, rellena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validación: Nombre mínimo 3 caracteres (solo en registro)
            if (!isLoginMode && nombre.replace(" ", "").length < 3) {
                Toast.makeText(this, "El nombre debe tener al menos 3 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isLoginMode && password.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnAction.isEnabled = false

            if (isLoginMode) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        btnAction.isEnabled = true
                        if (task.isSuccessful) {
                            irAMainActivity()
                        } else {
                            val errorMsg = task.exception?.localizedMessage ?: "Error al iniciar sesión"
                            Log.e("LoginActivity", "Login Error: ", task.exception)
                            Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                            guardarUsuarioEnFirestore(userId, nombre, email, btnAction)
                        } else {
                            btnAction.isEnabled = true
                            val errorMsg = task.exception?.localizedMessage ?: "Error al crear la cuenta"
                            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        btnGoogle.setOnClickListener {
            iniciarSesionConGoogle()
        }
    }

    private fun iniciarSesionConGoogle() {
        val serverClientId = "1048728494011-la208k90k2iqha6aplvoe5tebp601985.apps.googleusercontent.com"

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false) 
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential
                
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    
                    auth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener(this@LoginActivity) { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser!!
                                db.collection("usuarios").document(user.uid).get()
                                    .addOnSuccessListener { document ->
                                        if (!document.exists()) {
                                            guardarUsuarioEnFirestore(user.uid, user.displayName ?: "Usuario Google", user.email ?: "")
                                        } else {
                                            irAMainActivity()
                                        }
                                    }
                                    .addOnFailureListener { irAMainActivity() }
                            } else {
                                Toast.makeText(this@LoginActivity, "Error en Firebase: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            } catch (e: NoCredentialException) {
                Toast.makeText(this@LoginActivity, "No hay cuentas de Google disponibles en este dispositivo", Toast.LENGTH_LONG).show()
            } catch (e: GetCredentialException) {
                Log.e("LoginActivity", "Error Google: ${e.message}")
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error inesperado: ${e.message}")
            }
        }
    }

    private fun guardarUsuarioEnFirestore(userId: String, nombre: String, email: String, button: MaterialButton? = null) {
        val nuevoUsuario = hashMapOf(
            "idUsuario" to userId,
            "nombre" to nombre,
            "email" to email,
            "idFavoritas" to emptyList<String>()
        )

        db.collection("usuarios").document(userId).set(nuevoUsuario)
            .addOnSuccessListener {
                irAMainActivity()
            }
            .addOnFailureListener { e ->
                Log.e("LoginActivity", "Error Firestore: ${e.message}")
                button?.isEnabled = true
                irAMainActivity()
            }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            irAMainActivity()
        }
    }

    private fun irAMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}