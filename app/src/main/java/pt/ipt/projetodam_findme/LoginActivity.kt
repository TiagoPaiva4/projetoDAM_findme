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

class LoginActivity : AppCompatActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var txtRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        btnLogin = findViewById(R.id.btnLogin)
        txtRegister = findViewById(R.id.txtRegister)

        btnLogin.setOnClickListener { loginUser() }

        txtRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser() {
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Preencha os campos todos!", Toast.LENGTH_SHORT).show()
            return
        }

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
                // --- SUCESSO ---
                val errorMessage = response.optString("error")

                if (errorMessage.isNotEmpty()) {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                } else {
                    // GUARDAR A SESSÃO
                    val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
                    sharedPreferences.edit {
                        putBoolean("logado", true)
                        putString("email", email)
                    }

                    Toast.makeText(this, "Login efetuado com sucesso!", Toast.LENGTH_SHORT).show()

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            },
            { error ->
                // --- ERRO NA LIGAÇÃO (CORRIGIDO) ---
                val response = error.networkResponse

                if (response != null && response.data != null) {
                    // O servidor respondeu (ex: 404, 500)
                    val errorString = String(response.data, Charset.forName("UTF-8"))
                    val statusCode = response.statusCode

                    Log.e("LOGIN_ERRO", "Status: $statusCode | Body: $errorString")

                    // Tenta mostrar mensagem legível
                    try {
                        val jsonErro = JSONObject(errorString)
                        val msg = jsonErro.optString("error", "Erro no servidor")
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        // Se não for JSON (ex: erro HTML do XAMPP)
                        Toast.makeText(this, "Erro $statusCode. Veja o Logcat.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Erro de rede (sem internet ou servidor desligado)
                    Toast.makeText(this, "Erro de conexão: ${error.message}", Toast.LENGTH_LONG).show()
                    error.printStackTrace()
                }
            }
        )

        Volley.newRequestQueue(this).add(request)
    }
}