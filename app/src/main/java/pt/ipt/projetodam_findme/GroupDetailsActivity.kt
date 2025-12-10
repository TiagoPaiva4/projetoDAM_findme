package pt.ipt.projetodam_findme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.LocationServices
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
import org.json.JSONObject

class GroupDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    private var groupId: Int = -1
    private var groupName: String = ""
    private var myUserId: String = ""

    private lateinit var txtGroupNameTitle: TextView
    private lateinit var txtGroupMemberCount: TextView
    private lateinit var btnAddMember: ImageView

    private lateinit var recyclerGroupMembers: RecyclerView
    private val membersList = ArrayList<Friend>()
    private lateinit var membersAdapter: FriendsAdapter
    private val markersMap = HashMap<Int, Marker>()

    private var myLastLocation: Location? = null

    // Launcher para a Activity de Adicionar Membro
    private val addMemberResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Membro adicionado com sucesso, recarregar a lista
            Toast.makeText(this, "A atualizar lista de membros...", Toast.LENGTH_SHORT).show()
            buscarLocalizacoesDoGrupo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_details)

        // 1. Obter dados do Intent
        groupId = intent.getIntExtra("GROUP_ID", -1)
        groupName = intent.getStringExtra("GROUP_NAME") ?: "Detalhes do Grupo"
        if (groupId == -1) {
            Toast.makeText(this, "Erro: Grupo inválido.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val prefs = getSharedPreferences("SessaoUsuario", Context.MODE_PRIVATE)
        myUserId = prefs.getInt("id_user", -1).toString()

        // 2. Configurar UI
        txtGroupNameTitle = findViewById(R.id.txtGroupNameTitle)
        txtGroupMemberCount = findViewById(R.id.txtGroupMemberCount)
        btnAddMember = findViewById(R.id.btnAddMember)

        txtGroupNameTitle.text = groupName

        // 3. Configurar Mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapGroup) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 4. Configurar Bottom Sheet e RecyclerView
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheetGroup)

        // ******* VERIFICAÇÃO DE CRASH *******
        // Se a aplicação está a falhar aqui, verifique se R.id.bottomSheetGroup existe no seu layout
        // E se o <LinearLayout> é o PRIMEIRO filho do <CoordinatorLayout> (depois do mapa).
        try {
            sheetBehavior = BottomSheetBehavior.from(bottomSheet)
            sheetBehavior.peekHeight = dpToPx(100)
        } catch (e: IllegalArgumentException) {
            Log.e("GroupDetails", "Erro BottomSheet: ${e.message}. Certifique-se que o R.id.bottomSheetGroup é filho direto do CoordinatorLayout.")
            Toast.makeText(this, "Erro ao carregar ecrã do grupo. Verifique o layout.", Toast.LENGTH_LONG).show()
            // Pode não ser o erro principal, mas ajuda a depurar.
        }


        recyclerGroupMembers = findViewById(R.id.recyclerGroupMembers)
        recyclerGroupMembers.layoutManager = LinearLayoutManager(this)
        membersAdapter = FriendsAdapter(membersList) { member ->
            val pos = LatLng(member.latitude, member.longitude)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        recyclerGroupMembers.adapter = membersAdapter

        // 5. Listeners
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        btnAddMember.setOnClickListener {
            val intent = Intent(this, AddMemberActivity::class.java).apply {
                putExtra("GROUP_ID", groupId)
            }
            addMemberResultLauncher.launch(intent)
        }

        // 6. Começar a buscar localizações (com um pequeno atraso para dar tempo ao mapa)
        Handler(Looper.getMainLooper()).postDelayed({
            fetchMyCurrentLocation()
        }, 500)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        mMap.uiSettings.isZoomControlsEnabled = true
        // Ajustar padding para a Bottom Sheet (100dp é a peekHeight)
        mMap.setPadding(0, dpToPx(80), 0, dpToPx(100))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
    }

    // --- Lógica de Localização e API ---

    private fun fetchMyCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissão de localização necessária.", Toast.LENGTH_SHORT).show()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                myLastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
                // Se o mapa não tiver focado ainda, foca
                if (mMap.cameraPosition.zoom < 10f) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
                buscarLocalizacoesDoGrupo()
            } else {
                Toast.makeText(this, "Aguardando localização...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buscarLocalizacoesDoGrupo() {
        if (myLastLocation == null) return

        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_group_locations.php?group_id=$groupId&requester_id=$myUserId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                val membersArray = response.getJSONArray("members")
                membersList.clear()
                markersMap.values.forEach { it.remove() }
                markersMap.clear()

                txtGroupMemberCount.text = "${membersArray.length()} membros"


                for (i in 0 until membersArray.length()) {
                    val memberObj = membersArray.getJSONObject(i)
                    val memberId = memberObj.getInt("id_user")
                    val name = memberObj.getString("name")
                    // Se a latitude ou longitude for nula na BD (pode acontecer se a localização não foi enviada)
                    val lat = memberObj.optDouble("latitude", 0.0)
                    val lon = memberObj.optDouble("longitude", 0.0)
                    val lastUpd = memberObj.optString("last_update", "Desconhecido")

                    if (lat == 0.0 && lon == 0.0) continue // Ignora membros sem localização

                    val memberPos = LatLng(lat, lon)

                    // 1. Adicionar/Atualizar Marcador no mapa
                    val markerOptions = MarkerOptions()
                        .position(memberPos)
                        .title(name)
                        .icon(criarIconeCircular(name))
                        .anchor(0.5f, 0.5f)
                    val marker = mMap.addMarker(markerOptions)
                    if (marker != null) markersMap[memberId] = marker

                    // 2. Calcular Distância e Adicionar à Lista
                    val results = FloatArray(1)
                    Location.distanceBetween(myLastLocation!!.latitude, myLastLocation!!.longitude, lat, lon, results)
                    membersList.add(Friend(memberId, name, lat, lon, results[0], lastUpd))
                }

                membersAdapter.notifyDataSetChanged()

            } catch (e: Exception) { Log.e("API", "Erro JSON Members: ${e.message}") }
        }, { error -> Log.e("API", "Erro Volley Members: ${error.message}") })

        queue.add(request)
    }

    // (A função criarIconeCircular é a mesma de MainActivity.kt)
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
}