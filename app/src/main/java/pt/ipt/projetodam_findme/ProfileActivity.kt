package pt.ipt.projetodam_findme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

class ProfileActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var userId: String
    private lateinit var tvMyAddress: TextView
    private lateinit var switchLocation: MaterialSwitch
    private lateinit var tvShareStatus: TextView
    private lateinit var recyclerRequests: RecyclerView
    private lateinit var adapter: RequestsAdapter
    private val requestsList = ArrayList<FriendRequest>()
    private lateinit var mMap: GoogleMap

    // Variáveis para a funcionalidade Expandir/Recolher
    private lateinit var btnExpandMore: ImageView
    private lateinit var layoutMoreOptions: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // =========================================================================
        // Ajuste da Navbar (Mantido)
        // =========================================================================
        val navBarBottom = findViewById<LinearLayout>(R.id.navBarBottom)
        ViewCompat.setOnApplyWindowInsetsListener(navBarBottom) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPaddingBottom = (10 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, originalPaddingBottom + bars.bottom)
            insets
        }

        // 1. Inicializar Mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapProfile) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 2. Dados de Sessão
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        userId = sharedPreferences.getInt("id_user", -1).toString()
        val isSharing = sharedPreferences.getBoolean("share_location", true)

        // 3. Vincular Views
        tvMyAddress = findViewById(R.id.tvMyAddress)
        switchLocation = findViewById(R.id.switchLocation)
        tvShareStatus = findViewById(R.id.tvShareStatus)
        recyclerRequests = findViewById(R.id.recyclerRequests)

        // Novas Views para o expandir
        btnExpandMore = findViewById(R.id.btnExpandMore)
        layoutMoreOptions = findViewById(R.id.layoutMoreOptions)

        // 4. Lógica de Expandir/Recolher
        btnExpandMore.setOnClickListener {
            if (layoutMoreOptions.visibility == View.VISIBLE) {
                // Esconder
                layoutMoreOptions.visibility = View.GONE
                // Rodar seta de volta para baixo (animação suave)
                btnExpandMore.animate().rotation(0f).setDuration(300).start()
            } else {
                // Mostrar
                layoutMoreOptions.visibility = View.VISIBLE
                // Rodar seta para cima (animação suave)
                btnExpandMore.animate().rotation(180f).setDuration(300).start()

                // Opcional: Fazer scroll para baixo para ver o conteúdo novo
                // (Se necessário, mas o user costuma fazer scroll manualmente)
            }
        }

        // 5. Configurar Switch de Partilha
        switchLocation.isChecked = isSharing
        tvShareStatus.text = if (isSharing) "Visível para amigos" else "Localização oculta"
        switchLocation.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("share_location", isChecked).apply()
            val status = if (isChecked) "Visível para amigos" else "Localização oculta"
            tvShareStatus.text = status
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        }

        // 6. Configurar Lista de Pedidos
        recyclerRequests.layoutManager = LinearLayoutManager(this)
        adapter = RequestsAdapter(requestsList,
            onAccept = { req -> gerirPedido(req, "accepted") },
            onReject = { req -> gerirPedido(req, "rejected") }
        )
        recyclerRequests.adapter = adapter

        // 7. Carregar Dados
        obterMoradaAtual()
        carregarPedidos()

        // 8. Navegação
        findViewById<LinearLayout>(R.id.navPessoas).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.navGrupos).setOnClickListener { finish() }
        findViewById<LinearLayout>(R.id.navZona).setOnClickListener {
            Toast.makeText(this, "Zona: Em breve", Toast.LENGTH_SHORT).show()
        }

        // 9. Botão de Logout
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            sharedPreferences.edit().clear().apply()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.uiSettings.isZoomControlsEnabled = false
        mMap.uiSettings.isScrollGesturesEnabled = false

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
                }
            }
        }
    }

    private fun obterMoradaAtual() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tvMyAddress.text = "Sem permissão"
            return
        }

        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
            location?.let {
                try {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val rua = addresses[0].thoroughfare ?: ""
                        val cidade = addresses[0].locality ?: addresses[0].subAdminArea ?: ""
                        tvMyAddress.text = if (rua.isNotEmpty()) "$rua, $cidade" else cidade
                    } else {
                        tvMyAddress.text = "Localização desconhecida"
                    }
                } catch (e: Exception) {
                    tvMyAddress.text = "Erro ao obter morada"
                }
            }
        }
    }

    private fun carregarPedidos() {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_pending_requests.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            requestsList.clear()
            val requestsArray = response.optJSONArray("requests")
            if (requestsArray != null) {
                for (i in 0 until requestsArray.length()) {
                    val obj = requestsArray.getJSONObject(i)
                    requestsList.add(FriendRequest(
                        id = obj.getInt("id_friendship"),
                        senderId = 0,
                        name = obj.getString("name")
                    ))
                }
            }
            adapter.notifyDataSetChanged()
        }, {
            Log.e("Profile", "Erro API: ${it.message}")
        })
        queue.add(request)
    }

    private fun gerirPedido(request: FriendRequest, action: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/accept_request.php"
        val queue = Volley.newRequestQueue(this)

        val postRequest = object : StringRequest(Method.POST, url,
            {
                val msg = if (action == "accepted") "Pedido aceite" else "Pedido recusado"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                carregarPedidos()
            },
            { Toast.makeText(this, "Erro ao processar", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf("request_id" to request.id.toString(), "action" to action)
            }
        }
        queue.add(postRequest)
    }
}