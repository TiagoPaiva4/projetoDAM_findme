/**
 * MainActivity.kt
 *
 * Ecrã principal da aplicação FindMe.
 * Apresenta o mapa com a localização do utilizador e dos seus amigos.
 *
 * Funcionalidades:
 * - Mapa Google Maps com marcadores personalizados para cada amigo
 * - Bottom sheet com lista de amigos e distâncias
 * - Navegação para outras secções (Grupos, Zonas, Perfil)
 * - Gestão de permissões de localização
 * - Atualização em tempo real das posições
 */
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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

    private lateinit var recyclerFriends: RecyclerView
    private val friendsList = ArrayList<Friend>()
    private lateinit var adapter: FriendsAdapter
    private val markersMap = HashMap<Int, Marker>()

    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    private var currentTab = Tab.PEOPLE

    // UI Elements - ABAS
    private lateinit var tabPessoas: LinearLayout
    private lateinit var tabGrupos: LinearLayout
    private lateinit var tabCirculos: LinearLayout
    private lateinit var tabEu: LinearLayout

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

        // verificação de permissão
        checkLocationPermission()
    }

    private fun redirecionarLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun setupUI() {
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

        val btnToggle = findViewById<ImageView>(R.id.btnToggleSheet)

        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset >= 0) {
                    btnToggle.rotation = slideOffset * 180
                }
            }
        })

        btnToggle.setOnClickListener {
            if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }


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
            currentUserId = userId.toInt(),
            addFriendListener = { handleAddButtonClick() }
        )
        recyclerFriends.adapter = adapter

        // Inicializar NavItems
        tabPessoas = findViewById(R.id.navPessoas)
        tabGrupos = findViewById(R.id.navGrupos)
        tabCirculos = findViewById(R.id.navZona)
        tabEu = findViewById(R.id.navEu)

        // Estado Inicial
        atualizarEstiloAbas(tabPessoas)
        currentTab = Tab.PEOPLE

        // Listeners das Abas
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
            val intent = Intent(this, GroupsActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        tabCirculos.setOnClickListener {
            atualizarEstiloAbas(tabCirculos)
            val intent = Intent(this, ZonesActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        tabEu.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            @Suppress("DEPRECATION")
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

    /**
     * Cria um marcador personalizado com as iniciais do nome do utilizador.
     *
     * Processo:
     * 1. Extrai as iniciais do nome (ex: "João Silva" -> "JS")
     * 2. Se só tiver um nome, usa as primeiras 2 letras (ex: "Maria" -> "MA")
     * 3. Infla o layout layout_custom_marker.xml com as iniciais
     * 4. Converte a View para Bitmap para usar como ícone do marcador
     *
     * @param nome Nome completo do utilizador
     * @return BitmapDescriptor para usar no marcador do Google Maps
     */
    private fun getCustomMarkerBitmap(nome: String): BitmapDescriptor {
        val view = LayoutInflater.from(this).inflate(R.layout.layout_custom_marker, null)
        val textView = view.findViewById<TextView>(R.id.marker_text)

        // Divide o nome em partes (palavras)
        val nomeLimpo = nome.trim()
        val partes = nomeLimpo.split("\\s+".toRegex())

        // Gera as iniciais (2 caracteres)
        val sigla = if (partes.size >= 2) {
            // Se tem 2+ nomes, usa primeira letra de cada
            val letra1 = partes[0].firstOrNull()?.toString() ?: ""
            val letra2 = partes[1].firstOrNull()?.toString() ?: ""
            (letra1 + letra2).uppercase()
        } else {
            // Se só tem 1 nome, usa as primeiras 2 letras
            if (nomeLimpo.length >= 2) {
                nomeLimpo.substring(0, 2).uppercase()
            } else {
                nomeLimpo.uppercase()
            }
        }

        textView.text = sigla

        // Mede e desenha a View para converter em Bitmap
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(spec, spec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // --- LÓGICA DE PERMISSÕES ATUALIZADA ---

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
            // Se já foi negado anteriormente, podemos mostrar o aviso antes de pedir
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), permissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {

            // Verifica se a permissão de localização foi concedida
            val locationPermissionIndex = permissions.indexOf(Manifest.permission.ACCESS_FINE_LOCATION)

            if (locationPermissionIndex != -1 && grantResults[locationPermissionIndex] == PackageManager.PERMISSION_GRANTED) {
                // Sucesso
                startLocationUpdates()
                if (::mMap.isInitialized) enableBlueDot()
            } else {
                // NEGADO: Mostrar Alerta
                showPermissionDeniedDialog()
            }
        }
    }

    // NOVA FUNÇÃO: Mostra o alerta se o user disser "Não"
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissão de GPS Necessária")
            .setMessage("O FindMe precisa da sua localização para funcionar e mostrar os seus amigos no mapa.\n\nPor favor, ative a permissão nas definições.")
            .setCancelable(false) // Impede fechar tocando fora
            .setPositiveButton("Ir às Definições") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Sair") { dialog, _ ->
                dialog.dismiss()
                finish() // Fecha a app porque sem GPS não funciona
            }
            .show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        mMap.uiSettings.apply {
            isZoomControlsEnabled = false
            isMapToolbarEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isRotateGesturesEnabled = true
            isZoomGesturesEnabled = true
        }

        mMap.setPadding(0, dpToPx(50), 0, dpToPx(90))

        val centroPortugal = LatLng(39.60, -8.00)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centroPortugal, 6.2f))

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
                        .icon(getCustomMarkerBitmap(name))
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