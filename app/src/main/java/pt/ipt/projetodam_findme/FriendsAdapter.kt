/**
 * FriendsAdapter.kt
 *
 * Adapter para RecyclerView que mostra a lista de amigos.
 * Inclui avatar com inicial, nome, cidade, distância e tempo relativo.
 * Suporta rodapé opcional para adicionar amigos.
 */
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

// Data class (mantém-se igual)
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
    private val removeListener: ((Friend) -> Unit)? = null,
    private val addFriendListener: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1
    }

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtAvatarInitial: TextView = itemView.findViewById(R.id.txtAvatarInitial)
        val txtName: TextView = itemView.findViewById(R.id.txtName)
        val txtStatus: TextView = itemView.findViewById(R.id.txtStatus)
        val txtDistance: TextView = itemView.findViewById(R.id.txtDistance)
        val btnRemove: ImageView = itemView.findViewById(R.id.btnRemoveMember)
    }

    class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun getItemViewType(position: Int): Int {
        return if (addFriendListener != null && position == friendsList.size) {
            TYPE_FOOTER
        } else {
            TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_person_modern, parent, false)
            FriendViewHolder(view)
        } else {
            // Layout do botão de rodapé
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_add_friend_footer, parent, false)
            FooterViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_ITEM) {
            val friendHolder = holder as FriendViewHolder
            val friend = friendsList[position]
            val context = friendHolder.itemView.context

            // Nome
            friendHolder.txtName.text = friend.name

            // Avatar
            val inicial = if (friend.name.isNotEmpty()) friend.name.first().toString().uppercase() else "?"
            friendHolder.txtAvatarInitial.text = inicial

            // Status
            val tempoTexto = getTempoRelativo(friend.lastUpdate)
            val cidade = obterCidade(context, friend.latitude, friend.longitude)

            val statusText = if (friend.distanceMeters <= 0f && friend.latitude == 0.0) {
                "Localização indisponível"
            } else if (cidade.isNotEmpty()) {
                "$cidade • $tempoTexto"
            } else {
                tempoTexto
            }
            friendHolder.txtStatus.text = statusText

            // Distância
            if (friend.distanceMeters > 0f) {
                val distanceKm = friend.distanceMeters / 1000
                val formattedDistance = if (distanceKm < 1) {
                    "${friend.distanceMeters.toInt()} m"
                } else {
                    String.format(Locale.US, "%.1f km", distanceKm)
                }
                friendHolder.txtDistance.text = formattedDistance
            } else {
                friendHolder.txtDistance.text = ""
            }

            // Cliques
            friendHolder.itemView.setOnClickListener { clickListener(friend) }

            // Remover (apenas se for criador do grupo)
            val canRemove = removeListener != null && currentUserId == groupCreatorId && friend.id != currentUserId
            if (canRemove) {
                friendHolder.btnRemove.visibility = View.VISIBLE
                friendHolder.btnRemove.setOnClickListener { removeListener?.invoke(friend) }
            } else {
                friendHolder.btnRemove.visibility = View.GONE
            }

        } else {
            // Só entra aqui se addFriendListener != null
            holder.itemView.setOnClickListener {
                addFriendListener?.invoke()
            }
        }
    }

    override fun getItemCount(): Int {
        return friendsList.size + if (addFriendListener != null) 1 else 0
    }

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