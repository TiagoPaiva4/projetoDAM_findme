package pt.ipt.projetodam_findme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar Sessão
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("logado", false)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Inicializar Mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupButtons()
    }

    private fun setupButtons() {
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            sharedPreferences.edit { clear() }
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnEu).setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnPessoas).setOnClickListener { Toast.makeText(this, "A atualizar mapa...", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnGrupos).setOnClickListener { Toast.makeText(this, "Grupos", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnCirculos).setOnClickListener { Toast.makeText(this, "Círculos", Toast.LENGTH_SHORT).show() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID // Satélite
        mMap.uiSettings.isZoomControlsEnabled = true

        // 1. Centrar em Portugal (Visão Geral)
        val portugal = LatLng(39.55, -7.85) // Centro de Portugal
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(portugal, 7f)) // Zoom 7 vê o país todo

        // 2. Ligar o Ponto Azul (sem mexer na câmara)
        enableBlueDot()
    }

    private fun enableBlueDot() {
        // Verifica se temos permissão
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            // SIM: Mostra o ponto azul
            mMap.isMyLocationEnabled = true
        } else {
            // NÃO: Pede permissão (o utilizador pode já ter dado no outro ecrã, mas convém garantir)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    // Se o utilizador aceitar a permissão neste ecrã
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableBlueDot()
            }
        }
    }
}