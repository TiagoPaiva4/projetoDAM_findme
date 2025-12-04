package pt.ipt.projetodam_findme // Confirma se o pacote é este

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Inicializar o fragmento do mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    // Esta função é chamada quando o mapa estiver pronto para ser usado
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Adicionar um marcador em Portugal (Tomar) e mover a câmara
        val tomar = LatLng(39.60, -8.41) // Coordenadas de Tomar
        mMap.addMarker(MarkerOptions().position(tomar).title("Olá Tomar!"))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tomar, 12f))
    }
}