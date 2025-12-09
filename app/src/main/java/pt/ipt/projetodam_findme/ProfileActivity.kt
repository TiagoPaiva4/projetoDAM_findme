package pt.ipt.projetodam_findme

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
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

    // Variáveis para os pedidos de amizade
    private lateinit var recyclerRequests: RecyclerView
    private val requestsList = ArrayList<RequestItem>()
    private lateinit var requestsAdapter: RequestsAdapter
    private var userId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // 1. Obter ID do user
        val prefs = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        userId = prefs.getInt("id_user", -1)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        txtLocation = findViewById(R.id.txtLocation)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapProfile) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 2. Configurar Lista de Pedidos
        recyclerRequests = findViewById(R.id.recyclerRequests)
        recyclerRequests.layoutManager = LinearLayoutManager(this)

        requestsAdapter = RequestsAdapter(requestsList) { idFriendship ->
            aceitarPedido(idFriendship)
        }
        recyclerRequests.adapter = requestsAdapter

        // 3. Configurar UI e Buscar Dados
        setupFloatingUI()
        setupButtons()
        buscarPedidosPendentes()
    }

    // --- LÓGICA DE PEDIDOS DE AMIZADE ---

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
                        requestsList.add(RequestItem(
                            obj.getInt("id_friendship"),
                            obj.getString("name"),
                            obj.getString("email")
                        ))
                    }
                    requestsAdapter.notifyDataSetChanged()

                    if (requestsList.isNotEmpty()) {
                        Toast.makeText(this, "Tens ${requestsList.size} pedidos novos!", Toast.LENGTH_SHORT).show()
                        // Opcional: Se houver pedidos, abrir a aba automaticamente
                        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
                buscarPedidosPendentes() // Atualiza a lista
            },
            { error -> Toast.makeText(this, "Erro: ${error.message}", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams() = mutableMapOf(
                "id_friendship" to idFriendship.toString(),
                "action" to "accept"
            )
        }
        queue.add(request)
    }

    // --- LÓGICA DE UI FLUTUANTE (Igual à Main) ---

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingUI() {
        val navBar = findViewById<LinearLayout>(R.id.navBarProfile)
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheetProfile)
        sheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // 1. Insets (Margens do sistema)
        ViewCompat.setOnApplyWindowInsetsListener(navBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val paramsNav = view.layoutParams as CoordinatorLayout.LayoutParams
            paramsNav.bottomMargin = bars.bottom + dpToPx(10)
            view.layoutParams = paramsNav
            insets
        }

        // 2. Encaixar a Lista Atrás da Navbar (Lógica de Overlap)
        // AQUI ESTAVA O TEU ERRO: Usei a lógica completa para calcular o overlap correto
        navBar.doOnLayout {
            val navbarHeight = it.height
            val navMarginBottom = (it.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin
            val totalNavHeight = navbarHeight + navMarginBottom

            // Overlap de 20dp para esconder a união (fundo preto)
            val overlap = dpToPx(20)

            val sheetParams = bottomSheet.layoutParams as CoordinatorLayout.LayoutParams

            // A margem de baixo da aba é a altura da navbar MENOS o overlap
            sheetParams.bottomMargin = totalNavHeight - overlap
            bottomSheet.layoutParams = sheetParams

            // Padding interno para o conteúdo não ficar escondido
            bottomSheet.setPadding(0, 0, 0, overlap)
        }

        // 3. Estado Inicial: ESCONDIDO
        sheetBehavior.peekHeight = 0
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        sheetBehavior.isHideable = true
        sheetBehavior.skipCollapsed = true

        // Callback para garantir estado
        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        // 4. Lógica de Gestos (Manual)
        val touchListener = object : View.OnTouchListener {
            var startY = 0f
            var startTime = 0L
            var isDragging = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        startTime = System.currentTimeMillis()
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diff = event.rawY - startY
                        if (diff < -40 && !isDragging) { // Cima -> Abre
                            if (sheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                                sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                            }
                            isDragging = true
                        } else if (diff > 40 && !isDragging) { // Baixo -> Fecha
                            if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                                sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                            }
                            isDragging = true
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Se for um clique rápido, alterna
                        if (!isDragging && (System.currentTimeMillis() - startTime) < 200) {
                            toggleSheet()
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        }

        findViewById<View>(R.id.dragHandleAreaProfile).setOnTouchListener(touchListener)
        navBar.setOnTouchListener(touchListener)
    }

    private fun toggleSheet() {
        if (sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN ||
            sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun setupButtons() {
        // Botão Sair
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            getSharedPreferences("SessaoUsuario", MODE_PRIVATE).edit { clear() }
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        // Navegação Navbar
        findViewById<LinearLayout>(R.id.btnPessoas).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish() // Importante: fecha o perfil para não empilhar activities
        }

        findViewById<LinearLayout>(R.id.btnGrupos).setOnClickListener {
            Toast.makeText(this, "Grupos", Toast.LENGTH_SHORT).show()
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        findViewById<LinearLayout>(R.id.btnCirculos).setOnClickListener {
            Toast.makeText(this, "Círculos", Toast.LENGTH_SHORT).show()
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        // Botão Eu (estamos aqui, então alterna a aba)
        findViewById<LinearLayout>(R.id.btnEu).setOnClickListener {
            toggleSheet()
        }
    }

    // --- MAPA E LOCALIZAÇÃO ---

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.uiSettings.isZoomControlsEnabled = true
        // Padding para o logo do Google não ficar debaixo da navbar
        mMap.setPadding(0, 0, 0, dpToPx(130))

        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
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