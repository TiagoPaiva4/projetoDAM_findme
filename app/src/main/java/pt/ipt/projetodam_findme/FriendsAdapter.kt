package pt.ipt.projetodam_findme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Geocoder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Modelo de Dados
data class Friend(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
    val lastUpdate: String
)

class FriendsAdapter(
    private val friends: List<Friend>,
    private val onClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtDistance: TextView = view.findViewById(R.id.txtDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person_modern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val friend = friends[position]
        val context = holder.itemView.context // Necessário para o Geocoder

        // 1. Nome
        holder.txtName.text = friend.name

        // 2. Avatar (Letra Inicial)
        holder.imgAvatar.setImageBitmap(desenharAvatar(friend.name))

        // 3. Tempo e Cidade
        val tempoTexto = getTempoRelativo(friend.lastUpdate)
        val cidade = obterCidade(context, friend.latitude, friend.longitude)

        // Junta a cidade com o tempo: "Coimbra • Há 5 min"
        if (cidade.isNotEmpty()) {
            holder.txtStatus.text = "$cidade • $tempoTexto"
        } else {
            holder.txtStatus.text = tempoTexto
        }

        // 4. Distância
        val distString = if (friend.distanceMeters >= 1000) {
            String.format("%.1f km", friend.distanceMeters / 1000)
        } else {
            "${friend.distanceMeters.toInt()} m"
        }
        holder.txtDistance.text = distString

        // Clique
        holder.itemView.setOnClickListener { onClick(friend) }
    }

    override fun getItemCount() = friends.size

    // --- NOVA FUNÇÃO: OBTER NOME DA CIDADE ---
    private fun obterCidade(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            // Pega 1 resultado
            val addresses = geocoder.getFromLocation(lat, lon, 1)

            if (!addresses.isNullOrEmpty()) {
                // Tenta obter a localidade (Cidade), se não der, tenta o subAdminArea ou adminArea
                val address = addresses[0]
                address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            "" // Se der erro (sem net, etc), retorna vazio
        }
    }

    // --- FUNÇÃO PARA CALCULAR TEMPO ---
    private fun getTempoRelativo(dataString: String): String {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val past = format.parse(dataString)
            val now = Date()

            if (past == null) return "Desconhecido"

            val diffMillis = now.time - past.time
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
            val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
            val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

            return when {
                minutes < 1 -> "Agora"
                minutes < 60 -> "Há $minutes min"
                hours < 24 -> "Há $hours h"
                else -> "Há $days dias"
            }
        } catch (e: Exception) {
            return "Desconhecido"
        }
    }

    // --- FUNÇÃO PARA DESENHAR A BOLA COM LETRA ---
    private fun desenharAvatar(nome: String): Bitmap {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // 1. Fundo Circular
        paint.color = Color.parseColor("#444444")
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // 2. Letra
        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD

        val xPos = size / 2f
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2)
        val letra = if (nome.isNotEmpty()) nome.first().toString().uppercase() else "?"

        canvas.drawText(letra, xPos, yPos, paint)

        return bitmap
    }
}