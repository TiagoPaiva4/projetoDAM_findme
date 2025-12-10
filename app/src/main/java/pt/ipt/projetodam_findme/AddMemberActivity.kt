package pt.ipt.projetodam_findme

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class AddMemberActivity : AppCompatActivity() {

    private var groupId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_friend) // Reutiliza o layout de adicionar amigo

        groupId = intent.getIntExtra("GROUP_ID", -1)
        if (groupId == -1) {
            Toast.makeText(this, "Erro: Grupo inválido.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 1. Mudar textos do layout
        findViewById<TextView>(R.id.txtTitle).text = "Adicionar Membro"
        findViewById<TextView>(R.id.txtDesc).text = "Escreve o email da pessoa que queres adicionar ao grupo."
        findViewById<Button>(R.id.btnSendRequest).text = "Adicionar ao Grupo"

        val editEmail: EditText = findViewById(R.id.editEmailFriend)
        val btnSend: Button = findViewById(R.id.btnSendRequest)
        val btnClose: ImageButton = findViewById(R.id.btnClose)

        btnClose.setOnClickListener { finish() }
        btnSend.setOnClickListener {
            addMember(editEmail.text.toString())
        }
    }

    private fun addMember(email: String) {
        val emailTrim = email.trim()

        if (emailTrim.isEmpty()) {
            Toast.makeText(this, "Escreve um email válido.", Toast.LENGTH_SHORT).show()
            return
        }

        // URL do script PHP (o que criámos no passo 1)
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/add_group_member.php"

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        Toast.makeText(this, json.getString("success"), Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK) // Sinaliza que foi adicionado um membro
                        finish()
                    } else if (json.has("error")) {
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
                    "group_id" to groupId.toString(),
                    "email" to emailTrim
                )
            }
        }

        queue.add(request)
    }
}