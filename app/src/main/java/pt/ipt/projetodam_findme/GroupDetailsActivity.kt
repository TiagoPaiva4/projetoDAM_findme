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
import android.widget.Button
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
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.json.JSONObject

class GroupDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>

    private var groupId: Int = -1
    private var groupName: String = ""
    private var myUserId: String = ""
    private var groupCreatorId: Int = -1 // NOVO: ID do criador do grupo

    private lateinit var txtGroupNameTitle: TextView
    private lateinit var txtGroupMemberCount: TextView
    private lateinit var btnAddMember: ImageView
    private lateinit var btnLeaveGroup: Button // NOVO

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
            // Recarrega a localização e os dados
            fetchMyCurrentLocation()
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
        btnLeaveGroup = findViewById(R.id.btnLeaveGroup) // Inicialização do NOVO botão

        txtGroupNameTitle.text = groupName

        // 3. Configurar Mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapGroup) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // 4. Configurar Bottom Sheet e RecyclerView
        val bottomSheet = findViewById<LinearLayout>(R.id.bottomSheetGroup)

        try {
            sheetBehavior = BottomSheetBehavior.from(bottomSheet)
            sheetBehavior.peekHeight = dpToPx(100)
        } catch (e: IllegalArgumentException) {
            Log.e("GroupDetails", "Erro BottomSheet: ${e.message}")
        }


        recyclerGroupMembers = findViewById(R.id.recyclerGroupMembers)
        recyclerGroupMembers.layoutManager = LinearLayoutManager(this)
        membersAdapter = FriendsAdapter(membersList) { member ->
            // Ação ao clicar no membro
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

        btnLeaveGroup.setOnClickListener {
            leaveGroup() // NOVO listener
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
        mMap.setPadding(0, dpToPx(80), 0, dpToPx(100))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
    }

    private fun fetchMyCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                myLastLocation = location
                val currentLatLng = LatLng(location.latitude, location.longitude)
                if (mMap.cameraPosition.zoom < 10f) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
                buscarLocalizacoesDoGrupo()
            }
        }
    }

    // NOVO: Função para obter a informação do criador
    private fun fetchGroupCreator() {
        // Assume que existe um endpoint ou que o get_group_locations devolve esta info.
        // Como o get_group_locations.php não devolve, vamos criar uma chamada separada se necessário.
        // Por simplificação, vamos assumir que o get_group_locations.php foi alterado para devolver o ID do criador.
        // Se o get_group_locations.php não foi alterado, o id do criador será -1.

        if (groupCreatorId.toString() == myUserId) {
            btnLeaveGroup.text = "ELIMINAR GRUPO"
            btnLeaveGroup.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
        } else {
            btnLeaveGroup.text = "SAIR DESTE GRUPO"
            btnLeaveGroup.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
        }

    }

    private fun buscarLocalizacoesDoGrupo() {
        if (myLastLocation == null) return

        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_group_locations.php?group_id=$groupId&requester_id=$myUserId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                val membersArray = response.getJSONArray("members")

                // Supondo que a API devolve o criador do grupo no JSON,
                // por exemplo, response.optInt("creator_id", -1)
                // Se a sua API não devolve o creator_id, isto terá que ser alterado.
                // Por enquanto, vamos assumir que a API devolve uma estrutura mais simples.

                membersList.clear()
                markersMap.values.forEach { it.remove() }
                markersMap.clear()

                txtGroupMemberCount.text = "${membersArray.length()} membros"

                val boundsBuilder = LatLngBounds.builder()
                var validLocationCount = 0

                // NOVO: Verifica quem é o criador se a API o devolver (A API atual não devolve)
                // Usaremos um ID fictício para testes se a API não for alterada
                // groupCreatorId = 1 // <<--- Altere se souber o ID do criador ou se a API devolver.

                for (i in 0 until membersArray.length()) {
                    val memberObj = membersArray.getJSONObject(i)
                    val memberId = memberObj.getInt("id_user")
                    val name = memberObj.getString("name")

                    val lat = memberObj.optDouble("latitude", 0.0)
                    val lon = memberObj.optDouble("longitude", 0.0)
                    val lastUpd = memberObj.optString("last_update", "Desconhecido")

                    val hasLocation = (lat != 0.0 || lon != 0.0)

                    var distanceMeters = 0f
                    var statusText = "Localização indisponível"

                    if (hasLocation) {
                        val memberPos = LatLng(lat, lon)
                        boundsBuilder.include(memberPos)
                        validLocationCount++

                        val markerOptions = MarkerOptions()
                            .position(memberPos)
                            .title(name)
                            .icon(criarIconeCircular(name))
                            .anchor(0.5f, 0.5f)
                        val marker = mMap.addMarker(markerOptions)
                        if (marker != null) markersMap[memberId] = marker

                        val results = FloatArray(1)
                        Location.distanceBetween(myLastLocation!!.latitude, myLastLocation!!.longitude, lat, lon, results)
                        distanceMeters = results[0]
                        statusText = lastUpd
                    }

                    membersList.add(Friend(memberId, name, lat, lon, distanceMeters, statusText))
                }

                // Se groupCreatorId não foi preenchido pela API, tenta preencher aqui (por exemplo, assumindo que o primeiro membro é o criador, o que é inseguro, mas para testes...)
                if (membersArray.length() > 0) {
                    // Tentativa de obter o criador. Se o seu backend não foi alterado para incluir o criador,
                    // esta lógica pode ser inexata ou precisar de uma chamada extra.
                    // Vamos apenas assumir que, se o utilizador atual é o membro 1, ele é o criador
                    if (myUserId == "1") {
                        groupCreatorId = 1
                    }
                }
                fetchGroupCreator() // Atualiza o texto do botão

                if (validLocationCount > 0 && ::mMap.isInitialized) {
                    if (validLocationCount == 1) {
                        val firstValidMember = membersList.first { it.latitude != 0.0 || it.longitude != 0.0 }
                        val singlePos = LatLng(firstValidMember.latitude, firstValidMember.longitude)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(singlePos, 15f))
                    } else {
                        val bounds = boundsBuilder.build()
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, dpToPx(100)))
                    }
                }

                membersList.sortByDescending { it.distanceMeters > 0f }
                membersAdapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Log.e("GroupDetails", "Erro JSON Members: ${e.message}")
            }
        }, { error ->
            Log.e("GroupDetails", "Erro Volley Members: ${error.message}")
        })

        queue.add(request)
    }

    private fun leaveGroup() {
        val prefs = getSharedPreferences("SessaoUsuario", Context.MODE_PRIVATE)
        val myId = prefs.getInt("id_user", -1).toString()

        // URL do script PHP
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/remove_group_member.php"

        val isCreator = if (myId == groupCreatorId.toString()) "true" else "false"

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        Toast.makeText(this, json.getString("success"), Toast.LENGTH_LONG).show()

                        // Sinaliza que a MainActivity deve atualizar a lista de grupos
                        setResult(RESULT_OK)
                        finish()
                    } else if (json.has("error")) {
                        Toast.makeText(this, json.getString("error"), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao processar resposta.", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Toast.makeText(this, "Erro de conexão.", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "group_id" to groupId.toString(),
                    "user_id" to myId,
                    "is_creator" to isCreator
                )
            }
        }

        queue.add(request)
    }

    // (Mantenha a função criarIconeCircular inalterada)
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