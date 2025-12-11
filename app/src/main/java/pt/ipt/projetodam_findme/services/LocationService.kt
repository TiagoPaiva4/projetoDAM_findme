package pt.ipt.projetodam_findme.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.*
import pt.ipt.projetodam_findme.R

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var userId: String? = null

    companion object {
        private const val CHANNEL_ID = "location_channel_01"
        private const val NOTIFICATION_ID = 12345
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Receber o ID do utilizador vindo da MainActivity
        userId = intent?.getStringExtra("USER_ID")

        if (userId == null) {
            Log.e("LocationService", "Serviço parado: ID de utilizador não fornecido.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Criar a notificação para o serviço de primeiro plano
        val notification = createNotification()

        // Iniciar como Foreground Service (compatível com Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Iniciar o pedido de atualizações de localização
        startLocationUpdates()

        return START_STICKY // Tenta reiniciar o serviço se o sistema o matar
    }

    private fun startLocationUpdates() {
        // CONFIGURAÇÃO OTIMIZADA PARA POUPAR DADOS E BATERIA
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).apply {
            // Intervalo mínimo entre atualizações (5 segundos)
            setMinUpdateIntervalMillis(5000)

            // IMPORTANTE: Só envia se o utilizador se deslocar mais de 15 metros.
            // Isto evita enviar dados repetidos quando estás parado (drift do GPS).
            setMinUpdateDistanceMeters(15f)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("LocationService", "Movimento detetado: ${location.latitude}, ${location.longitude}")
                    // Enviar para a API apenas quando há movimento real
                    userId?.let { id -> enviarLocalizacao(id, location.latitude, location.longitude) }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationService", "Erro: Permissão de localização perdida.")
        }
    }

    private fun enviarLocalizacao(userId: String, latitude: Double, longitude: Double) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/update_location.php"

        // Usar applicationContext para a queue para evitar leaks de memória do serviço
        val queue = Volley.newRequestQueue(applicationContext)

        val postRequest = object : StringRequest(Request.Method.POST, url,
            { response -> Log.d("API", "Sucesso ao enviar: $response") },
            { error -> Log.e("API", "Erro ao enviar: ${error.message}") }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return mutableMapOf(
                    "user_id" to userId,
                    "latitude" to latitude.toString(),
                    "longitude" to longitude.toString()
                )
            }
        }
        queue.add(postRequest)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FindMe Ativo")
            .setContentText("A atualizar a tua localização em movimento...")
            .setSmallIcon(R.mipmap.ic_launcher) // Garante que tens este ícone ou usa R.drawable.ic_launcher_foreground
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Impede o utilizador de "limpar" a notificação sem querer
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Partilha de Localização",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação ativa enquanto a localização é partilhada"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Parar o rastreio quando o serviço é destruído para poupar bateria
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        Log.d("LocationService", "Serviço de localização parado.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}