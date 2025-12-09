package pt.ipt.projetodam_findme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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

// Modelo de Dados
data class Friend(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
    val lastUpdate: String // Vem do servidor como "2023-12-09 19:56:59"
)

class FriendsAdapter(
    private val friends: List<Friend>,
    private val onClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val txtDistance: TextView = view.findViewById(R.id.txtDistance) // Adicionei este ID no XML acima
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Certifica-te que o nome do layout aqui é o correto (item_person_modern ou item_friend)
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person_modern, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val friend = friends[position]

        // 1. Nome
        holder.txtName.text = friend.name

        // 2. Avatar (Letra Inicial)
        holder.imgAvatar.setImageBitmap(desenharAvatar(friend.name))

        // 3. Tempo Relativo ("Há 5 min")
        val tempoTexto = getTempoRelativo(friend.lastUpdate)
        holder.txtStatus.text = tempoTexto

        // 4. Distância
        val distString = if (friend.distanceMeters >= 1000) {
            String.format("%.1f km", friend.distanceMeters / 1000)
        } else {
            "${friend.distanceMeters.toInt()} m"
        }
        holder.txtDistance.text = distString

        // Clique
        holder.itemView.setOnClickListener { onClick(friend) }
    }

    override fun getItemCount() = friends.size

    // --- FUNÇÃO PARA CALCULAR TEMPO ("Há X min") ---
    private fun getTempoRelativo(dataString: String): String {
        try {
            // Formato que vem do MySQL/PHP
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val past = format.parse(dataString)
            val now = Date()

            if (past == null) return "Desconhecido"

            val diffMillis = now.time - past.time
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

    // --- FUNÇÃO PARA DESENHAR A BOLA COM LETRA ---
    private fun desenharAvatar(nome: String): Bitmap {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // 1. Fundo Circular (Azulão ou Cinzento)
        paint.color = Color.parseColor("#444444") // Cinzento escuro
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        // 2. Letra
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