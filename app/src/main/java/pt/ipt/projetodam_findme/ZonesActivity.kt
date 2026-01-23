package pt.ipt.projetodam_findme

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject

class ZonesActivity : AppCompatActivity() {

    private lateinit var zonesRecyclerView: RecyclerView
    private lateinit var fabAddZone: FloatingActionButton
    private lateinit var zonesAdapter: ZonesAdapter
    private val zonesList = mutableListOf<Zone>()
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zones)

        // Get User ID from session
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        userId = sharedPreferences.getInt("id_user", -1).toString()

        // Setup RecyclerView
        zonesRecyclerView = findViewById(R.id.zones_recycler_view)
        zonesRecyclerView.layoutManager = LinearLayoutManager(this)
        zonesAdapter = ZonesAdapter(
            zonesList,
            this,
            onZoneClick = { zone ->
                val intent = Intent(this, MapsActivity::class.java)
                intent.putExtra("MODE", "VIEW_ZONE")
                intent.putExtra("ZONE", zone)
                startActivity(intent)
            },
            onZoneLongClick = { zone, view ->
                showZoneOptionsMenu(zone, view)
            }
        )
        zonesRecyclerView.adapter = zonesAdapter

        // Setup FAB
        fabAddZone = findViewById(R.id.fab_add_zone)
        fabAddZone.setOnClickListener {
            val intent = Intent(this, MapsActivity::class.java)
            intent.putExtra("MODE", "CREATE_ZONE")
            startActivity(intent)
        }

        // Setup navigation bar padding for system bars
        val navBarBottom = findViewById<LinearLayout>(R.id.navBarBottom)
        ViewCompat.setOnApplyWindowInsetsListener(navBarBottom) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val originalPadding = (10 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, originalPadding + bars.bottom)
            insets
        }

        // Setup navigation
        setupNavigation()

        // Fetch zones
        fetchZones()
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

        // Zona -> Scroll to top (current screen)
        findViewById<LinearLayout>(R.id.navZona).setOnClickListener {
            zonesRecyclerView.smoothScrollToPosition(0)
        }

        // Eu -> ProfileActivity
        findViewById<LinearLayout>(R.id.navEu).setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        fetchZones()
    }

    private fun fetchZones() {
        if (userId == "-1") return

        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_user_areas.php?user_id=$userId"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            try {
                zonesList.clear()
                val areasArray = response.optJSONArray("areas")

                if (areasArray != null) {
                    for (i in 0 until areasArray.length()) {
                        val areaObj = areasArray.getJSONObject(i)
                        val id = areaObj.getString("id")
                        val name = areaObj.getString("name")
                        val adminId = areaObj.getString("admin_id")
                        val areaUserId = areaObj.getString("user_id")
                        val coordsJson = areaObj.getString("coordinates")
                        val coordinates = parseCoordinates(coordsJson)
                        val isActive = areaObj.optInt("is_active", 1) == 1

                        zonesList.add(Zone(id, name, adminId, areaUserId, coordinates, isActive))
                    }
                }
                // Sort: active zones first, inactive at the bottom
                zonesList.sortBy { !it.isActive }
                zonesAdapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Log.e("ZonesActivity", "Error parsing JSON: ${e.message}")
            }
        }, { error ->
            Log.e("ZonesActivity", "Volley error: ${error.message}")
        })

        queue.add(request)
    }

    private fun parseCoordinates(json: String): List<LatLng> {
        val list = mutableListOf<LatLng>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val point = jsonArray.getJSONObject(i)
                list.add(LatLng(point.getDouble("lat"), point.getDouble("lng")))
            }
        } catch (e: Exception) {
            Log.e("ZonesActivity", "Error parsing coordinates: ${e.message}")
        }
        return list
    }

    private fun showZoneOptionsMenu(zone: Zone, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_zone_options, popup.menu)

        // Update toggle text based on current state
        val toggleItem = popup.menu.findItem(R.id.action_toggle)
        toggleItem.title = if (zone.isActive) "Desativar" else "Ativar"

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    openEditZone(zone)
                    true
                }
                R.id.action_delete -> {
                    confirmDeleteZone(zone)
                    true
                }
                R.id.action_toggle -> {
                    toggleZoneStatus(zone)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun openEditZone(zone: Zone) {
        val intent = Intent(this, MapsActivity::class.java)
        intent.putExtra("MODE", "EDIT_ZONE")
        intent.putExtra("ZONE", zone)
        startActivity(intent)
    }

    private fun confirmDeleteZone(zone: Zone) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Zona")
            .setMessage("Tem a certeza que pretende eliminar a zona \"${zone.name}\"?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteZone(zone)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteZone(zone: Zone) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/delete_area.php"
        val queue = Volley.newRequestQueue(this)

        val jsonBody = JSONObject()
        jsonBody.put("id", zone.id)

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        Toast.makeText(this, "Zona eliminada", Toast.LENGTH_SHORT).show()
                        fetchZones()
                    } else {
                        val error = json.optString("error", "Erro desconhecido")
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ZonesActivity", "Error parsing response: ${e.message}")
                    Toast.makeText(this, "Erro ao processar resposta", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("ZonesActivity", "Volley error: ${error.message}")
                Toast.makeText(this, "Erro de conexao", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getBody() = jsonBody.toString().toByteArray(Charsets.UTF_8)
            override fun getBodyContentType() = "application/json; charset=utf-8"
        }

        queue.add(request)
    }

    private fun toggleZoneStatus(zone: Zone) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/toggle_area_status.php"
        val queue = Volley.newRequestQueue(this)

        val jsonBody = JSONObject()
        jsonBody.put("id", zone.id)
        jsonBody.put("is_active", !zone.isActive)

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.has("success")) {
                        val statusText = if (!zone.isActive) "ativada" else "desativada"
                        Toast.makeText(this, "Zona $statusText", Toast.LENGTH_SHORT).show()
                        fetchZones()
                    } else {
                        val error = json.optString("error", "Erro desconhecido")
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ZonesActivity", "Error parsing response: ${e.message}")
                    Toast.makeText(this, "Erro ao processar resposta", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("ZonesActivity", "Volley error: ${error.message}")
                Toast.makeText(this, "Erro de conexao", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getBody() = jsonBody.toString().toByteArray(Charsets.UTF_8)
            override fun getBodyContentType() = "application/json; charset=utf-8"
        }

        queue.add(request)
    }
}
