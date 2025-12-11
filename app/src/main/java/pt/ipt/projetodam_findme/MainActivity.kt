package pt.ipt.projetodam_findme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import pt.ipt.projetodam_findme.services.LocationService

enum class Tab { PEOPLE, GROUPS, CIRCLES, ME }

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val permissionRequestCode = 101

    private lateinit var userId: String
    private var lastSentLocation: Location? = null
    private val MIN_DISTANCE_METERS = 30.0f
    private var isFirstLocation = true

    private lateinit var recyclerFriends: RecyclerView
    private val friendsList = ArrayList<Friend>()
    private lateinit var adapter: FriendsAdapter
    private val markersMap = HashMap<Int, Marker>()

    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    // Estado atual (apenas PEOPLE ou CIRCLES, pois GROUPS e ME abrem outras Activities)
    private var currentTab = Tab.PEOPLE

    // Elemento para o botão de adicionar
    private lateinit var btnAddFriend: ImageView

    // UI Elements - ABAS
    private lateinit var tabPessoas: LinearLayout
    private lateinit var tabGrupos: LinearLayout
    private lateinit var tabCirculos: LinearLayout
    private lateinit var tabEu: LinearLayout

    // UI Elements - INDICADORES
    private lateinit var indicatorPessoas: View
    private lateinit var indicatorGrupos: View
    private lateinit var indicatorCirculos: View
    private lateinit var indicatorEu: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("logado", false)) {
            redirecionarLogin()
            return
        }
        val id = sharedPreferences.getInt("id_user", -1)
        if (id == -1) {
            redirecionarLogin()
            return
        }

        userId = id.toString()

        setContentView(R.layout.activity_main)

        // Iniciar Serviço de Notificações
        val notifIntent = Intent(this, pt.ipt.projetodam_findme.services.NotificationService::class.java)
        notifIntent.putExtra("USER_ID", userId)
        ContextCompat.startForegroundService(this, notifIntent)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        checkLocationPermission()
    }

    private fun redirecionarLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setupUI() {
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        sheetBehavior = BottomSheetBehavior.from(bottomSheet)

        sheetBehavior.peekHeight = dpToPx(240)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        recyclerFriends = findViewById(R.id.recyclerFriends)
        recyclerFriends.layoutManager = LinearLayoutManager(this)

        // Configurar Adapter de Amigos
        adapter = FriendsAdapter(
            friendsList = friendsList,
            clickListener = { friend ->
                if (::mMap.isInitialized) {
                    val pos = LatLng(friend.latitude, friend.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
                    sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            },
            currentUserId = userId.toInt()
        )
        recyclerFriends.adapter = adapter

        // Botão + (Add) - Adiciona Amigos
        btnAddFriend = findViewById(R.id.btnAddFriend)
        btnAddFriend.setOnClickListener {
            handleAddButtonClick()
        }

        // Inicializar Views
        tabPessoas = findViewById(R.id.tabPessoas)
        tabGrupos = findViewById(R.id.tabGrupos)
        tabCirculos = findViewById(R.id.tabCirculos)
        tabEu = findViewById(R.id.tabEu)

        indicatorPessoas = findViewById(R.id.indicatorPessoas)
        indicatorGrupos = findViewById(R.id.indicatorGrupos)
        indicatorCirculos = findViewById(R.id.indicatorCirculos)
        indicatorEu = findViewById(R.id.indicatorEu)

        // Estado Inicial
        atualizarEstiloAbas(tabPessoas)
        currentTab = Tab.PEOPLE

        // --- LISTENERS DAS ABAS ---

        // 1. PESSOAS (Mantém nesta activity)
        tabPessoas.setOnClickListener {
            atualizarEstiloAbas(tabPessoas)
            if (currentTab != Tab.PEOPLE) {
                currentTab = Tab.PEOPLE
                recyclerFriends.adapter = adapter
            }
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            buscarAmigos()
        }

        // 2. GRUPOS (Abre nova Activity)
        tabGrupos.setOnClickListener {
            // Inicia a nova Activity de Grupos
            val intent = Intent(this, GroupsActivity::class.java)
            startActivity(intent)
            // Remove a animação de transição para parecer uma navbar contínua
            overridePendingTransition(0, 0)
            // Não fazemos finish() aqui se quisermos que o Back button volte para o mapa principal,
            // mas como é uma navbar, talvez queiras finish(). Depende da tua preferência de navegação.
            // Para "Single Activity feel", geralmente não se faz finish() no Main, mas nos outros sim.
        }

        // 3. CÍRCULOS (Futuro)
        tabCirculos.setOnClickListener {
            atualizarEstiloAbas(tabCirculos)
            currentTab = Tab.CIRCLES
            Toast.makeText(this, "Círculos (Em breve)", Toast.LENGTH_SHORT).show()
        }

        // 4. EU (Abre ProfileActivity)
        tabEu.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    private fun handleAddButtonClick() {
        if (currentTab == Tab.PEOPLE) {
            startActivity(Intent(this, AddFriendActivity::class.java))
        } else {
            Toast.makeText(this, "Funcionalidade indisponível aqui.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun atualizarEstiloAbas(abaSelecionada: LinearLayout) {
        val listaAbas = listOf(
            Pair(tabPessoas, indicatorPessoas),
            Pair(tabGrupos, indicatorGrupos),
            Pair(tabCirculos, indicatorCirculos),
            Pair(tabEu, indicatorEu)
        )
        val corInativa = Color.parseColor("#AAAAAA")
        val corAtiva = Color.WHITE

        for ((aba, indicador) in listaAbas) {
            val icon = aba.getChildAt(0) as ImageView
            val text = aba.getChildAt(1) as TextView

            if (aba == abaSelecionada) {
                icon.setColorFilter(corAtiva)
                text.setTextColor(corAtiva)
                text.typeface = Typeface.DEFAULT_BOLD
                indicador.visibility = View.VISIBLE
            } else {
                icon.setColorFilter(corInativa)
                text.setTextColor(corInativa)
                text.typeface = Typeface.DEFAULT
                indicador.visibility = View.INVISIBLE
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun criarIconeCircular(nome: String): BitmapDescriptor {
        val width = 120
        val height = 120
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.color = Color.parseColor("#444444")
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)

        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD

        val xPos = width / 2f
        val yPos = (height / 2f) - ((paint.descent() + paint.ascent()) / 2)
        val letra = if (nome.isNotEmpty()) nome.first().toString().uppercase() else "?"

        canvas.drawText(letra, xPos, yPos, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // --- LÓGICA DE PERMISSÕES ---
    private fun checkLocationPermission() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), permissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
                if (::mMap.isInitialized) enableBlueDot()
            } else {
                Toast.makeText(this, "Permissão de localização necessária.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setPadding(0, dpToPx(80), 0, dpToPx(240))

        val portugal = LatLng(39.55, -7.85)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(portugal, 6f))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableBlueDot()
        }
    }

    private fun enableBlueDot() {
        try {
            if (::mMap.isInitialized && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.isMyLocationEnabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(this, LocationService::class.java)
        intent.putExtra("USER_ID", userId)
        ContextCompat.startForegroundService(this, intent)

        enableBlueDot()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply {
            setMinUpdateIntervalMillis(2000)
        }.build()

        val uiLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { currentLocation ->
                    lastSentLocation = currentLocation

                    if (isFirstLocation && ::mMap.isInitialized) {
                        isFirstLocation = false
                        val userPos = LatLng(currentLocation.latitude, currentLocation.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 15f))
                    }

                    if (currentTab == Tab.PEOPLE) buscarAmigos()
                    // Grupos já não são carregados aqui
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, uiLocationCallback, Looper.getMainLooper())
    }

    private fun buscarAmigos() {
        if (lastSentLocation == null) return
        if (!::mMap.isInitialized) return

        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                // Se o utilizador trocou de aba enquanto a request ocorria
                if (currentTab != Tab.PEOPLE) return@JsonObjectRequest
                if (!::mMap.isInitialized) return@JsonObjectRequest

                val usersArray = response.getJSONArray("users")
                friendsList.clear()

                markersMap.values.forEach { it.remove() }
                markersMap.clear()

                for (i in 0 until usersArray.length()) {
                    val userObj = usersArray.getJSONObject(i)
                    val friendId = userObj.getInt("id_user")
                    val name = userObj.getString("name")
                    val lat = userObj.getDouble("latitude")
                    val lon = userObj.getDouble("longitude")
                    val lastUpd = userObj.optString("last_update", "Desconhecido")

                    val friendPos = LatLng(lat, lon)

                    val markerOptions = MarkerOptions()
                        .position(friendPos)
                        .title(name)
                        .icon(criarIconeCircular(name))
                        .anchor(0.5f, 0.5f)

                    val marker = mMap.addMarker(markerOptions)
                    if (marker != null) markersMap[friendId] = marker

                    val results = FloatArray(1)
                    Location.distanceBetween(lastSentLocation!!.latitude, lastSentLocation!!.longitude, lat, lon, results)
                    friendsList.add(Friend(friendId, name, lat, lon, results[0], lastUpd))
                }

                adapter.notifyDataSetChanged()

            } catch (e: Exception) { Log.e("API", "Erro JSON: ${e.message}") }
        }, { error -> Log.e("API", "Erro Volley: ${error.message}") })

        queue.add(request)
    }
}