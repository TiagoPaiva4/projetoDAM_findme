package pt.ipt.projetodam_findme

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.nio.charset.Charset
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException

class LoginActivity : AppCompatActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var txtRegister: TextView
    private lateinit var btnGoogleSignIn: SignInButton
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object { // <-- Add this block
        private const val RC_SIGN_IN = 9001
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        btnLogin = findViewById(R.id.btnLogin)
        txtRegister = findViewById(R.id.txtRegister)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("1050226007080-844dg6rmgu34vgp5l4ra78fdm69taa2b.apps.googleusercontent.com") // <-- IMPORTANT
        .requestEmail()
        .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnLogin.setOnClickListener { loginUser() }

        txtRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnGoogleSignIn.setOnClickListener {
            signIn()
        }


    }

    private fun loginUser() {
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Preencha os campos todos!", Toast.LENGTH_SHORT).show()
            return
        }

        // URL do teu servidor Azure
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/login.php"

        val jsonBody = JSONObject().apply {
            put("email", email)
            put("password", password)
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            url,
            jsonBody,
            { response ->
                // --- SUCESSO NA COMUNICAÇÃO ---

                // O teu PHP envia "error" se falhar, ou "token" e "user" se acertar
                if (response.has("error")) {
                    val msgErro = response.getString("error")
                    Toast.makeText(this, msgErro, Toast.LENGTH_LONG).show()
                } else {
                    // LOGIN CORRETO!
                    try {
                        // 1. Ler os dados que vêm do PHP
                        val token = response.optString("token")
                        val userObj = response.getJSONObject("user")
                        val userId = userObj.getInt("id")
                        val userName = userObj.getString("name")




                        // ...
// 2. Guardar TUDO nas SharedPreferences
                        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
                        sharedPreferences.edit(commit = true) {  // <--- CORREÇÃO AQUI
                            putBoolean("logado", true)
                            putInt("id_user", userId)
                            putString("nome_user", userName)
                            putString("email_user", email)
                            putString("token", token)
                        }
// ...

                        Toast.makeText(this, "Bem-vindo, $userName!", Toast.LENGTH_SHORT).show()

                        // 3. Mudar para o ecrã principal
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()


                    } catch (e: Exception) {
                        Toast.makeText(this, "Erro ao processar dados: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }
            },
            { error ->
                // --- ERRO NA LIGAÇÃO ---
                val response = error.networkResponse

                if (response != null && response.data != null) {
                    val errorString = String(response.data, Charset.forName("UTF-8"))
                    val statusCode = response.statusCode
                    Log.e("LOGIN_ERRO", "Status: $statusCode | Body: $errorString")

                    try {
                        val jsonErro = JSONObject(errorString)
                        val msg = jsonErro.optString("error", "Erro no servidor")
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Erro $statusCode. Tente novamente.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Erro de conexão. Verifique a internet.", Toast.LENGTH_LONG).show()
                    error.printStackTrace()
                }
            }
        )

        Volley.newRequestQueue(this).add(request)
    }
    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }




    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    sendTokenToBackend(idToken)
                } else {
                    Toast.makeText(this, "Erro: token não recebido", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.w("LoginActivity", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In falhou: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendTokenToBackend(idToken: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/google_auth.php"

        val jsonBody = JSONObject().apply {
            put("id_token", idToken)
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            url,
            jsonBody,
            { response ->
                if (response.has("error")) {
                    Toast.makeText(this, response.getString("error"), Toast.LENGTH_LONG).show()
                } else {
                    val token = response.optString("token")
                    val userObj = response.getJSONObject("user")
                    val userId = userObj.getInt("id")
                    val userName = userObj.getString("name")
                    val userEmail = userObj.getString("email")

                    val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
                    sharedPreferences.edit {
                        putBoolean("logado", true)
                        putInt("id_user", userId)
                        putString("nome_user", userName)
                        putString("email_user", userEmail)
                        putString("token", token)
                    }

                    Toast.makeText(this, "Bem-vindo, $userName!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            },
            { error ->
                Toast.makeText(this, "Erro de conexão", Toast.LENGTH_SHORT).show()
                error.printStackTrace()
            }
        )

        Volley.newRequestQueue(this).add(request)
    }
}

