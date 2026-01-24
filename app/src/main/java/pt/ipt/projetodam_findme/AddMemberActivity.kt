/**
 * AddMemberActivity.kt
 *
 * Ecrã para adicionar membros a um grupo.
 * Permite selecionar amigos da lista ou adicionar por email.
 */
package pt.ipt.projetodam_findme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class AddMemberActivity : AppCompatActivity() {

    private var groupId: Int = -1
    private lateinit var userId: String
    private lateinit var recyclerFriends: RecyclerView
    private lateinit var txtEmpty: TextView
    private val friendsList = mutableListOf<SimpleFriend>()
    private lateinit var adapter: SelectFriendAdapter

    // Classe de dados simples para amigos
    data class SimpleFriend(val id: Int, val name: String, val email: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_member)

        groupId = intent.getIntExtra("GROUP_ID", -1)
        if (groupId == -1) {
            Toast.makeText(this, "Erro: Grupo inválido.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Obter o ID do utilizador a partir da sessão
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        userId = sharedPreferences.getInt("id_user", -1).toString()

        // Configurar a interface do utilizador
        val btnClose: ImageButton = findViewById(R.id.btnClose)
        val editEmail: EditText = findViewById(R.id.editEmail)
        val btnAddByEmail: Button = findViewById(R.id.btnAddByEmail)
        val layoutEmail: LinearLayout = findViewById(R.id.layoutEmail)
        recyclerFriends = findViewById(R.id.recyclerFriends)
        txtEmpty = findViewById(R.id.txtEmpty)

        // Gerir o sistema para a secção de e-mail
        ViewCompat.setOnApplyWindowInsetsListener(layoutEmail) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom + 16)
            insets
        }

        btnClose.setOnClickListener { finish() }

        // Configurar o RecyclerView
        recyclerFriends.layoutManager = LinearLayoutManager(this)
        adapter = SelectFriendAdapter(friendsList) { friend ->
            addMemberById(friend.id, friend.name)
        }
        recyclerFriends.adapter = adapter

        // Botão para adicionar por e-mail
        btnAddByEmail.setOnClickListener {
            val email = editEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                addMemberByEmail(email)
            } else {
                Toast.makeText(this, "Escreve um email válido.", Toast.LENGTH_SHORT).show()
            }
        }

        // Carregar amigos
        fetchFriends()
    }

    private fun fetchFriends() {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            friendsList.clear()
            val usersArray = response.optJSONArray("users")
            if (usersArray != null && usersArray.length() > 0) {
                for (i in 0 until usersArray.length()) {
                    val user = usersArray.getJSONObject(i)
                    val id = user.getInt("id_user")
                    val name = user.getString("name")
                    val email = user.optString("email", "")
                    friendsList.add(SimpleFriend(id, name, email))
                }
                txtEmpty.visibility = View.GONE
                recyclerFriends.visibility = View.VISIBLE
            } else {
                txtEmpty.visibility = View.VISIBLE
                recyclerFriends.visibility = View.GONE
            }
            adapter.notifyDataSetChanged()
        }, { error ->
            Toast.makeText(this, "Erro ao carregar amigos.", Toast.LENGTH_SHORT).show()
            txtEmpty.visibility = View.VISIBLE
            recyclerFriends.visibility = View.GONE
        })

        queue.add(request)
    }

    private fun addMemberById(friendId: Int, friendName: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/add_group_member.php"
        val queue = Volley.newRequestQueue(this)

        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                handleResponse(response, friendName)
            },
            { error ->
                Toast.makeText(this, "Erro de conexão.", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "group_id" to groupId.toString(),
                    "user_id" to friendId.toString()
                )
            }
            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=UTF-8"
            }
        }

        queue.add(request)
    }

    private fun addMemberByEmail(email: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/add_group_member.php"
        val queue = Volley.newRequestQueue(this)

        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                handleResponse(response, email)
            },
            { error ->
                Toast.makeText(this, "Erro de conexão.", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "group_id" to groupId.toString(),
                    "email" to email
                )
            }
            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=UTF-8"
            }
        }

        queue.add(request)
    }

    private fun handleResponse(response: String, identifier: String) {
        try {
            val json = JSONObject(response)
            if (json.has("success")) {
                Toast.makeText(this, json.getString("success"), Toast.LENGTH_LONG).show()
                setResult(RESULT_OK)
                finish()
            } else if (json.has("error")) {
                Toast.makeText(this, json.getString("error"), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao processar resposta.", Toast.LENGTH_SHORT).show()
        }
    }

    // Adaptador simples para selecionar amigos
    inner class SelectFriendAdapter(
        private val friends: List<SimpleFriend>,
        private val onFriendClick: (SimpleFriend) -> Unit
    ) : RecyclerView.Adapter<SelectFriendAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtAvatar: TextView = view.findViewById(R.id.txtAvatar)
            val txtName: TextView = view.findViewById(R.id.txtName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_friend_select, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val friend = friends[position]
            holder.txtName.text = friend.name
            holder.txtAvatar.text = if (friend.name.isNotEmpty()) {
                friend.name.first().uppercase()
            } else "?"

            holder.itemView.setOnClickListener {
                onFriendClick(friend)
            }
        }

        override fun getItemCount() = friends.size
    }
}
