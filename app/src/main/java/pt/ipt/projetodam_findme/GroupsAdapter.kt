package pt.ipt.projetodam_findme

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// A tua classe de dados
data class Group(val id: Int, val name: String, val totalMembers: Int)

class GroupsAdapter(
    private val groups: List<Group>,
    private val context: Context // Adicionei o Contexto para poder iniciar Activities
) : RecyclerView.Adapter<GroupsAdapter.GroupViewHolder>() {

    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // IDs definidos no teu item_group.xml
        val tvGroupInitial: TextView = itemView.findViewById(R.id.tvGroupInitial)
        val tvGroupName: TextView = itemView.findViewById(R.id.tvGroupName)
        val tvGroupDetails: TextView = itemView.findViewById(R.id.tvGroupDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]

        // 1. Nome do Grupo
        holder.tvGroupName.text = group.name

        // 2. Detalhes (Membros)
        holder.tvGroupDetails.text = "${group.totalMembers} membros"

        // 3. Primeira Letra (Ícone)
        if (group.name.isNotEmpty()) {
            holder.tvGroupInitial.text = group.name.first().toString().uppercase()
        } else {
            holder.tvGroupInitial.text = "?"
        }

        // 4. Clique no item -> Abrir Detalhes
        holder.itemView.setOnClickListener {
            val intent = Intent(context, GroupDetailsActivity::class.java)
            intent.putExtra("GROUP_ID", group.id)
            intent.putExtra("GROUP_NAME", group.name)
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = groups.size
}