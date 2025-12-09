package pt.ipt.projetodam_findme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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
import org.json.JSONObject

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequestCode = 1

    // Variáveis de controlo
    private lateinit var userId: String
    private var lastSentLocation: Location? = null
    private val MIN_DISTANCE_METERS = 30.0f

    // Controlo de Zoom inicial
    private var isFirstLocation = true

    // Lista para guardar os marcadores dos amigos (ID do user -> Marcador no mapa)
    private val markersMap = HashMap<Int, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Verificar sessão
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("logado", false)) {
            redirecionarLogin()
            return
        }

        // 2. Obter o ID do utilizador corretamente
        val id = sharedPreferences.getInt("id_user", -1)
        if (id == -1) {
            Toast.makeText(this, "Sessão inválida. Faça login novamente.", Toast.LENGTH_LONG).show()
            redirecionarLogin()
            return
        }
        userId = id.toString()

        setContentView(R.layout.activity_main)

        // 3. Inicializar mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupButtons()
        checkLocationPermission()
    }

    private fun redirecionarLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setupButtons() {
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            sharedPreferences.edit { clear() }
            redirecionarLogin()
        }

        findViewById<Button>(R.id.btnEu).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<Button>(R.id.btnPessoas).setOnClickListener {
            // Forçar atualização manual
            Toast.makeText(this, "A atualizar amigos...", Toast.LENGTH_SHORT).show()
            buscarAmigos()
        }
        findViewById<Button>(R.id.btnGrupos).setOnClickListener {
            Toast.makeText(this, "Grupos (Em breve)", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnCirculos).setOnClickListener {
            Toast.makeText(this, "Círculos (Em breve)", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Permissões e Mapa ---

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
            if (::mMap.isInitialized) enableBlueDot()
        } else {
            Toast.makeText(this, "Permissão de localização necessária.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // Mapa Híbrido (Satélite + Ruas)
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.uiSettings.isZoomControlsEnabled = true

        val portugal = LatLng(39.55, -7.85)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(portugal, 1f))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            enableBlueDot()
        }
    }

    private fun enableBlueDot() {
        if (::mMap.isInitialized &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
        }
    }

    // --- Lógica de GPS ---

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply {
            setMinUpdateIntervalMillis(5000)
            setMinUpdateDistanceMeters(10f)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { currentLocation ->

                    // 1. Zoom Automático na primeira vez que encontra o GPS
                    if (isFirstLocation) {
                        isFirstLocation = false
                        val userPos = LatLng(currentLocation.latitude, currentLocation.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 18f))
                    }

                    // 2. Verificar se devemos enviar atualização
                    var shouldSend = false

                    if (lastSentLocation == null) {
                        shouldSend = true
                    } else {
                        val distance = lastSentLocation!!.distanceTo(currentLocation)
                        if (distance >= MIN_DISTANCE_METERS) {
                            shouldSend = true
                            Log.d("GPS_DEBUG", "Moveu-se ${distance.toInt()}m. A enviar...")
                        }
                    }

                    if (shouldSend) {
                        lastSentLocation = currentLocation
                        enviarLocalizacao(userId, currentLocation.latitude, currentLocation.longitude)

                        // Sempre que envio a minha localização, pergunto onde estão os amigos
                        buscarAmigos()
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    // --- API REQUESTS ---

    private fun enviarLocalizacao(userId: String, latitude: Double, longitude: Double) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/update_location.php"
        val queue = Volley.newRequestQueue(this)

        val postRequest = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                // Log silencioso para não incomodar
                Log.i("API_AZURE", "Loc enviada: $response")
            },
            { error ->
                Log.e("API_AZURE", "Erro envio: ${error.message}")
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "user_id" to userId,
                    "latitude" to latitude.toString(),
                    "longitude" to longitude.toString()
                )
            }
        }
        queue.add(postRequest)
    }

    private fun buscarAmigos() {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    // O JSON vem no formato { "users": [ ... ] }
                    val usersArray = response.getJSONArray("users")

                    for (i in 0 until usersArray.length()) {
                        val userObj = usersArray.getJSONObject(i)

                        val friendId = userObj.getInt("id_user")
                        val name = userObj.getString("name")
                        val lat = userObj.getDouble("latitude")
                        val lon = userObj.getDouble("longitude")

                        val friendPos = LatLng(lat, lon)

                        // Se já temos um marcador para este amigo, atualizamos a posição (animação suave)
                        if (markersMap.containsKey(friendId)) {
                            markersMap[friendId]?.position = friendPos
                        } else {
                            // Se é novo, criamos o marcador
                            val markerOptions = MarkerOptions()
                                .position(friendPos)
                                .title(name)
                                // Ícone Azul Claro para distinguir
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))

                            val marker = mMap.addMarker(markerOptions)
                            if (marker != null) {
                                markersMap[friendId] = marker
                            }
                        }
                    }
                    Log.i("API_FRIENDS", "Amigos atualizados: ${usersArray.length()}")

                } catch (e: Exception) {
                    Log.e("API_FRIENDS", "Erro JSON: ${e.message}")
                }
            },
            { error ->
                Log.e("API_FRIENDS", "Erro Volley: ${error.message}")
            }
        )

        queue.add(request)
    }
}