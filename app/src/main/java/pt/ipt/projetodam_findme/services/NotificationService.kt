/**
 * NotificationService.kt
 *
 * Serviço em primeiro plano para verificar novos pedidos de amizade.
 * Envia notificações push quando há pedidos pendentes.
 * Verifica a cada 60 segundos para poupar bateria.
 */
package pt.ipt.projetodam_findme.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.android.volley.Request
import com.android.volley.RequestQueue // [NOVO] Import necessário
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import pt.ipt.projetodam_findme.ProfileActivity
import pt.ipt.projetodam_findme.R

class NotificationService : Service() {

    private var userId: String? = null
    private val handler = Handler(Looper.getMainLooper())

    // [CORREÇÃO] Variável para armazenar a fila única
    private lateinit var requestQueue: RequestQueue

    // ALTERAÇÃO: Verificar a cada 60 segundos para poupar bateria
    private val POLL_INTERVAL = 60000L

    companion object {
        private const val CHANNEL_SERVICE_ID = "notification_service_channel"
        private const val CHANNEL_ALERTS_ID = "friend_requests_channel"
        private const val NOTIFICATION_ID = 999
        private const val PREFS_NAME = "NotificacoesLog"
    }

    // O "Robot" que executa a tarefa repetidamente
    private val checkRunnable = object : Runnable {
        override fun run() {
            userId?.let { checkFriendRequests(it) }
            // Agenda a próxima execução
            handler.postDelayed(this, POLL_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // [CORREÇÃO] Inicializar a fila APENAS UMA VEZ aqui
        requestQueue = Volley.newRequestQueue(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getStringExtra("USER_ID")

        if (userId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Mostrar notificação fixa "A verificar..." (Obrigatório para serviços background)
        val notification = createServiceNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Iniciar o Loop
        handler.post(checkRunnable)

        return START_STICKY
    }

    private fun checkFriendRequests(id: String) {
        val url = "https://findmyandroid-e0cdh2ehcubgczac.francecentral-01.azurewebsites.net/backend/get_pending_requests.php?user_id=$id"

        // [CORREÇÃO] Removemos a linha: val queue = Volley.newRequestQueue(...)
        // Usamos a requestQueue global criada no onCreate

        val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
            val requestsArray = response.optJSONArray("requests")
            if (requestsArray != null) {

                // ALTERAÇÃO: Usar SharedPreferences para persistir quem já foi notificado
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()

                for (i in 0 until requestsArray.length()) {
                    val obj = requestsArray.getJSONObject(i)
                    val reqId = obj.getInt("id_friendship")
                    val senderName = obj.getString("name")

                    // Chave única para este pedido de amizade
                    val key = "req_$reqId"

                    // Se AINDA NÃO notificámos este pedido (false no prefs)
                    if (!prefs.getBoolean(key, false)) {
                        sendAlertNotification(senderName)

                        // Marcar como notificado para sempre
                        editor.putBoolean(key, true)
                    }
                }
                editor.apply()
            }
        }, { Log.e("NotifService", "Erro API: ${it.message}") })

        // [CORREÇÃO] Adicionar à fila única
        requestQueue.add(request)
    }

    private fun sendAlertNotification(name: String) {
        val manager = getSystemService(NotificationManager::class.java)

        // Ao clicar abre o Perfil
        val intent = Intent(this, ProfileActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setContentTitle("Novo Pedido de Amizade")
            .setContentText("$name quer adicionar-te!")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500)) // Vibrar
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Usa o tempo atual como ID para permitir várias notificações
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle("FindMe Notificações")
            .setContentText("A aguardar novos pedidos...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Canal Silencioso (Serviço)
            val serviceChannel = NotificationChannel(CHANNEL_SERVICE_ID, "Serviço de Background", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(serviceChannel)

            // Canal Barulhento (Alertas)
            val alertsChannel = NotificationChannel(CHANNEL_ALERTS_ID, "Pedidos de Amizade", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable) // Parar o loop
        // [OPCIONAL] Se quiseres limpar a fila ao destruir o serviço:
        // requestQueue.cancelAll { true }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}