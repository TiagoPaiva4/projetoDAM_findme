package pt.ipt.projetodam_findme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Modelo de Dados
data class Friend(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
    val lastUpdate: String
)

// Adaptador
class FriendsAdapter(private val friends: List<Friend>, private val onClick: (Friend) -> Unit) :
    RecyclerView.Adapter<FriendsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtDistance: TextView = view.findViewById(R.id.txtDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val friend = friends[position]

        holder.txtName.text = friend.name

        // Formatação da distância (m ou km)
        val distString = if (friend.distanceMeters >= 1000) {
            String.format("%.1f km", friend.distanceMeters / 1000)
        } else {
            "${friend.distanceMeters.toInt()} m"
        }
        holder.txtDistance.text = distString

        // Estado simples (Podes melhorar com Geocoder para dizer "Lousã")
        holder.txtStatus.text = "Atualizado: ${friend.lastUpdate}"

        // Clique na linha
        holder.itemView.setOnClickListener { onClick(friend) }
    }

    override fun getItemCount() = friends.size
}