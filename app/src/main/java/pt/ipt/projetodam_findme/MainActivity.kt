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
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import pt.ipt.projetodam_findme.services.LocationService
import org.json.JSONObject

enum class Tab { PEOPLE, GROUPS, CIRCLES, ME }

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

    // Variáveis para a funcionalidade de Grupos
    private lateinit var groupsAdapter: GroupsAdapter
    private val groupsList = ArrayList<Group>()
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

    // Activity Result Launcher para a Criação de Grupo (Existente)
    private val createGroupResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // O grupo foi criado com sucesso, atualiza a lista
            buscarGrupos()
        }
    }

    // Activity Result Launcher para Detalhes do Grupo
    private val groupDetailsResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // O utilizador saiu ou eliminou o grupo, atualiza a lista se estiver no tab GRUPOS
            if (currentTab == Tab.GROUPS) {
                buscarGrupos()
            }
        }
    }

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

        // Adapter de Amigos
        adapter = FriendsAdapter(
            friendsList = friendsList,
            clickListener = { friend ->
                // CORREÇÃO: Só mexer no mapa se ele estiver pronto
                if (::mMap.isInitialized) {
                    val pos = LatLng(friend.latitude, friend.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
                    sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            },
            currentUserId = userId.toInt()
        )
        recyclerFriends.adapter = adapter

        // Adapter de Grupos
        groupsAdapter = GroupsAdapter(groupsList) { group ->
            val intent = Intent(this, GroupDetailsActivity::class.java).apply {
                putExtra("GROUP_ID", group.id)
                putExtra("GROUP_NAME", group.name)
            }
            groupDetailsResultLauncher.launch(intent)
        }

        // Botão + (Add/Create)
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
            if (currentTab != Tab.GROUPS) {
                currentTab = Tab.GROUPS
                recyclerFriends.adapter = groupsAdapter
            }
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            buscarGrupos()
        }

        tabCirculos.setOnClickListener {
            atualizarEstiloAbas(tabCirculos)
            currentTab = Tab.CIRCLES
            Toast.makeText(this, "Círculos (Em breve)", Toast.LENGTH_SHORT).show()
        }

        tabEu.setOnClickListener {
            atualizarEstiloAbas(tabEu)
            currentTab = Tab.ME
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun handleAddButtonClick() {
        when (currentTab) {
            Tab.PEOPLE -> {
                startActivity(Intent(this, AddFriendActivity::class.java))
            }
            Tab.GROUPS -> {
                val intent = Intent(this, CreateGroupActivity::class.java)
                createGroupResultLauncher.launch(intent)
            }
            else -> {
                Toast.makeText(this, "Função de adicionar não disponível neste separador.", Toast.LENGTH_SHORT).show()
            }
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
            // CORREÇÃO: Só chamar enableBlueDot se o mapa existir
            if (::mMap.isInitialized) enableBlueDot()
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
        // 1. Verificar Permissões
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // 2. Iniciar o Serviço (Responsável por ENVIAR para a BD em segundo plano)
        val intent = Intent(this, LocationService::class.java)
        intent.putExtra("USER_ID", userId)
        ContextCompat.startForegroundService(this, intent)

        // 3. Ativar o Ponto Azul no Mapa
        enableBlueDot()

        // 4. Pedir atualizações LOCAIS para a UI (Para a lista de amigos funcionar)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply {
            setMinUpdateIntervalMillis(2000)
        }.build()

        val uiLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { currentLocation ->
                    // Guardar a localização atual para calcular distâncias
                    lastSentLocation = currentLocation

                    // Se for a primeira localização, focar o mapa no utilizador
                    if (isFirstLocation && ::mMap.isInitialized) {
                        isFirstLocation = false
                        val userPos = LatLng(currentLocation.latitude, currentLocation.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userPos, 15f))
                    }

                    // Atualizar as listas de amigos/grupos com as novas distâncias
                    if (currentTab == Tab.PEOPLE) buscarAmigos()
                    if (currentTab == Tab.GROUPS) buscarGrupos()

                    // NOTA: Não chamamos enviarLocalizacao() aqui porque o LocationService já está a fazer isso!
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, uiLocationCallback, Looper.getMainLooper())
    }

    private fun enviarLocalizacao(userId: String, latitude: Double, longitude: Double) {
        // NOVA VERIFICAÇÃO: Se o utilizador desligou a partilha, não envia nada
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("share_location", true)) {
            return
        }


        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/update_location.php"
        val queue = Volley.newRequestQueue(this)

        val postRequest = object : StringRequest(Request.Method.POST, url,
            { },
            { error -> Log.e("API", "Erro envio: ${error.message}") }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf("user_id" to userId, "latitude" to latitude.toString(), "longitude" to longitude.toString())
            }
        }
        queue.add(postRequest)
    }

    private fun buscarAmigos() {
        if (lastSentLocation == null) return

        // CORREÇÃO: Se o mapa não existe, sai logo para não dar erro
        if (!::mMap.isInitialized) return

        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_users_locations.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                if (currentTab != Tab.PEOPLE) return@JsonObjectRequest

                // CORREÇÃO: Revalidar o mapa antes de tentar adicionar marcadores
                if (!::mMap.isInitialized) return@JsonObjectRequest

                val usersArray = response.getJSONArray("users")
                friendsList.clear()

                // Limpeza segura dos marcadores
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

                    // Adicionar marcador ao mapa
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

    private fun buscarGrupos() {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_my_groups.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                if (currentTab != Tab.GROUPS) return@JsonObjectRequest

                val groupsArray = response.getJSONArray("groups")
                groupsList.clear()

                for (i in 0 until groupsArray.length()) {
                    val groupObj = groupsArray.getJSONObject(i)
                    val id = groupObj.getInt("id_group")
                    val name = groupObj.getString("name_group")
                    val totalMembers = groupObj.getInt("total_members")
                    groupsList.add(Group(id, name, totalMembers))
                }

                groupsAdapter.notifyDataSetChanged()

            } catch (e: Exception) { Log.e("API", "Erro JSON Groups: ${e.message}") }
        }, { error -> Log.e("API", "Erro Volley Groups: ${error.message}") })

        queue.add(request)
    }
}