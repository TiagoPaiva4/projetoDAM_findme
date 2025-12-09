package pt.ipt.projetodam_findme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class RequestItem(val id_friendship: Int, val name: String, val email: String)

class RequestsAdapter(
    private val requests: List<RequestItem>,
    private val onAccept: (Int) -> Unit
) : RecyclerView.Adapter<RequestsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txtReqName)
        val email: TextView = view.findViewById(R.id.txtReqEmail)
        val btn: Button = view.findViewById(R.id.btnAccept)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = requests[position]
        holder.name.text = item.name
        holder.email.text = "Quer seguir-te"

        holder.btn.setOnClickListener {
            onAccept(item.id_friendship)
        }
    }

    override fun getItemCount() = requests.size
}