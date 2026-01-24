/**
 * MapsActivity.kt
 *
 * Ecrã de mapa para criação, visualização e edição de zonas.
 * Suporta três modos: CREATE_ZONE, VIEW_ZONE e EDIT_ZONE.
 * Utiliza algoritmo Ray Casting para deteção de ponto dentro do polígono.
 */
package pt.ipt.projetodam_findme

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
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

    // Estado do modo
    private var isCreateMode = false
    private var isViewMode = false
    private var isEditMode = false
    private var viewZone: Zone? = null
    private var editZone: Zone? = null

    // Estado do modo de criação
    private val polygonPoints = mutableListOf<LatLng>()
    private var currentPolygon: Polygon? = null
    private val markers = mutableListOf<Marker>()

    // Elementos da UI
    private lateinit var tvInstruction: TextView
    private lateinit var drawingControls: LinearLayout
    private lateinit var btnUndo: Button
    private lateinit var btnClear: Button
    private lateinit var btnConfirm: Button

    // Informação do utilizador
    private lateinit var userId: String

    // Lista de amigos para seleção de alvo
    private val targetsList = mutableListOf<Pair<String, String>>()

    // Cliente de localização
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Referência do polígono da zona para atualização de cor
    private var zonePolygon: Polygon? = null

    // Rastreamento de localização em tempo real
    private var locationCallback: LocationCallback? = null
    private var userMarker: Marker? = null
    private var zonePoints: List<LatLng> = emptyList()
    private var monitoredUserName: String = "Utilizador"
    private val handler = Handler(Looper.getMainLooper())
    private var apiPollingRunnable: Runnable? = null
    private val LOCATION_UPDATE_INTERVAL = 5000L // 5 segundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obter ID do utilizador da sessão
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        userId = sharedPreferences.getInt("id_user", -1).toString()

        // Inicializar cliente de localização
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Verificar modo
        val mode = intent.getStringExtra("MODE")
        isCreateMode = mode == "CREATE_ZONE"
        isViewMode = mode == "VIEW_ZONE"
        isEditMode = mode == "EDIT_ZONE"
        if (isViewMode || isEditMode) {
            @Suppress("DEPRECATION")
            val zone = intent.getParcelableExtra<Zone>("ZONE")
            if (isViewMode) viewZone = zone
            if (isEditMode) editZone = zone
        }

        // Obter referências da UI
        tvInstruction = findViewById(R.id.tvInstruction)
        drawingControls = findViewById(R.id.drawingControls)
        btnUndo = findViewById(R.id.btnUndo)
        btnClear = findViewById(R.id.btnClear)
        btnConfirm = findViewById(R.id.btnConfirm)

        // Configurar listeners dos botões
        btnUndo.setOnClickListener { undoLastPoint() }
        btnClear.setOnClickListener { clearPolygon() }
        btnConfirm.setOnClickListener { confirmZoneCreation() }

        // Configurar barra de navegação para modo de visualização
        val navBarBottom = findViewById<LinearLayout>(R.id.navBarBottom)
        if (isViewMode) {
            navBarBottom.visibility = View.VISIBLE
            setupNavigation()

            // Ajustar padding para barras do sistema
            ViewCompat.setOnApplyWindowInsetsListener(navBarBottom) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val originalPadding = (10 * resources.displayMetrics.density).toInt()
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, originalPadding + bars.bottom)
                insets
            }
        }

        // Ajustar padding dos controlos de desenho (modo criar/editar)
        ViewCompat.setOnApplyWindowInsetsListener(drawingControls) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPadding = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(originalPadding, originalPadding, originalPadding, originalPadding + bars.bottom)
            insets
        }

        // Inicia o fragmento do mapa
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
        mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE

        if (isCreateMode || isEditMode) {
            // Mostrar a interface de desenho
            tvInstruction.visibility = View.VISIBLE
            drawingControls.visibility = View.VISIBLE

            // Configurar listener de clique no mapa para desenhar polígono
            mMap.setOnMapClickListener { latLng ->
                addPolygonPoint(latLng)
            }

            // Obter amigos para seleção de alvo
            fetchFriends()

            if (isEditMode && editZone != null) {
                // Carregar polígono existente para edição
                loadExistingPolygon(editZone!!)
            } else {
                // Centrar na localização do utilizador ou, por defeito, em Tomar
                val tomar = LatLng(39.60, -8.41)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tomar, 14f))
            }
        } else if (isViewMode && viewZone != null) {
            // Modo de visualização da zona – mostrar o polígono da zona e a localização do utilizador
            showZoneOnMap(viewZone!!)
        } else {
            // Modo de visualização por defeito
            val tomar = LatLng(39.60, -8.41)
            mMap.addMarker(MarkerOptions().position(tomar).title("Ola Tomar!"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tomar, 12f))
        }
    }

    private fun loadExistingPolygon(zone: Zone) {
        // Adicionar todos os pontos existentes ao polígono
        zone.coordinates.forEach { coord ->
            val latLng = LatLng(coord.latitude, coord.longitude)
            addPolygonPoint(latLng)
        }
        tvInstruction.text = "Modifique o poligono ou confirme"

        // Ajustar o zoom para enquadrar o polígono
        if (zone.coordinates.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            zone.coordinates.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
            try {
                val bounds = boundsBuilder.build()
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            } catch (e: Exception) {
                val first = zone.coordinates[0]
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 15f))
            }
        }
    }

    private fun showZoneOnMap(zone: Zone) {
        // Armazenar os pontos da zona para atualizações em tempo real
        zonePoints = zone.coordinates.map { LatLng(it.latitude, it.longitude) }

        // Desenhar o polígono da zona
        // Cor cinza se a zona estiver desativada, azul caso contrário
        val strokeColor = if (zone.isActive) Color.parseColor("#3A8DDE") else Color.parseColor("#808080")
        val fillColor = if (zone.isActive) Color.parseColor("#403A8DDE") else Color.parseColor("#40808080")

        if (zonePoints.size >= 3) {
            zonePolygon = mMap.addPolygon(
                PolygonOptions()
                    .addAll(zonePoints)
                    .strokeColor(strokeColor)
                    .fillColor(fillColor)
                    .strokeWidth(4f)
            )
        }

        // Rastrear localização apenas se a zona estiver ativa
        if (zone.isActive) {
            startRealTimeLocationTracking(zone.associatedUserId)
        } else {
            // aplicar zoom para a zona sem rastreio
            zoomToZone()
        }
    }

    /**
     * Atualiza a cor do polígono da zona consoante se o utilizador está dentro ou fora
     * Verde = utilizador está dentro da zona
     * Vermelho = utilizador está fora da zona
     */
    private fun updateZonePolygonColor(isUserInside: Boolean) {
        zonePolygon?.let { polygon ->
            if (isUserInside) {
                // Verde quando dentro dentro da zona
                polygon.strokeColor = Color.parseColor("#4CAF50")
                polygon.fillColor = Color.parseColor("#404CAF50")
            } else {
                // Vermelho quando fora da zona
                polygon.strokeColor = Color.parseColor("#F44336")
                polygon.fillColor = Color.parseColor("#40F44336")
            }
        }
    }

    /**
     * Algoritmo de ray casting para determinar se um ponto está dentro de um polígono
     */
    private fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude

            val intersect = ((yi > point.latitude) != (yj > point.latitude)) &&
                    (point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi)

            if (intersect) inside = !inside
            j = i
        }

        return inside
    }

    /**
     * Inicia o rastreio de localização em tempo real do utilizador monitorizado
     */
    private fun startRealTimeLocationTracking(monitoredUserId: String) {
        // Obter nome de utilizador a partir da sessão se estiver a monitorizar-se a si próprio
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        monitoredUserName = if (monitoredUserId == userId) {
            sharedPreferences.getString("nome_user", "Eu") ?: "Eu"
        } else {
            "Utilizador"
        }

        if (monitoredUserId == userId) {
            // Auto-monitorização: Usar atualizações de localização GPS
            startSelfLocationUpdates()
        } else {
            // Monitorização de amigos: Consultar a API periodicamente
            startFriendLocationPolling(monitoredUserId)
        }

        // Zoom inicial para a zona
        zoomToZone()
    }

    /**
     * Inicia as atualizações de localização GPS para auto-monitorização
     */
    private fun startSelfLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL).apply {
            setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL / 2)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val userLocation = LatLng(location.latitude, location.longitude)
                    updateUserLocationOnMap(userLocation)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
    }

    /**
     * Inicia a consulta periódica à API para a localização do amigo
     */
    private fun startFriendLocationPolling(monitoredUserId: String) {
        apiPollingRunnable = object : Runnable {
            override fun run() {
                fetchFriendLocation(monitoredUserId)
                handler.postDelayed(this, LOCATION_UPDATE_INTERVAL)
            }
        }

        handler.post(apiPollingRunnable!!)
    }

    /**
     * Obtém a localização do amigo através da API
     */
    private fun fetchFriendLocation(monitoredUserId: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                val usersArray = response.optJSONArray("users")
                if (usersArray != null) {
                    for (i in 0 until usersArray.length()) {
                        val user = usersArray.getJSONObject(i)
                        if (user.getString("id_user") == monitoredUserId) {
                            val lat = user.getDouble("latitude")
                            val lng = user.getDouble("longitude")
                            monitoredUserName = user.getString("name")
                            updateUserLocationOnMap(LatLng(lat, lng))
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MapsActivity", "Error fetching friend location: ${e.message}")
            }
        }, { error ->
            Log.e("MapsActivity", "Volley error: ${error.message}")
        })

        queue.add(request)
    }

    /**
     * Atualiza a posição do marcador do utilizador e a cor do polígono
     */
    private fun updateUserLocationOnMap(userLocation: LatLng) {
        // Atualizar ou criar marcador
        if (userMarker == null) {
            userMarker = mMap.addMarker(
                MarkerOptions()
                    .position(userLocation)
                    .title(monitoredUserName)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            userMarker?.position = userLocation
        }

        // Verificar se o utilizador está dentro da zona e atualiza a cor do polígono
        val isInside = isPointInPolygon(userLocation, zonePoints)
        updateZonePolygonColor(isInside)
    }

    /**
     * Ajusta o zoom da câmara para enquadrar a zona
     */
    private fun zoomToZone() {
        if (zonePoints.isEmpty()) return

        try {
            val boundsBuilder = LatLngBounds.Builder()
            zonePoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        } catch (e: Exception) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(zonePoints[0], 15f))
        }
    }

    /**
     * Interrompe o rastreio de localização em tempo real
     */
    private fun stopRealTimeLocationTracking() {
        // Parar as atualizações do GPS
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }

        // Parar a consulta periódica à API
        apiPollingRunnable?.let {
            handler.removeCallbacks(it)
            apiPollingRunnable = null
        }
    }

    override fun onPause() {
        super.onPause()
        if (isViewMode) {
            stopRealTimeLocationTracking()
        }
    }

    override fun onResume() {
        super.onResume()
        // Retomar o rastreio se estivermos no modo de visualização e o mapa estiver pronto
        if (isViewMode && viewZone != null && ::mMap.isInitialized) {
            startRealTimeLocationTracking(viewZone!!.associatedUserId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRealTimeLocationTracking()
    }

    /**
     * Adiciona um novo ponto ao polígono da zona.
     * Chamado quando o utilizador toca no mapa durante modo CREATE ou EDIT.
     *
     * @param latLng Coordenadas do ponto tocado
     */
    private fun addPolygonPoint(latLng: LatLng) {
        polygonPoints.add(latLng)

        // Cria marcador circular numerado no ponto
        val pointNumber = polygonPoints.size
        val markerIcon = createNumberedCircleMarker(pointNumber)
        val marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromBitmap(markerIcon))
                .anchor(0.5f, 0.5f)
        )
        marker?.let { markers.add(it) }

        // Redesenha o polígono com o novo ponto
        redrawPolygon()

        // Atualiza texto de instrução
        updateInstructionText()
    }

    /**
     * Cria um marcador circular azul com um número dentro.
     * Usado para numerar os vértices do polígono durante a criação de zonas.
     *
     * @param number Número a mostrar dentro do círculo
     * @return Bitmap do marcador circular
     */
    private fun createNumberedCircleMarker(number: Int): Bitmap {
        val size = (40 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Desenhar círculo azul
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A8DDE")
            style = Paint.Style.FILL
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius - 2, circlePaint)

        // Desenhar contorno branco
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(radius, radius, radius - 2, borderPaint)

        // Desenhar número
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.45f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(number.toString(), radius, textY, textPaint)

        return bitmap
    }

    /**
     * Redesenha o polígono no mapa com os pontos atuais.
     * Um polígono válido precisa de pelo menos 3 pontos.
     * Reutiliza o polígono existente se possível (melhor performance).
     */
    private fun redrawPolygon() {
        if (polygonPoints.size >= 3) {
            if (currentPolygon != null) {
                // Reutiliza polígono existente - só atualiza os pontos
                currentPolygon!!.points = polygonPoints
            } else {
                // Cria novo polígono se não existir
                currentPolygon = mMap.addPolygon(
                    PolygonOptions()
                        .addAll(polygonPoints)
                        .strokeColor(Color.parseColor("#3A8DDE"))  // Azul
                        .fillColor(Color.parseColor("#403A8DDE")) // Azul translúcido
                        .strokeWidth(4f)
                )
            }
        } else {
            // Menos de 3 pontos - remove polígono se existir
            currentPolygon?.remove()
            currentPolygon = null
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

        // Limpar o polígono definindo pontos vazios e, em seguida, remover
        currentPolygon?.let { polygon ->
            polygon.points = emptyList()
            polygon.remove()
        }
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

                targetsList.add(Pair(userId, "Eu (minha localizacao)"))
            }
        }, { error ->
            Log.e("MapsActivity", "Volley error: ${error.message}")

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

        // Pre-fill values for edit mode
        val dialogTitle: String
        val buttonText: String
        if (isEditMode && editZone != null) {
            dialogTitle = "Editar Zona"
            buttonText = "Atualizar"
            etZoneName.setText(editZone!!.name)
            // Find and select the current target user
            val currentTargetIndex = targetsList.indexOfFirst { it.first == editZone!!.associatedUserId }
            if (currentTargetIndex >= 0) {
                spinnerTarget.setSelection(currentTargetIndex)
            }
        } else {
            dialogTitle = "Criar Zona"
            buttonText = "Guardar"
        }

        AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(buttonText) { _, _ ->
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
                if (isEditMode && editZone != null) {
                    updateZone(editZone!!.id, name, targetId)
                } else {
                    saveZone(name, targetId)
                }
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

    private fun updateZone(zoneId: String, name: String, targetUserId: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/update_area.php"
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
        jsonBody.put("id", zoneId)
        jsonBody.put("name", name)
        jsonBody.put("user_id", targetUserId)
        jsonBody.put("coordinates", coordsArray.toString())

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        Toast.makeText(this, "Zona atualizada com sucesso!", Toast.LENGTH_SHORT).show()
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
                Log.e("MapsActivity", "Error updating zone - Status: $statusCode, Body: $responseBody, Message: ${error.message}")
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

    // Função para converter o layout XML em Bitmap
    private fun getCustomMarkerBitmap(text: String): android.graphics.Bitmap {
        val view = android.view.LayoutInflater.from(this).inflate(R.layout.layout_custom_marker, null)
        val textView = view.findViewById<android.widget.TextView>(R.id.marker_text)

        // Define a letra (ex: primeira letra do nome)
        textView.text = text

        // Obriga a view a desenhar-se para sabermos o tamanho
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        // Cria o bitmap
        val bitmap = android.graphics.Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)

        return bitmap
    }
}
