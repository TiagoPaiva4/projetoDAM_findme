package pt.ipt.projetodam_findme

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray

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
        zonesAdapter = ZonesAdapter(zonesList, this) { zone ->
            val intent = Intent(this, MapsActivity::class.java)
            intent.putExtra("MODE", "VIEW_ZONE")
            intent.putExtra("ZONE", zone)
            startActivity(intent)
        }
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

                        zonesList.add(Zone(id, name, adminId, areaUserId, coordinates))
                    }
                }
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
}
