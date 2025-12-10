package pt.ipt.projetodam_findme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Group(val id: Int, val name: String, val totalMembers: Int)

class GroupsAdapter(
    private val groups: List<Group>,
    private val onClick: (Group) -> Unit
) : RecyclerView.Adapter<GroupsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.txtGroupName)
        val txtInfo: TextView = view.findViewById(R.id.txtGroupInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]
        holder.txtName.text = group.name
        holder.txtInfo.text = "${group.totalMembers} membros"

        holder.itemView.setOnClickListener { onClick(group) }
    }

    override fun getItemCount() = groups.size
}