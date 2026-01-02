package pt.ipt.projetodam_findme

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ZonesActivity : AppCompatActivity() {

    private lateinit var zonesRecyclerView: RecyclerView
    private lateinit var fabAddZone: FloatingActionButton
    // private lateinit var zonesAdapter: ZonesAdapter // Will be created later
    // private val zonesList = mutableListOf<Zone>() // Zone data class will be created later

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zones)

        zonesRecyclerView = findViewById(R.id.zones_recycler_view)
        fabAddZone = findViewById(R.id.fab_add_zone)

        zonesRecyclerView.layoutManager = LinearLayoutManager(this)
        // zonesAdapter = ZonesAdapter(zonesList)
        // zonesRecyclerView.adapter = zonesAdapter

        fabAddZone.setOnClickListener {
            // TODO: Launch MapsActivity for drawing a new zone
        }

        // TODO: Fetch existing zones from the backend and update the RecyclerView
    }
}
