package pt.ipt.projetodam_findme

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
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
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlin.math.abs

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequestCode = 1

    private lateinit var userId: String
    private var lastSentLocation: Location? = null
    private val MIN_DISTANCE_METERS = 30.0f
    private var isFirstLocation = true

    private lateinit var recyclerFriends: RecyclerView
    private val friendsList = ArrayList<Friend>()
    private lateinit var adapter: FriendsAdapter
    private val markersMap = HashMap<Int, Marker>()

    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

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

        setupFloatingUI()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupButtons()
        checkLocationPermission()
    }

    private fun redirecionarLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingUI() {
        val navBar = findViewById<LinearLayout>(R.id.navBar)
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheet)
        sheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // 1. LIMITAR ALTURA A 55%
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val desiredHeight = (screenHeight * 0.55).toInt()
        val params = bottomSheet.layoutParams
        params.height = desiredHeight
        bottomSheet.layoutParams = params

        // 2. INSETS & POSICIONAMENTO DA NAVBAR
        ViewCompat.setOnApplyWindowInsetsListener(navBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val paramsNav = view.layoutParams as CoordinatorLayout.LayoutParams
            paramsNav.bottomMargin = bars.bottom + dpToPx(10)
            view.layoutParams = paramsNav
            insets
        }

        // 3. ENCAIXAR A LISTA ATRÁS DA NAVBAR
        navBar.doOnLayout {
            val navbarHeight = it.height
            val navMarginBottom = (it.layoutParams as CoordinatorLayout.LayoutParams).bottomMargin
            val totalNavHeight = navbarHeight + navMarginBottom

            // Truque visual: Subtrair um pouco (20dp) para overlap
            val overlap = dpToPx(20)

            val sheetParams = bottomSheet.layoutParams as CoordinatorLayout.LayoutParams
            sheetParams.bottomMargin = totalNavHeight - overlap
            bottomSheet.layoutParams = sheetParams

            // Padding interno para compensar
            bottomSheet.setPadding(0, 0, 0, overlap + dpToPx(10))
        }

        // 4. ESTADO INICIAL: TOTALMENTE ESCONDIDO
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

        // 5. GESTOS MANUAIS NA NAVBAR
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
                        // Arrastar para CIMA -> Mostrar Lista
                        if (diff < -40 && !isDragging) {
                            if (sheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                                sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                                buscarAmigos()
                            }
                            isDragging = true
                        }
                        // Arrastar para BAIXO -> Esconder Lista
                        else if (diff > 40 && !isDragging) {
                            if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                                sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                            }
                            isDragging = true
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
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

        findViewById<View>(R.id.dragHandleArea).setOnTouchListener(touchListener)
        navBar.setOnTouchListener(touchListener)
    }

    // --- CORREÇÃO AQUI: APENAS UMA FUNÇÃO TOGGLESHEET ---
    private fun toggleSheet() {
        if (sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN ||
            sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            buscarAmigos()
        } else {
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun setupButtons() {
        recyclerFriends = findViewById(R.id.recyclerFriends)
        recyclerFriends.layoutManager = LinearLayoutManager(this)
        adapter = FriendsAdapter(friendsList) { friend ->
            val pos = LatLng(friend.latitude, friend.longitude)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
        }
        recyclerFriends.adapter = adapter

        findViewById<ImageButton>(R.id.btnAddFriend).setOnClickListener {
            Toast.makeText(this, "Adicionar Amigo", Toast.LENGTH_SHORT).show()
        }

        // Botão Pessoas
        findViewById<LinearLayout>(R.id.btnPessoas).setOnClickListener {
            toggleSheet()
        }

        findViewById<LinearLayout>(R.id.btnGrupos).setOnClickListener {
            Toast.makeText(this, "Dispositivos...", Toast.LENGTH_SHORT).show()
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        findViewById<LinearLayout>(R.id.btnCirculos).setOnClickListener {
            Toast.makeText(this, "Objetos...", Toast.LENGTH_SHORT).show()
            sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        findViewById<LinearLayout>(R.id.btnEu).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
            if (::mMap.isInitialized) enableBlueDot()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setPadding(0, 0, 0, dpToPx(100))

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
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply {
            setMinUpdateIntervalMillis(5000)
            setMinUpdateDistanceMeters(10f)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { currentLocation ->
                    if (isFirstLocation) {
                        isFirstLocation = false
                        val userPos = LatLng(currentLocation.latitude, currentLocation.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 18f))
                        buscarAmigos()
                    }
                    var shouldSend = false
                    if (lastSentLocation == null) shouldSend = true
                    else if (lastSentLocation!!.distanceTo(currentLocation) >= MIN_DISTANCE_METERS) shouldSend = true

                    if (shouldSend) {
                        lastSentLocation = currentLocation
                        enviarLocalizacao(userId, currentLocation.latitude, currentLocation.longitude)
                        buscarAmigos()
                    }
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun enviarLocalizacao(userId: String, latitude: Double, longitude: Double) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/update_location.php"
        val queue = Volley.newRequestQueue(this)
        val postRequest = object : StringRequest(Request.Method.POST, url, {}, { error -> Log.e("API", "Erro envio: ${error.message}") }) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf("user_id" to userId, "latitude" to latitude.toString(), "longitude" to longitude.toString())
            }
        }
        queue.add(postRequest)
    }

    private fun buscarAmigos() {
        if (lastSentLocation == null) return
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)
        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                val usersArray = response.getJSONArray("users")
                friendsList.clear()
                for (i in 0 until usersArray.length()) {
                    val userObj = usersArray.getJSONObject(i)
                    val friendId = userObj.getInt("id_user")
                    val name = userObj.getString("name")
                    val lat = userObj.getDouble("latitude")
                    val lon = userObj.getDouble("longitude")
                    val lastUpd = userObj.optString("last_update", "Desconhecido")
                    val friendPos = LatLng(lat, lon)
                    if (markersMap.containsKey(friendId)) {
                        markersMap[friendId]?.position = friendPos
                    } else {
                        val markerOptions = MarkerOptions().position(friendPos).title(name).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        val marker = mMap.addMarker(markerOptions)
                        if (marker != null) markersMap[friendId] = marker
                    }
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