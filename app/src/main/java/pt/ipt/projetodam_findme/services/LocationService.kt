/**
 * LocationService.kt
 *
 * Serviço em primeiro plano para rastreamento contínuo de localização.
 * Envia atualizações de posição para o backend e verifica geofences.
 *
 * Funcionalidades:
 * - Atualização de localização a cada 5-10 segundos
 * - Verificação de entrada/saída em zonas (geofencing)
 * - Notificações de alerta quando o utilizador entra/sai de zonas
 */
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
import pt.ipt.projetodam_findme.LatLng

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var userId: String? = null

    // Lista de zonas para monitorizar
    private var myZones: MutableList<Zone> = mutableListOf()

    companion object {
        const val CHANNEL_GEOFENCE_ID = "geofence_channel"
        const val NOTIFICATION_ID = 12345
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        /**
         * Callback chamado sempre que há uma nova localização GPS.
         * Executado a cada 5-10 segundos conforme configurado em startLocationUpdates().
         */
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    userId?.let { id ->
                        // 1. Enviar localização para o backend para partilha com amigos
                        sendLocationToBackend(id, location.latitude, location.longitude)

                        // 2. Verificar se entrou/saiu de alguma zona (geofencing)
                        checkGeofences(location.latitude, location.longitude)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getStringExtra("USER_ID")

        // Notificação persistente do serviço (Obrigatório Android mais recentes)
        val notification = createServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Carregar zonas assim que o serviço inicia
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

    // --- 1. LER ZONAS E O SEU ESTADO (ATIVO/INATIVO) ---
    private fun fetchUserZones(id: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_user_areas.php?user_id=$id"

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val zonesArray = response.optJSONArray("areas")
                if (zonesArray != null) {
                    myZones.clear()
                    for (i in 0 until zonesArray.length()) {
                        val obj = zonesArray.getJSONObject(i)

                        val coordsJson = obj.getString("coordinates")
                        val coordsList = parseCoordinates(coordsJson)

                        // [CORREÇÃO] Ler o campo 'is_active'.
                        // O PHP costuma mandar 0 ou 1. Convertemos para Boolean.
                        // Se o campo não existir, assumimos 'true' por segurança.
                        val isActiveInt = obj.optInt("is_active", 1)
                        val isActive = (isActiveInt == 1)

                        myZones.add(Zone(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            adminId = obj.optString("admin_id", ""),
                            associatedUserId = obj.optString("user_id", ""),
                            coordinates = coordsList,
                            isActive = isActive // Passamos o valor lido para a classe Zone
                        ))
                    }
                    Log.d("LocationService", "Zonas carregadas: ${myZones.size}")
                }
            },
            { Log.e("LocationService", "Erro ao carregar zonas: ${it.message}") }
        )
        Volley.newRequestQueue(this).add(request)
    }

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

    /**
     * Verifica se o utilizador entrou ou saiu de alguma zona monitorizada.
     *
     * Lógica:
     * 1. Para cada zona ativa, verifica se o ponto atual está dentro do polígono
     * 2. Compara com o estado anterior guardado em SharedPreferences
     * 3. Se houve mudança de estado (entrou/saiu), envia notificação
     * 4. Guarda o novo estado para comparações futuras
     *
     * @param lat Latitude atual do utilizador
     * @param lng Longitude atual do utilizador
     */
    private fun checkGeofences(lat: Double, lng: Double) {
        val currentPoint = GeofenceManager.Point(lat, lng)
        // SharedPreferences para guardar o último estado conhecido de cada zona
        val prefs = getSharedPreferences("GeofenceStatus", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        for (zone in myZones) {
            // Ignora zonas desativadas
            if (!zone.isActive) {
                continue
            }

            // Usa o algoritmo Ray Casting para verificar se está dentro
            val isInside = GeofenceManager.isPointInPolygon(currentPoint, zone.coordinates)
            val currentStatus = if (isInside) "inside" else "outside"

            // Obtém o estado anterior (unknown se for a primeira verificação)
            val lastStatus = prefs.getString("zone_${zone.id}", "unknown")

            // Só notifica se houve MUDANÇA de estado (e não é a primeira vez)
            if (lastStatus != "unknown" && lastStatus != currentStatus) {
                val msg = if (isInside) "Entraste na zona: ${zone.name}" else "Saíste da zona: ${zone.name}"
                sendNotification(msg)
            }

            // Guarda o estado atual para a próxima comparação
            if (lastStatus != currentStatus) {
                editor.putString("zone_${zone.id}", currentStatus)
                editor.apply()
            }
        }
    }

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
            .setContentTitle("FindMe Ativo")
            .setContentText("A monitorizar localização...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_GEOFENCE_ID, "Geofencing", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun sendLocationToBackend(userId: String, lat: Double, lng: Double) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/update_location.php"
        val jsonBody = JSONObject().apply {
            put("user_id", userId)
            put("latitude", lat)
            put("longitude", lng)
        }
        val request = JsonObjectRequest(Request.Method.POST, url, jsonBody, {}, {})
        Volley.newRequestQueue(this).add(request)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}