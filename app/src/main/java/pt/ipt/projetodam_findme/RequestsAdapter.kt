/**
 * RequestsAdapter.kt
 *
 * Adapter para RecyclerView que mostra pedidos de amizade pendentes.
 * Permite aceitar ou rejeitar cada pedido.
 */
package pt.ipt.projetodam_findme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Modelo de dados simples para o pedido
data class FriendRequest(
    val id: Int,
    val senderId: Int,
    val name: String
)

class RequestsAdapter(
    private val requests: List<FriendRequest>,
    private val onAccept: (FriendRequest) -> Unit,
    private val onReject: (FriendRequest) -> Unit
) : RecyclerView.Adapter<RequestsAdapter.RequestViewHolder>() {


    class RequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvRequestName) // Confirma se o ID no XML é tvRequestName ou tvName
        val btnAccept: ImageButton = itemView.findViewById(R.id.btnAccept)
        val btnReject: ImageButton = itemView.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_request, parent, false)
        return RequestViewHolder(view)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]

        // Define o nome da pessoa
        holder.tvName.text = request.name

        // Configura o clique no botão de aceitar
        holder.btnAccept.setOnClickListener {
            onAccept(request)
        }

        // Configura o clique no botão de rejeitar
        holder.btnReject.setOnClickListener {
            onReject(request)
        }
    }

    override fun getItemCount(): Int {
        return requests.size
    }
}