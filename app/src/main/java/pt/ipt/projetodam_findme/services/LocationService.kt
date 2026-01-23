package pt.ipt.projetodam_findme.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.*
import org.json.JSONObject
import pt.ipt.projetodam_findme.MainActivity
import pt.ipt.projetodam_findme.R
import pt.ipt.projetodam_findme.Zone
import pt.ipt.projetodam_findme.LatLng // Importante: Usar a tua classe LatLng

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var userId: String? = null

    // Lista de zonas para monitorizar (em memória)
    private var myZones: MutableList<Zone> = mutableListOf()

    companion object {
        const val CHANNEL_GEOFENCE_ID = "geofence_channel"
        const val NOTIFICATION_ID = 12345
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    userId?.let { id ->
                        // 1. Enviar para o servidor (para os amigos verem no mapa)
                        sendLocationToBackend(id, location.latitude, location.longitude)

                        // 2. VERIFICAÇÃO LOCAL (A Mágica acontece aqui!)
                        checkGeofences(location.latitude, location.longitude)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getStringExtra("USER_ID")

        // Iniciar como Foreground Service (obrigatório para location background no Android recente)
        val notification = createServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Assim que o serviço arranca, vamos buscar as zonas ao servidor UMA VEZ
        userId?.let { fetchUserZones(it) }

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // --- 1. FUNÇÃO: Sacar as zonas do servidor para memória ---
    private fun fetchUserZones(id: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_user_areas.php?user_id=$id"

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val zonesArray = response.optJSONArray("areas")
                if (zonesArray != null) {
                    myZones.clear()
                    for (i in 0 until zonesArray.length()) {
                        val obj = zonesArray.getJSONObject(i)

                        // Converter o JSON String de coordenadas para List<LatLng>
                        val coordsJson = obj.getString("coordinates")
                        val coordsList = parseCoordinates(coordsJson)

                        // Adicionar à lista
                        myZones.add(Zone(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            adminId = obj.optString("admin_id", ""),
                            associatedUserId = obj.optString("user_id", ""),
                            coordinates = coordsList // Lista convertida
                        ))
                    }
                    Log.d("LocationService", "Zonas carregadas: ${myZones.size}")
                }
            },
            { Log.e("LocationService", "Erro ao carregar zonas: ${it.message}") }
        )
        Volley.newRequestQueue(this).add(request)
    }

    // --- 2. FUNÇÃO: Converter String JSON para List<LatLng> ---
    private fun parseCoordinates(jsonStr: String): List<LatLng> {
        val list = mutableListOf<LatLng>()
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(LatLng(
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lng")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // --- 3. FUNÇÃO: Verificar se estou dentro/fora (Geofencing Local) ---
    private fun checkGeofences(lat: Double, lng: Double) {
        // Cria o ponto atual usando a classe interna do GeofenceManager
        val currentPoint = GeofenceManager.Point(lat, lng)

        // SharedPreferences para não repetir notificações
        val prefs = getSharedPreferences("GeofenceStatus", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        for (zone in myZones) {
            // Usa o algoritmo local para verificar (sem ir à net)
            val isInside = GeofenceManager.isPointInPolygon(currentPoint, zone.coordinates)

            val currentStatus = if (isInside) "inside" else "outside"

            // Verifica o estado anterior
            val lastStatus = prefs.getString("zone_${zone.id}", "unknown")

            // Se o estado mudou (e não é a primeira vez), notifica!
            if (lastStatus != "unknown" && lastStatus != currentStatus) {
                val msg = if (isInside) "Entraste na zona: ${zone.name}" else "Saíste da zona: ${zone.name}"
                sendNotification(msg)
            }

            // Atualiza o estado guardado
            if (lastStatus != currentStatus) {
                editor.putString("zone_${zone.id}", currentStatus)
                editor.apply()
            }
        }
    }

    // --- Auxiliares de Notificação ---

    private fun sendNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, CHANNEL_GEOFENCE_ID)
            .setContentTitle("Alerta de Localização")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createServiceNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_GEOFENCE_ID)
            .setContentTitle("FindMe a correr")
            .setContentText("A monitorizar a tua localização...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_GEOFENCE_ID, "Geofencing Alert", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    // --- Envio para Backend (para tracking visual dos amigos) ---
    private fun sendLocationToBackend(userId: String, lat: Double, lng: Double) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/update_location.php"
        val jsonBody = JSONObject().apply {
            put("user_id", userId)
            put("latitude", lat)
            put("longitude", lng)
        }
        // Não precisamos de resposta aqui, é fire-and-forget
        val request = JsonObjectRequest(Request.Method.POST, url, jsonBody, {}, {})
        Volley.newRequestQueue(this).add(request)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}