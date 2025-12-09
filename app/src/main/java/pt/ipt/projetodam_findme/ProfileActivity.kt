package pt.ipt.projetodam_findme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.Locale

class ProfileActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var txtLocation: TextView

    private lateinit var recyclerRequests: RecyclerView
    private val requestsList = ArrayList<RequestItem>()
    private lateinit var requestsAdapter: RequestsAdapter
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val prefs = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        userId = prefs.getInt("id_user", -1)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        txtLocation = findViewById(R.id.txtLocation)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapProfile) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Configurar Lista de Pedidos
        recyclerRequests = findViewById(R.id.recyclerRequests)
        recyclerRequests.layoutManager = LinearLayoutManager(this)
        requestsAdapter = RequestsAdapter(requestsList) { idFriendship ->
            aceitarPedido(idFriendship)
        }
        recyclerRequests.adapter = requestsAdapter

        setupUI()
        buscarPedidosPendentes()
    }

    private fun setupUI() {
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheetProfile)
        sheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // Peek Height para ver o menu "Eu" e a localização
        // Podes ajustar para mais ou menos conforme o ecrã
        sheetBehavior.peekHeight = dpToPx(300)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // Botão Sair
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            getSharedPreferences("SessaoUsuario", MODE_PRIVATE).edit { clear() }
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        // Navegação das Abas (Topo)
        findViewById<LinearLayout>(R.id.tabPessoas).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish() // Fecha o perfil
        }

        findViewById<LinearLayout>(R.id.tabGrupos).setOnClickListener {
            Toast.makeText(this, "Grupos (Em breve)", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.tabEu).setOnClickListener {
            // Já estamos aqui, expande ou contrai
            if (sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    // --- (Restante código de API e Mapa permanece igual) ---
    // Copiar: buscarPedidosPendentes, aceitarPedido, onMapReady, enableMyLocation, getStreetName

    private fun buscarPedidosPendentes() {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_pending_requests.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)
        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    requestsList.clear()
                    val array = response.getJSONArray("requests")
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        requestsList.add(RequestItem(obj.getInt("id_friendship"), obj.getString("name"), obj.getString("email")))
                    }
                    requestsAdapter.notifyDataSetChanged()
                } catch (e: Exception) { e.printStackTrace() }
            },
            { error -> error.printStackTrace() }
        )
        queue.add(request)
    }

    private fun aceitarPedido(idFriendship: Int) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/accept_request.php"
        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(Method.POST, url,
            { _ ->
                Toast.makeText(this, "Amigo aceite!", Toast.LENGTH_SHORT).show()
                buscarPedidosPendentes()
            },
            { error -> Toast.makeText(this, "Erro: ${error.message}", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams() = mutableMapOf("id_friendship" to idFriendship.toString(), "action" to "accept")
        }
        queue.add(request)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.uiSettings.isZoomControlsEnabled = true
        // Padding para o logo não ficar tapado (Topo: 0, Fundo: 300dp)
        mMap.setPadding(0, 0, 0, dpToPx(300))
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
                    getStreetName(location)
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun getStreetName(location: Location) {
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val rua = address.thoroughfare ?: "Rua desconhecida"
                val cidade = address.locality ?: ""
                txtLocation.text = if (cidade.isNotEmpty()) "$rua, $cidade" else rua
            } else {
                txtLocation.text = "Localização disponível"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            txtLocation.text = "A partilhar localização"
        }
    }
}