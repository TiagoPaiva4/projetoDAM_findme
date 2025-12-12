package pt.ipt.projetodam_findme

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.switchmaterial.SwitchMaterial
import pt.ipt.projetodam_findme.services.LocationService

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchLocation: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Inicializar Views
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnLogout = findViewById<AppCompatButton>(R.id.btnLogout)
        val btnEditProfile = findViewById<LinearLayout>(R.id.btnEditProfile)
        switchLocation = findViewById(R.id.switchLocation)

        // Configurar estado inicial do Switch (baseado se o serviço está a correr ou preferência guardada)
        // Nota: Idealmente guardaria isto em SharedPreferences
        val sharedPref = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val isSharing = sharedPref.getBoolean("is_sharing_location", true)
        switchLocation.isChecked = isSharing

        // Ação de Voltar
        btnBack.setOnClickListener {
            finish()
        }

        // Ação de Logout
        btnLogout.setOnClickListener {
            performLogout()
        }

        // Toggle Localização
        switchLocation.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPref.edit()
            editor.putBoolean("is_sharing_location", isChecked)
            editor.apply()

            if (isChecked) {
                startLocationService()
                Toast.makeText(this, "Partilha de localização ativada", Toast.LENGTH_SHORT).show()
            } else {
                stopLocationService()
                Toast.makeText(this, "Partilha de localização parada", Toast.LENGTH_SHORT).show()
            }
        }

        // Navegar para Perfil (Reutiliza a activity existente)
        btnEditProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        startService(serviceIntent)
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        stopService(serviceIntent)
    }

    private fun performLogout() {
        // 1. Limpar SharedPreferences (Sessão)
        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        // 2. Parar Serviço de Localização
        stopLocationService()

        // 3. Redirecionar para Login e limpar stack de activities
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}