/**
 * AddFriendActivity.kt
 *
 * Ecrã para enviar pedidos de amizade.
 * Procura utilizador por email e envia pedido via backend.
 */
package pt.ipt.projetodam_findme

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class AddFriendActivity : AppCompatActivity() {

    private lateinit var editEmail: EditText
    private lateinit var btnSend: Button
    private lateinit var btnClose: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_friend)

        editEmail = findViewById(R.id.editEmailFriend)
        btnSend = findViewById(R.id.btnSendRequest)
        btnClose = findViewById(R.id.btnClose)

        // Botão para fechar o ecrã
        btnClose.setOnClickListener { finish() }

        // Botão para enviar
        btnSend.setOnClickListener {
            enviarPedido()
        }
    }

    private fun enviarPedido() {
        val email = editEmail.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this, "Escreve um email válido.", Toast.LENGTH_SHORT).show()
            return
        }

        // Obter o meu ID guardado na sessão
        val prefs = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        val myId = prefs.getInt("id_user", -1)

        if (myId == -1) {
            Toast.makeText(this, "Erro de sessão. Faz login novamente.", Toast.LENGTH_SHORT).show()
            return
        }

        // URL do ficheiro PHP
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/add_friend.php"

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        // SUCESSO
                        Toast.makeText(this, json.getString("success"), Toast.LENGTH_LONG).show()
                        finish() // Fecha o ecrã e volta ao mapa
                    } else if (json.has("error")) {
                        // ERRO DO SERVIDOR (ex: email não existe)
                        Toast.makeText(this, json.getString("error"), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao processar resposta.", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Erro de conexão.", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "user_id" to myId.toString(),
                    "email" to email
                )
            }
        }

        queue.add(request)
    }
}