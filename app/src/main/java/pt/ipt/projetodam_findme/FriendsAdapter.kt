package pt.ipt.projetodam_findme

import android.content.Context
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

// Classe de dados do Amigo
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
        // MUDANÇA: Agora usamos o TextView da inicial, não o ImageView
        val txtAvatarInitial: TextView = itemView.findViewById(R.id.txtAvatarInitial)
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

        // 2. Avatar (Letra Inicial) - NOVA LÓGICA
        val inicial = if (friend.name.isNotEmpty()) {
            friend.name.first().toString().uppercase()
        } else {
            "?"
        }
        holder.txtAvatarInitial.text = inicial

        // 3. Status Complexo (Cidade + Tempo)
        val tempoTexto = getTempoRelativo(friend.lastUpdate)
        val cidade = obterCidade(context, friend.latitude, friend.longitude)

        val statusText = if (friend.distanceMeters <= 0f && friend.latitude == 0.0) {
            "Localização indisponível"
        } else if (cidade.isNotEmpty()) {
            "$cidade • $tempoTexto"
        } else {
            tempoTexto
        }
        holder.txtStatus.text = statusText

        // 4. Distância Formatada
        if (friend.distanceMeters > 0f) {
            val distanceKm = friend.distanceMeters / 1000
            val formattedDistance = if (distanceKm < 1) {
                "${friend.distanceMeters.toInt()} m"
            } else {
                String.format(Locale.US, "%.1f km", distanceKm)
            }
            holder.txtDistance.text = formattedDistance
        } else {
            holder.txtDistance.text = ""
        }

        // 5. Clique no item
        holder.itemView.setOnClickListener { clickListener(friend) }

        // 6. Botão de Remover
        val canRemove = removeListener != null && currentUserId == groupCreatorId && friend.id != currentUserId

        if (canRemove) {
            holder.btnRemove.visibility = View.VISIBLE
            holder.btnRemove.setOnClickListener {
                removeListener?.invoke(friend)
            }
        } else {
            holder.btnRemove.visibility = View.GONE
        }
    }

    override fun getItemCount() = friendsList.size

    // --- FUNÇÕES AUXILIARES ---

    private fun obterCidade(context: Context, lat: Double, lon: Double): String {
        if (lat == 0.0 && lon == 0.0) return ""
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
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
            if (diffMillis < 0) return "Agora"

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
}