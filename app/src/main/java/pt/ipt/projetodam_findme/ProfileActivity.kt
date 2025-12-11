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

    // Novas variáveis para a morada dividida
    private lateinit var tvStreet: TextView
    private lateinit var tvCityZip: TextView

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

        // Ajuste da Navbar (Bottom Insets)
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
        // ATENÇÃO: Mudámos de tvMyAddress para estas duas
        tvStreet = findViewById(R.id.tvStreet)
        tvCityZip = findViewById(R.id.tvCityZip)

        switchLocation = findViewById(R.id.switchLocation)
        tvShareStatus = findViewById(R.id.tvShareStatus)
        recyclerRequests = findViewById(R.id.recyclerRequests)

        // Novas Views para o expandir
        btnExpandMore = findViewById(R.id.btnExpandMore)
        layoutMoreOptions = findViewById(R.id.layoutMoreOptions)

        // 4. Lógica de Expandir/Recolher
        btnExpandMore.setOnClickListener {
            if (layoutMoreOptions.visibility == View.VISIBLE) {
                layoutMoreOptions.visibility = View.GONE
                btnExpandMore.animate().rotation(0f).setDuration(300).start()
            } else {
                layoutMoreOptions.visibility = View.VISIBLE
                btnExpandMore.animate().rotation(180f).setDuration(300).start()
            }
        }

        // 5. Configurar Switch de Partilha
        switchLocation.isChecked = isSharing
        tvShareStatus.text = if (isSharing) "Ativa" else "Inativa"

        switchLocation.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("share_location", isChecked).apply()
            val status = if (isChecked) "Ativa" else "Inativa"
            tvShareStatus.text = status
            // Toast.makeText(this, status, Toast.LENGTH_SHORT).show() // Opcional
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
            tvStreet.text = "Sem permissão"
            tvCityZip.text = ""
            return
        }

        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
            location?.let {
                try {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)

                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0]

                        // Extrair partes da morada
                        val rua = address.thoroughfare ?: "Rua desconhecida"
                        val numero = address.subThoroughfare ?: "" // Número da porta
                        val cp = address.postalCode ?: ""
                        val cidade = address.locality ?: address.subAdminArea ?: ""
                        val pais = address.countryName ?: ""

                        // LINHA 1: Rua + Número
                        val linha1 = if (numero.isNotEmpty()) "$rua, $numero" else rua
                        tvStreet.text = linha1

                        // LINHA 2: CP + Cidade + País
                        // Ex: 1250-141 Lisboa, Portugal
                        val linha2Builder = StringBuilder()
                        if (cp.isNotEmpty()) linha2Builder.append("$cp ")
                        if (cidade.isNotEmpty()) linha2Builder.append("$cidade")
                        if (pais.isNotEmpty()) linha2Builder.append(", $pais")

                        tvCityZip.text = linha2Builder.toString()

                    } else {
                        tvStreet.text = "Localização desconhecida"
                        tvCityZip.text = ""
                    }
                } catch (e: Exception) {
                    tvStreet.text = "Erro ao obter morada"
                    tvCityZip.text = ""
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