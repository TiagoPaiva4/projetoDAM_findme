/**
 * RegisterActivity.kt
 *
 * Ecrã de registo de novos utilizadores.
 * Valida os dados inseridos e envia para o backend.
 */
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

        editName = findViewById(R.id.editName)
        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        editConfirmPassword = findViewById(R.id.editConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        txtLoginLink = findViewById(R.id.txtLoginLink)

        btnRegister.setOnClickListener {
            if (validateInputs()) {
                registerUser()
            }
        }

        txtLoginLink.setOnClickListener {
            finish()
        }
    }

    private fun validateInputs(): Boolean {
        val name = editName.text.toString().trim()
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString().trim()
        val confirmPass = editConfirmPassword.text.toString().trim()

        var isValid = true

        if (name.isEmpty()) {
            editName.error = "O nome é obrigatório"
            isValid = false
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.error = "Insira um email válido"
            isValid = false
        }

        if (password.length < 8) {
            editPassword.error = "A password deve ter pelo menos 8 caracteres"
            isValid = false
        }

        if (password != confirmPass) {
            editConfirmPassword.error = "As passwords não coincidem"
            isValid = false
        }

        return isValid
    }

    private fun registerUser() {
        val name = editName.text.toString().trim()
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString().trim()

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

                if (response.has("error")) {
                    Toast.makeText(this, response.getString("error"), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Conta criada com sucesso!", Toast.LENGTH_SHORT).show()


                    val intent = Intent(this, LoginActivity::class.java)
                    intent.putExtra("email_registado", email)
                    startActivity(intent)
                    finish()
                }
            },
            { error ->

                Toast.makeText(this, "Erro ao registar: ${error.message}", Toast.LENGTH_LONG).show()

            }
        )

        Volley.newRequestQueue(this).add(request)
    }
}