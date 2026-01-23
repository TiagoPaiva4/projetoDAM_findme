package pt.ipt.projetodam_findme

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ZonesAdapter(
    private val zones: List<Zone>,
    private val context: Context,
    private val onZoneClick: (Zone) -> Unit,
    private val onZoneLongClick: (Zone, View) -> Unit
) : RecyclerView.Adapter<ZonesAdapter.ZoneViewHolder>() {

    class ZoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvZoneInitial: TextView = itemView.findViewById(R.id.tvZoneInitial)
        val tvZoneName: TextView = itemView.findViewById(R.id.tvZoneName)
        val tvZoneDetails: TextView = itemView.findViewById(R.id.tvZoneDetails)
        val ivLocationIcon: ImageView = itemView.findViewById(R.id.ivLocationIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZoneViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_zone, parent, false)
        return ZoneViewHolder(view)
    }

    override fun onBindViewHolder(holder: ZoneViewHolder, position: Int) {
        val zone = zones[position]

        holder.tvZoneName.text = zone.name

        if (zone.name.isNotEmpty()) {
            holder.tvZoneInitial.text = zone.name.first().toString().uppercase()
        } else {
            holder.tvZoneInitial.text = "?"
        }

        // Show status in details
        val statusText = if (zone.isActive) "Ativa" else "Desativada"
        holder.tvZoneDetails.text = "${zone.coordinates.size} pontos • $statusText"

        // Visual indicator for disabled zones
        if (zone.isActive) {
            holder.tvZoneInitial.alpha = 1.0f
            holder.tvZoneName.alpha = 1.0f
            holder.tvZoneDetails.setTextColor(Color.parseColor("#8E8E93"))
            holder.ivLocationIcon.setColorFilter(Color.parseColor("#3A8DDE"))
        } else {
            holder.tvZoneInitial.alpha = 0.4f
            holder.tvZoneName.alpha = 0.5f
            holder.tvZoneDetails.setTextColor(Color.parseColor("#F44336"))
            holder.ivLocationIcon.setColorFilter(Color.parseColor("#808080"))
        }

        holder.itemView.setOnClickListener { onZoneClick(zone) }
        holder.itemView.setOnLongClickListener {
            onZoneLongClick(zone, it)
            true
        }
    }

    override fun getItemCount() = zones.size
}
