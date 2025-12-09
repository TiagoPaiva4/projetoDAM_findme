package pt.ipt.projetodam_findme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng

class ProfileActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicializar o mapa do Perfil (tem ID mapProfile no XML)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapProfile) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupButtons()
    }

    private fun setupButtons() {
        // Logout
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            getSharedPreferences("SessaoUsuario", MODE_PRIVATE).edit { clear() }
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        // Navegação
        findViewById<Button>(R.id.btnPessoas).setOnClickListener {
            // Voltar para a Main (onde estarão as pessoas)
            startActivity(Intent(this, MainActivity::class.java))
            finish() // Fecha esta activity para não acumular janelas
        }

        // Os outros botões
        findViewById<Button>(R.id.btnGrupos).setOnClickListener { Toast.makeText(this, "Grupos", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnCirculos).setOnClickListener { Toast.makeText(this, "Círculos", Toast.LENGTH_SHORT).show() }

        // Botão EU (recarrega a localização se clicarmos outra vez)
        findViewById<Button>(R.id.btnEu).setOnClickListener {
            enableMyLocation()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID // Satélite
        mMap.uiSettings.isZoomControlsEnabled = true

        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f)) // Zoom bem perto (18f)
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }
}