package com.example.nefelibata.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
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
import com.example.nefelibata.utils.Constants
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

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            irAMainActivity()
        }
    }

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
                        etEmail.hint = getString(R.string.email_username_hint)
                        btnAction.text = getString(R.string.login_button)
                    }
                    R.id.btn_tab_register -> {
                        isLoginMode = false
                        tilNombre.visibility = View.VISIBLE
                        etEmail.hint = getString(R.string.email_hint)
                        btnAction.text = getString(R.string.register_button)
                    }
                }
            }
        }

        btnAction.setOnClickListener {
            val inputIdentificador = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val nombre = etNombre.text.toString().trim()

            if (inputIdentificador.isEmpty() || password.isEmpty() || (!isLoginMode && nombre.isEmpty())) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isLoginMode && nombre.replace(" ", "").length < Constants.MIN_NAME_LENGTH) {
                Toast.makeText(this, getString(R.string.min_name_chars, Constants.MIN_NAME_LENGTH), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isLoginMode && password.length < 6) {
                Toast.makeText(this, getString(R.string.password_too_short), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnAction.isEnabled = false

            if (isLoginMode) {
                if (Patterns.EMAIL_ADDRESS.matcher(inputIdentificador).matches()) {
                    loginConEmail(inputIdentificador, password, btnAction)
                } else {
                    db.collection("usuarios").whereEqualTo("nombre", inputIdentificador).get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                val emailEncontrado = documents.documents[0].getString("email") ?: ""
                                loginConEmail(emailEncontrado, password, btnAction)
                            } else {
                                btnAction.isEnabled = true
                                Toast.makeText(this, getString(R.string.user_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            btnAction.isEnabled = true
                            Toast.makeText(this, getString(R.string.auth_error), Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                db.collection("usuarios").whereEqualTo("nombre", nombre).get().addOnSuccessListener { docs ->
                    if (docs.isEmpty) {
                        auth.createUserWithEmailAndPassword(inputIdentificador, password)
                            .addOnCompleteListener(this) { task ->
                                if (task.isSuccessful) {
                                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                                    guardarUsuarioEnFirestore(userId, nombre, inputIdentificador, btnAction)
                                } else {
                                    btnAction.isEnabled = true
                                    Toast.makeText(this, task.exception?.localizedMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        btnAction.isEnabled = true
                        Toast.makeText(this, getString(R.string.name_already_exists), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnGoogle.setOnClickListener { iniciarSesionConGoogle() }
    }

    private fun loginConEmail(email: String, pass: String, button: MaterialButton) {
        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            button.isEnabled = true
            if (task.isSuccessful) irAMainActivity()
            else Toast.makeText(this, getString(R.string.login_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarSesionConGoogle() {
        val serverClientId = "1048728494011-la208k90k2iqha6aplvoe5tebp601985.apps.googleusercontent.com"
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser!!
                            db.collection("usuarios").document(user.uid).get().addOnSuccessListener { doc ->
                                if (!doc.exists()) guardarUsuarioEnFirestore(user.uid, user.displayName ?: "Usuario", user.email ?: "")
                                else irAMainActivity()
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("LoginActivity", "Error Google") }
        }
    }

    private fun guardarUsuarioEnFirestore(userId: String, nombre: String, email: String, button: MaterialButton? = null) {
        val nuevoUsuario = hashMapOf("idUsuario" to userId, "nombre" to nombre, "email" to email, "idFavoritas" to emptyList<String>(), "fotoUser" to "", "numSeguidor" to 0)
        db.collection("usuarios").document(userId).set(nuevoUsuario).addOnSuccessListener { irAMainActivity() }
    }

    private fun irAMainActivity() {
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
    }
}