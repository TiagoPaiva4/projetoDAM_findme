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

// Certifica-te que esta classe de dados corresponde ao que usas na MainActivity
// Se já tiveres a classe Friend noutro ficheiro, podes apagar esta definição aqui.
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
        val imgAvatar: ImageView = itemView.findViewById(R.id.imgAvatar)
        val txtName: TextView = itemView.findViewById(R.id.txtName)
        val txtStatus: TextView = itemView.findViewById(R.id.txtStatus)
        val txtDistance: TextView = itemView.findViewById(R.id.txtDistance)
        val btnRemove: ImageView = itemView.findViewById(R.id.btnRemoveMember)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        // Assegura que estamos a usar o layout correto 'item_person_modern'
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_person_modern, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendsList[position]
        val context = holder.itemView.context

        // 1. Nome
        holder.txtName.text = friend.name

        // 2. Avatar (Letra Inicial desenhada dinamicamente)
        holder.imgAvatar.setImageBitmap(desenharAvatar(friend.name))

        // 3. Status Complexo (Cidade + Tempo)
        // Nota: O Geocoder pode ser lento na UI thread, mas mantive a tua lógica original
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

        // 4. Distância Formatada (km ou m)
        if (friend.distanceMeters > 0f) {
            val distanceKm = friend.distanceMeters / 1000
            val formattedDistance = if (distanceKm < 1) {
                "${friend.distanceMeters.toInt()} m"
            } else {
                String.format(Locale.US, "%.1f km", distanceKm)
            }
            holder.txtDistance.text = formattedDistance
        } else {
            // Se a distância for 0 ou inválida, esconde ou limpa o texto
            holder.txtDistance.text = ""
        }

        // 5. Clique no item (para abrir o mapa)
        holder.itemView.setOnClickListener { clickListener(friend) }

        // 6. Botão de Remover (Lógica de Grupos)
        // Só mostra o botão se formos o criador do grupo e o amigo não for nós próprios
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
            // Nota: getFromLocation pode bloquear a UI. Idealmente seria feito em background.
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Tenta obter Localidade, se falhar tenta Área Administrativa
                address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            "" // Em caso de erro (sem internet, etc), retorna vazio sem crashar
        }
    }

    private fun getTempoRelativo(dataString: String): String {
        try {
            // Ajusta o formato se a tua BD enviar diferente (ex: com T ou timezone)
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val past = format.parse(dataString)
            val now = Date()

            if (past == null) return "Desconhecido"

            val diffMillis = now.time - past.time
            // Evitar tempos negativos se o relógio do servidor estiver desalinhado
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

    private fun desenharAvatar(nome: String): Bitmap {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Fundo do círculo
        paint.color = Color.parseColor("#444444")
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // Texto (Inicial do nome)
        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD

        val xPos = size / 2f
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2)
        val letra = if (nome.isNotEmpty()) nome.first().toString().uppercase() else "?"

        canvas.drawText(letra, xPos, yPos, paint)

        return bitmap
    }
}