package pt.ipt.projetodam_findme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
// Removi o import do FloatingActionButton pois já não é usado

class GroupsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var recyclerGroups: RecyclerView
    private lateinit var groupsAdapter: GroupsAdapter
    private val groupsList = ArrayList<Group>()
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groups)

        // 1. Configurar Padding da Navbar
        val navBarBottom = findViewById<LinearLayout>(R.id.navBarBottom)
        ViewCompat.setOnApplyWindowInsetsListener(navBarBottom) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPadding = (10 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, originalPadding + bars.bottom)
            insets
        }

        // 2. Obter User ID da Sessão
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        userId = sharedPreferences.getInt("id_user", -1).toString()

        // 3. Inicializar Mapa (Fundo)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapGroups) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 4. Configurar RecyclerView (Lista)
        recyclerGroups = findViewById(R.id.recyclerGroups)
        recyclerGroups.layoutManager = LinearLayoutManager(this)
        groupsAdapter = GroupsAdapter(groupsList, this)
        recyclerGroups.adapter = groupsAdapter

        // ====================================================================
        // 5. Botão Criar Grupo (CORRIGIDO)
        // Mudámos de FloatingActionButton para Button normal (MaterialButton)
        // e o ID agora é btnCreateGroup
        // ====================================================================
        val btnCreate = findViewById<Button>(R.id.btnCreateGroup)
        btnCreate.setOnClickListener {
            val intent = Intent(this, CreateGroupActivity::class.java)
            startActivity(intent)
        }

        // 6. Configurar Navbar (Navegação)
        setupNavigation()

        // 7. Carregar Dados
        buscarGrupos()
    }

    override fun onResume() {
        super.onResume()
        buscarGrupos()
    }

    private fun setupNavigation() {
        // Botão PESSOAS -> Vai para o MainActivity
        findViewById<LinearLayout>(R.id.navPessoas).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        // Botão GRUPOS -> Scroll para o topo
        findViewById<LinearLayout>(R.id.navGrupos).setOnClickListener {
            recyclerGroups.smoothScrollToPosition(0)
        }

        // Botão ZONA
        findViewById<LinearLayout>(R.id.navZona).setOnClickListener {
            Toast.makeText(this, "Zona: Em breve", Toast.LENGTH_SHORT).show()
        }

        // Botão EU -> Vai para ProfileActivity
        findViewById<LinearLayout>(R.id.navEu).setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun buscarGrupos() {
        if (userId == "-1") return

        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_my_groups.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                groupsList.clear()
                val groupsArray = response.optJSONArray("groups")

                if (groupsArray != null) {
                    for (i in 0 until groupsArray.length()) {
                        val groupObj = groupsArray.getJSONObject(i)
                        val id = groupObj.getInt("id_group")
                        val name = groupObj.getString("name_group")
                        val totalMembers = groupObj.getInt("total_members")

                        groupsList.add(Group(id, name, totalMembers))
                    }
                }
                groupsAdapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Log.e("GroupsActivity", "Erro ao processar JSON: ${e.message}")
            }
        }, { error ->
            Log.e("GroupsActivity", "Erro Volley: ${error.message}")
            // Podes comentar o Toast se não quiseres avisos de erro na UI
            // Toast.makeText(this, "Erro ao carregar grupos", Toast.LENGTH_SHORT).show()
        })

        queue.add(request)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        mMap.uiSettings.apply {
            isScrollGesturesEnabled = false
            isZoomGesturesEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isMapToolbarEnabled = false
        }

        // Centrar em Portugal
        val portugal = LatLng(39.55, -7.85)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(portugal, 6f))

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Lógica de localização opcional
        }
    }
}