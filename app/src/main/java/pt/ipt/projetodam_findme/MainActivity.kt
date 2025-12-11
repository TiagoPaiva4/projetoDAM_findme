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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
    private var isFirstLocation = true

    private lateinit var recyclerFriends: RecyclerView
    private val friendsList = ArrayList<Friend>()
    private lateinit var adapter: FriendsAdapter
    private val markersMap = HashMap<Int, Marker>()

    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    private var currentTab = Tab.PEOPLE
    private lateinit var btnAddFriend: ImageView

    // UI Elements - ABAS
    private lateinit var tabPessoas: LinearLayout
    private lateinit var tabGrupos: LinearLayout
    private lateinit var tabCirculos: LinearLayout
    private lateinit var tabEu: LinearLayout

    // INDICADORES REMOVIDOS AQUI

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

        try {
            val notifIntent = Intent(this, pt.ipt.projetodam_findme.services.NotificationService::class.java)
            notifIntent.putExtra("USER_ID", userId)
            ContextCompat.startForegroundService(this, notifIntent)
        } catch (e: Exception) { e.printStackTrace() }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapMain) as SupportMapFragment
        mapFragment.getMapAsync(this)

        checkLocationPermission()
    }

    private fun redirecionarLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setupUI() {
        // Correção Navbar
        val navBarBottom = findViewById<LinearLayout>(R.id.navBarBottom)
        ViewCompat.setOnApplyWindowInsetsListener(navBarBottom) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = dpToPx(10) + insets.bottom)
            windowInsets
        }

        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheetFriends)
        sheetBehavior = BottomSheetBehavior.from(bottomSheet)
        sheetBehavior.peekHeight = dpToPx(240)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        recyclerFriends = findViewById(R.id.recyclerFriends)
        recyclerFriends.layoutManager = LinearLayoutManager(this)

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

        btnAddFriend = findViewById(R.id.btnAddFriend)
        btnAddFriend.setOnClickListener {
            handleAddButtonClick()
        }

        // Inicializar NavItems
        tabPessoas = findViewById(R.id.navPessoas)
        tabGrupos = findViewById(R.id.navGrupos)
        tabCirculos = findViewById(R.id.navZona)
        tabEu = findViewById(R.id.navEu)

        // INDICADORES REMOVIDOS DE FINDVIEWBYID

        // Estado Inicial
        atualizarEstiloAbas(tabPessoas)
        currentTab = Tab.PEOPLE

        // Listeners
        tabPessoas.setOnClickListener {
            atualizarEstiloAbas(tabPessoas)
            if (currentTab != Tab.PEOPLE) {
                currentTab = Tab.PEOPLE
                recyclerFriends.adapter = adapter
            }
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            buscarAmigos()
        }

        tabGrupos.setOnClickListener {
            atualizarEstiloAbas(tabGrupos)
            currentTab = Tab.GROUPS
            Toast.makeText(this, "Grupos selecionado", Toast.LENGTH_SHORT).show()
        }

        tabCirculos.setOnClickListener {
            atualizarEstiloAbas(tabCirculos)
            currentTab = Tab.CIRCLES
            Toast.makeText(this, "Círculos (Em breve)", Toast.LENGTH_SHORT).show()
        }

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

    // Função simplificada (apenas muda cores, não indicadores)
    private fun atualizarEstiloAbas(abaSelecionada: LinearLayout) {
        val listaAbas = listOf(tabPessoas, tabGrupos, tabCirculos, tabEu)

        val corAtiva = Color.parseColor("#3A8DDE")
        val corInativa = Color.parseColor("#8E8E93")

        for (aba in listaAbas) {
            val icon = aba.getChildAt(0) as ImageView
            val text = aba.getChildAt(1) as TextView

            if (aba == abaSelecionada) {
                icon.setColorFilter(corAtiva)
                text.setTextColor(corAtiva)
                text.typeface = Typeface.DEFAULT_BOLD
            } else {
                icon.setColorFilter(corInativa)
                text.setTextColor(corInativa)
                text.typeface = Typeface.DEFAULT
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
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setPadding(0, dpToPx(50), 0, dpToPx(90))

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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        val intent = Intent(this, LocationService::class.java)
        intent.putExtra("USER_ID", userId)
        ContextCompat.startForegroundService(this, intent)

        enableBlueDot()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply {
            setMinUpdateIntervalMillis(2000)
        }.build()

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { currentLocation ->
                    lastSentLocation = currentLocation
                    if (isFirstLocation && ::mMap.isInitialized) {
                        isFirstLocation = false
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLocation.latitude, currentLocation.longitude), 15f))
                    }
                    if (currentTab == Tab.PEOPLE) buscarAmigos()
                }
            }
        }, Looper.getMainLooper())
    }

    private fun buscarAmigos() {
        if (lastSentLocation == null || !::mMap.isInitialized || currentTab != Tab.PEOPLE) return

        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                if (currentTab != Tab.PEOPLE) return@JsonObjectRequest
                markersMap.values.forEach { it.remove() }
                markersMap.clear()
                friendsList.clear()

                val usersArray = response.getJSONArray("users")
                for (i in 0 until usersArray.length()) {
                    val userObj = usersArray.getJSONObject(i)
                    val friendId = userObj.getInt("id_user")
                    val name = userObj.getString("name")
                    val lat = userObj.getDouble("latitude")
                    val lon = userObj.getDouble("longitude")
                    val lastUpd = userObj.optString("last_update", "Desconhecido")

                    val friendPos = LatLng(lat, lon)
                    val marker = mMap.addMarker(MarkerOptions()
                        .position(friendPos)
                        .title(name)
                        .icon(criarIconeCircular(name))
                        .anchor(0.5f, 0.5f))
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