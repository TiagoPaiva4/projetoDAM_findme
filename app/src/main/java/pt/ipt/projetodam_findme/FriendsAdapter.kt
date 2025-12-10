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

// Modelo de Dados (Mantenha inalterado)
data class Friend(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
    val lastUpdate: String
)

class FriendsAdapter(
    private val friendsList: List<Friend>,
    private val clickListener: (Friend) -> Unit,
    private val currentUserId: Int = -1,
    private val groupCreatorId: Int = -2,
    private val removeListener: ((Friend) -> Unit)? = null
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // IDs CORRIGIDOS para corresponderem ao item_person_modern.xml
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val txtName: TextView = itemView.findViewById(R.id.txtName)
        val txtStatus: TextView = itemView.findViewById(R.id.txtStatus)
        val txtDistance: TextView = itemView.findViewById(R.id.txtDistance)
        val btnRemove: ImageView = itemView.findViewById(R.id.btnRemoveMember)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_person_modern, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendsList[position]
        val context = holder.itemView.context

        // 1. Nome
        holder.txtName.text = friend.name

        // 2. Avatar (Letra Inicial)
        holder.imgAvatar.setImageBitmap(desenharAvatar(friend.name))

        // 3. Status/Tempo/Cidade
        val tempoTexto = getTempoRelativo(friend.lastUpdate)
        val cidade = obterCidade(context, friend.latitude, friend.longitude)

        val statusText = if (friend.distanceMeters <= 0f) {
            "Localização indisponível"
        } else if (cidade.isNotEmpty()) {
            "$cidade • $tempoTexto"
        } else {
            tempoTexto
        }
        holder.txtStatus.text = statusText

        // 4. Distância
        if (friend.distanceMeters > 0f) {
            val distanceKm = friend.distanceMeters / 1000
            val formattedDistance = if (distanceKm < 1) {
                "${friend.distanceMeters.toInt()} m"
            } else {
                String.format("%.1f km", distanceKm)
            }
            holder.txtDistance.text = formattedDistance
        } else {
            holder.txtDistance.text = ""
        }

        // Lógica de clique para abrir o mapa
        holder.itemView.setOnClickListener { clickListener(friend) }

        // LÓGICA DO BOTÃO DE REMOÇÃO
        val canRemove = removeListener != null && currentUserId == groupCreatorId && friend.id != currentUserId

        if (canRemove) {
            holder.btnRemove.visibility = View.VISIBLE
            holder.btnRemove.setOnClickListener {
                removeListener.invoke(friend)
            }
        } else {
            holder.btnRemove.visibility = View.GONE
        }
    }

    override fun getItemCount() = friendsList.size

    // --- FUNÇÕES AUXILIARES ---

    private fun obterCidade(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

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

    private fun desenharAvatar(nome: String): Bitmap {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.color = Color.parseColor("#444444")
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD

        val xPos = size / 2f
        // CORREÇÃO: Usar 'size' em vez de 'height'
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2)
        val letra = if (nome.isNotEmpty()) nome.first().toString().uppercase() else "?"

        canvas.drawText(letra, xPos, yPos, paint)

        return bitmap
    }
}