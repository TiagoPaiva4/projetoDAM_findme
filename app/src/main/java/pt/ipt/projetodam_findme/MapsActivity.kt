package pt.ipt.projetodam_findme

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.LatLngBounds
import org.json.JSONArray
import org.json.JSONObject

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    // Mode state
    private var isCreateMode = false
    private var isViewMode = false
    private var viewZone: Zone? = null

    // Create mode state
    private val polygonPoints = mutableListOf<LatLng>()
    private var currentPolygon: Polygon? = null
    private val markers = mutableListOf<Marker>()

    // UI elements
    private lateinit var tvInstruction: TextView
    private lateinit var drawingControls: LinearLayout
    private lateinit var btnUndo: Button
    private lateinit var btnClear: Button
    private lateinit var btnConfirm: Button

    // User info
    private lateinit var userId: String

    // Friends list for target selection
    private val targetsList = mutableListOf<Pair<String, String>>() // id to name

    // Location client for getting current user's location
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Get User ID from session
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        userId = sharedPreferences.getInt("id_user", -1).toString()

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check mode
        val mode = intent.getStringExtra("MODE")
        isCreateMode = mode == "CREATE_ZONE"
        isViewMode = mode == "VIEW_ZONE"
        if (isViewMode) {
            @Suppress("DEPRECATION")
            viewZone = intent.getParcelableExtra("ZONE")
        }

        // Get UI references
        tvInstruction = findViewById(R.id.tvInstruction)
        drawingControls = findViewById(R.id.drawingControls)
        btnUndo = findViewById(R.id.btnUndo)
        btnClear = findViewById(R.id.btnClear)
        btnConfirm = findViewById(R.id.btnConfirm)

        // Setup button listeners
        btnUndo.setOnClickListener { undoLastPoint() }
        btnClear.setOnClickListener { clearPolygon() }
        btnConfirm.setOnClickListener { confirmZoneCreation() }

        // Setup navigation bar for view mode
        val navBarBottom = findViewById<LinearLayout>(R.id.navBarBottom)
        if (isViewMode) {
            navBarBottom.visibility = View.VISIBLE
            setupNavigation()

            // Handle system bar insets
            ViewCompat.setOnApplyWindowInsetsListener(navBarBottom) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val originalPadding = (10 * resources.displayMetrics.density).toInt()
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, originalPadding + bars.bottom)
                insets
            }
        }

        // Initialize the map fragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupNavigation() {
        // Pessoas -> MainActivity
        findViewById<LinearLayout>(R.id.navPessoas).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        // Grupos -> GroupsActivity
        findViewById<LinearLayout>(R.id.navGrupos).setOnClickListener {
            val intent = Intent(this, GroupsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        // Zona -> ZonesActivity
        findViewById<LinearLayout>(R.id.navZona).setOnClickListener {
            val intent = Intent(this, ZonesActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        // Eu -> ProfileActivity
        findViewById<LinearLayout>(R.id.navEu).setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (isCreateMode) {
            // Show drawing UI
            tvInstruction.visibility = View.VISIBLE
            drawingControls.visibility = View.VISIBLE

            // Set up map click listener for polygon drawing
            mMap.setOnMapClickListener { latLng ->
                addPolygonPoint(latLng)
            }

            // Fetch friends for target selection
            fetchFriends()

            // Center on user's location or default to Tomar
            val tomar = LatLng(39.60, -8.41)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tomar, 14f))
        } else if (isViewMode && viewZone != null) {
            // View zone mode - show zone polygon and user location
            showZoneOnMap(viewZone!!)
        } else {
            // Default view mode
            val tomar = LatLng(39.60, -8.41)
            mMap.addMarker(MarkerOptions().position(tomar).title("Ola Tomar!"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tomar, 12f))
        }
    }

    private fun showZoneOnMap(zone: Zone) {
        // Draw the zone polygon
        val points = zone.coordinates.map { LatLng(it.latitude, it.longitude) }
        if (points.size >= 3) {
            mMap.addPolygon(
                PolygonOptions()
                    .addAll(points)
                    .strokeColor(Color.parseColor("#3A8DDE"))
                    .fillColor(Color.parseColor("#403A8DDE"))
                    .strokeWidth(4f)
            )
        }

        // Fetch and show monitored user's location
        fetchMonitoredUserLocation(zone.associatedUserId, points)
    }

    private fun fetchMonitoredUserLocation(monitoredUserId: String, zonePoints: List<LatLng>) {
        // If monitoring self, get device location
        if (monitoredUserId == userId) {
            fetchCurrentDeviceLocation(zonePoints)
            return
        }

        // Otherwise fetch friend's location from API
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                var userLocation: LatLng? = null
                var userName = "Utilizador"

                // Search in friends list
                val usersArray = response.optJSONArray("users")
                if (usersArray != null) {
                    for (i in 0 until usersArray.length()) {
                        val user = usersArray.getJSONObject(i)
                        if (user.getString("id_user") == monitoredUserId) {
                            val lat = user.getDouble("latitude")
                            val lng = user.getDouble("longitude")
                            userLocation = LatLng(lat, lng)
                            userName = user.getString("name")
                            break
                        }
                    }
                }

                // Show user marker and zoom to fit
                zoomToFitZoneAndUser(zonePoints, userLocation, userName)

            } catch (e: Exception) {
                Log.e("MapsActivity", "Error fetching user location: ${e.message}")
                zoomToFitZoneAndUser(zonePoints, null, null)
            }
        }, { error ->
            Log.e("MapsActivity", "Volley error: ${error.message}")
            zoomToFitZoneAndUser(zonePoints, null, null)
        })

        queue.add(request)
    }

    private fun fetchCurrentDeviceLocation(zonePoints: List<LatLng>) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // No permission, just show zone without user marker
            zoomToFitZoneAndUser(zonePoints, null, null)
            return
        }

        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        val userName = sharedPreferences.getString("nome_user", "Eu") ?: "Eu"

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = LatLng(location.latitude, location.longitude)
                zoomToFitZoneAndUser(zonePoints, userLocation, userName)
            } else {
                Log.e("MapsActivity", "Device location is null")
                zoomToFitZoneAndUser(zonePoints, null, null)
            }
        }.addOnFailureListener { e ->
            Log.e("MapsActivity", "Failed to get device location: ${e.message}")
            zoomToFitZoneAndUser(zonePoints, null, null)
        }
    }

    private fun zoomToFitZoneAndUser(zonePoints: List<LatLng>, userLocation: LatLng?, userName: String?) {
        val boundsBuilder = LatLngBounds.Builder()

        // Add all zone points to bounds
        zonePoints.forEach { boundsBuilder.include(it) }

        // Add user marker if available
        if (userLocation != null) {
            mMap.addMarker(
                MarkerOptions()
                    .position(userLocation)
                    .title(userName ?: "Utilizador")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            boundsBuilder.include(userLocation)
        }

        // Zoom to fit all points
        try {
            val bounds = boundsBuilder.build()
            val padding = 100 // pixels
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        } catch (e: Exception) {
            // Fallback if bounds is empty
            if (zonePoints.isNotEmpty()) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(zonePoints[0], 15f))
            }
        }
    }

    private fun addPolygonPoint(latLng: LatLng) {
        polygonPoints.add(latLng)

        // Add marker at the point
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .anchor(0.5f, 0.5f)
        )
        marker?.let { markers.add(it) }

        // Redraw polygon
        redrawPolygon()

        // Update instruction text
        updateInstructionText()
    }

    private fun redrawPolygon() {
        currentPolygon?.remove()

        if (polygonPoints.size >= 3) {
            currentPolygon = mMap.addPolygon(
                PolygonOptions()
                    .addAll(polygonPoints)
                    .strokeColor(Color.parseColor("#3A8DDE"))
                    .fillColor(Color.parseColor("#403A8DDE"))
                    .strokeWidth(4f)
            )
        }
    }

    private fun updateInstructionText() {
        val count = polygonPoints.size
        tvInstruction.text = when {
            count < 3 -> "Toque no mapa para adicionar pontos ($count/3 minimo)"
            else -> "Poligono com $count pontos. Adicione mais ou confirme."
        }
    }

    private fun undoLastPoint() {
        if (polygonPoints.isNotEmpty()) {
            polygonPoints.removeAt(polygonPoints.size - 1)
            markers.lastOrNull()?.remove()
            if (markers.isNotEmpty()) {
                markers.removeAt(markers.size - 1)
            }
            redrawPolygon()
            updateInstructionText()
        }
    }

    private fun clearPolygon() {
        polygonPoints.clear()
        markers.forEach { it.remove() }
        markers.clear()
        currentPolygon?.remove()
        currentPolygon = null
        updateInstructionText()
    }

    private fun confirmZoneCreation() {
        if (polygonPoints.size < 3) {
            Toast.makeText(this, "Minimo 3 pontos necessarios", Toast.LENGTH_SHORT).show()
            return
        }
        showZoneCreationDialog()
    }

    private fun fetchFriends() {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                targetsList.clear()
                // Add self as first option
                val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
                val userName = sharedPreferences.getString("nome_user", "Eu") ?: "Eu"
                targetsList.add(Pair(userId, "Eu ($userName)"))

                val usersArray = response.optJSONArray("users")
                if (usersArray != null) {
                    for (i in 0 until usersArray.length()) {
                        val user = usersArray.getJSONObject(i)
                        val id = user.getString("id_user")
                        val name = user.getString("name")
                        targetsList.add(Pair(id, name))
                    }
                }
            } catch (e: Exception) {
                Log.e("MapsActivity", "Error fetching friends: ${e.message}")
                // Add self anyway
                targetsList.add(Pair(userId, "Eu (minha localizacao)"))
            }
        }, { error ->
            Log.e("MapsActivity", "Volley error: ${error.message}")
            // Add self anyway
            targetsList.add(Pair(userId, "Eu (minha localizacao)"))
        })

        queue.add(request)
    }

    private fun showZoneCreationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_zone, null)
        val etZoneName = dialogView.findViewById<EditText>(R.id.etZoneName)
        val spinnerTarget = dialogView.findViewById<Spinner>(R.id.spinnerTarget)

        // Populate spinner with targets
        val targetNames = targetsList.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, targetNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTarget.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Criar Zona")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val name = etZoneName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Por favor insira um nome para a zona", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedIndex = spinnerTarget.selectedItemPosition
                val targetId = if (selectedIndex >= 0 && selectedIndex < targetsList.size) {
                    targetsList[selectedIndex].first
                } else {
                    userId
                }
                saveZone(name, targetId)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveZone(name: String, targetUserId: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/create_area.php"
        val queue = Volley.newRequestQueue(this)

        // Build coordinates JSON array
        val coordsArray = JSONArray()
        for (point in polygonPoints) {
            val obj = JSONObject()
            obj.put("lat", point.latitude)
            obj.put("lng", point.longitude)
            coordsArray.put(obj)
        }

        val jsonBody = JSONObject()
        jsonBody.put("name", name)
        jsonBody.put("admin_id", userId)
        jsonBody.put("user_id", targetUserId)
        jsonBody.put("area_type", "polygon")
        jsonBody.put("coordinates", coordsArray.toString())

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        Toast.makeText(this, "Zona criada com sucesso!", Toast.LENGTH_SHORT).show()
                        finish() // Return to ZonesActivity
                    } else {
                        val error = json.optString("error", "Erro desconhecido")
                        Toast.makeText(this, "Erro: $error", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MapsActivity", "Error parsing response: ${e.message}")
                    Toast.makeText(this, "Erro ao processar resposta", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                val statusCode = error.networkResponse?.statusCode
                val responseBody = error.networkResponse?.data?.let { String(it) }
                Log.e("MapsActivity", "Error saving zone - Status: $statusCode, Body: $responseBody, Message: ${error.message}")
                Toast.makeText(this, "Erro: $statusCode - ${responseBody ?: error.message ?: "conexao"}", Toast.LENGTH_LONG).show()
            }
        ) {
            override fun getBody(): ByteArray {
                return jsonBody.toString().toByteArray(Charsets.UTF_8)
            }

            override fun getBodyContentType(): String {
                return "application/json; charset=utf-8"
            }
        }

        queue.add(request)
    }
}
