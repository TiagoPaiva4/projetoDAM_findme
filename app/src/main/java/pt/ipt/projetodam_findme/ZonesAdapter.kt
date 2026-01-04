package pt.ipt.projetodam_findme

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ZonesAdapter(
    private val zones: List<Zone>,
    private val context: Context,
    private val onZoneClick: (Zone) -> Unit
) : RecyclerView.Adapter<ZonesAdapter.ZoneViewHolder>() {

    class ZoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvZoneInitial: TextView = itemView.findViewById(R.id.tvZoneInitial)
        val tvZoneName: TextView = itemView.findViewById(R.id.tvZoneName)
        val tvZoneDetails: TextView = itemView.findViewById(R.id.tvZoneDetails)
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

        holder.tvZoneDetails.text = "${zone.coordinates.size} pontos"

        holder.itemView.setOnClickListener { onZoneClick(zone) }
    }

    override fun getItemCount() = zones.size
}
