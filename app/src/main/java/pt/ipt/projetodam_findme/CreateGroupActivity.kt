/**
 * CreateGroupActivity.kt
 *
 * Ecrã para criação de novos grupos.
 * Envia nome do grupo para o backend e adiciona o criador como membro.
 */
package pt.ipt.projetodam_findme

import android.content.Intent
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

class CreateGroupActivity : AppCompatActivity() {

    private lateinit var editGroupName: EditText
    private lateinit var btnCreateGroup: Button
    private lateinit var btnCloseGroup: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_group)

        editGroupName = findViewById(R.id.editGroupName)
        btnCreateGroup = findViewById(R.id.btnCreateGroup)
        btnCloseGroup = findViewById(R.id.btnCloseGroup)

        // Botão para fechar o ecrã
        btnCloseGroup.setOnClickListener { finish() }

        // Botão para criar o grupo
        btnCreateGroup.setOnClickListener {
            createGroup()
        }
    }

    private fun createGroup() {
        val groupName = editGroupName.text.toString().trim()

        if (groupName.isEmpty()) {
            Toast.makeText(this, "O nome do grupo não pode ser vazio.", Toast.LENGTH_SHORT).show()
            return
        }

        // Obter o meu ID guardado na sessão
        val prefs = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        val myId = prefs.getInt("id_user", -1)

        if (myId == -1) {
            Toast.makeText(this, "Erro de sessão. Faça login novamente.", Toast.LENGTH_SHORT).show()
            return
        }

        // URL do seu ficheiro create_group.php
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/create_group.php"

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        // SUCESSO
                        Toast.makeText(this, json.getString("success"), Toast.LENGTH_LONG).show()

                        // Sinaliza para MainActivity atualizar a lista
                        setResult(RESULT_OK)
                        finish()

                    } else if (json.has("error")) {
                        // ERRO DO SERVIDOR
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
                // Envia o nome do grupo e o ID do criador
                return mutableMapOf(
                    "name" to groupName,
                    "user_id" to myId.toString()
                )
            }
        }

        queue.add(request)
    }
}