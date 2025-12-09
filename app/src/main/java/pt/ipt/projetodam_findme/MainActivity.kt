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
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequestCode = 1

    // Variáveis de controlo
    private lateinit var userId: String
    private var lastSentLocation: Location? = null // Guarda a última posição enviada para a BD
    private val MIN_DISTANCE_METERS = 30.0f    // Distância mínima (em metros) para atualizar

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
            Toast.makeText(this, "A atualizar mapa...", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Permissão de localização necessária para a app funcionar.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL // Podes mudar para HYBRID se preferires satélite
        mMap.uiSettings.isZoomControlsEnabled = true

        // Move a câmara inicial para Portugal (centro aproximado)
        val portugal = LatLng(39.55, -7.85)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(portugal, 6f))

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

    // --- Lógica de GPS Otimizada ---

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply {
            // Tenta obter localização a cada 5s no mínimo
            setMinUpdateIntervalMillis(5000)
            // Dica ao Android: só avisa se mudar pelo menos 10 metros (nível de hardware)
            setMinUpdateDistanceMeters(10f)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { currentLocation ->

                    var shouldSend = false

                    if (lastSentLocation == null) {
                        // Primeira vez que obtemos localização: Enviar sempre
                        shouldSend = true
                    } else {
                        // Calcular distância em metros
                        val distance = lastSentLocation!!.distanceTo(currentLocation)

                        if (distance >= MIN_DISTANCE_METERS) {
                            shouldSend = true
                            Log.d("GPS_DEBUG", "Moveu-se ${distance.toInt()}m. A enviar...")
                        } else {
                            // Se estiver parado, não faz nada
                            Log.d("GPS_DEBUG", "Parado (deslocamento: ${distance.toInt()}m). Ignorar.")
                        }
                    }

                    if (shouldSend) {
                        lastSentLocation = currentLocation
                        enviarLocalizacao(userId, currentLocation.latitude, currentLocation.longitude)


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

    // --- Envio para o Azure ---

    private fun enviarLocalizacao(userId: String, latitude: Double, longitude: Double) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/update_location.php"
        val queue = Volley.newRequestQueue(this)

        val postRequest = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        Log.i("API_AZURE", "Sucesso: ${json.getString("success")}")
                    } else if (json.has("error")) {
                        Log.e("API_AZURE", "Erro servidor: ${json.getString("error")}")
                    }
                } catch (e: Exception) {
                    Log.e("API_AZURE", "Erro JSON: ${e.message}")
                }
            },
            { error ->
                Log.e("API_AZURE", "Erro Volley: ${error.message}")
                // Opcional: Toast apenas se for crítico. Evita spam de Toasts se a net falhar.
                // Toast.makeText(this, "Falha ao enviar localização", Toast.LENGTH_SHORT).show()
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
}