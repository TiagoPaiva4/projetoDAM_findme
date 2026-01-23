package pt.ipt.projetodam_findme

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {

    private lateinit var editName: EditText
    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var editConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var txtLoginLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Inicializar vistas
        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        editConfirmPassword = findViewById(R.id.editConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        txtLoginLink = findViewById(R.id.txtLoginLink)

        // Ação do Botão Registar
        btnRegister.setOnClickListener {
            if (validateInputs()) {
                registerUser()
            }
        }

        // Ação do Link "Entrar agora" (Voltar ao Login)
        txtLoginLink.setOnClickListener {
            finish() // Fecha esta atividade e volta ao Login que está por trás
        }
    }

    // --- FUNÇÃO DE VALIDAÇÃO ---
    private fun validateInputs(): Boolean {
        val name = editName.text.toString().trim()
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString().trim()
        val confirmPass = editConfirmPassword.text.toString().trim()

        var isValid = true

        // 1. Validar Nome
        if (name.isEmpty()) {
            editName.error = "O nome é obrigatório"
            isValid = false
        }

        // 2. Validar Email (Usa Patterns do Android para verificar formato a@b.c)
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.error = "Insira um email válido"
            isValid = false
        }

        // 3. Validar Password (Minimo 8 caracteres)
        if (password.length < 8) {
            editPassword.error = "A password deve ter pelo menos 8 caracteres"
            isValid = false
        }

        // 4. Validar se as Passwords coincidem
        if (password != confirmPass) {
            editConfirmPassword.error = "As passwords não coincidem"
            isValid = false
        }

        return isValid
    }

    // --- FUNÇÃO DE REGISTO (BACKEND) ---
    private fun registerUser() {
        val name = editName.text.toString().trim()
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString().trim()

        // URL do teu servidor Azure (Verifica se o caminho está correto)
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/register.php"

        val jsonBody = JSONObject().apply {
            put("name", name)
            put("email", email)
            put("password", password)
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            url,
            jsonBody,
            { response ->
                // Sucesso
                if (response.has("error")) {
                    Toast.makeText(this, response.getString("error"), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()

                    // Opcional: Já fazer login automático ou enviar para o ecrã de login
                    // Vamos enviar para o Login e preencher o email
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.putExtra("email_registado", email)
                    startActivity(intent)
                    finish()
                }
            },
            { error ->
                // Erro
                Toast.makeText(this, "Erro ao registar: ${error.message}", Toast.LENGTH_LONG).show()
                error.printStackTrace()
            }
        )

        Volley.newRequestQueue(this).add(request)
    }
}