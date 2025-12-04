package pt.ipt.projetodam_findme

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. VERIFICAR SE O UTILIZADOR ESTÁ LOGADO
        val sharedPreferences = getSharedPreferences("SessaoUsuario", MODE_PRIVATE)
        val estaLogado = sharedPreferences.getBoolean("logado", false)

        if (!estaLogado) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // --- Configurar Botão de Sair (Logout) ---
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            // Limpa os dados da sessão
            sharedPreferences.edit {
                clear()
            }

            // Volta ao ecrã de login
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        // --- Configurar Botões da Barra Inferior ---
        val btnPessoas = findViewById<Button>(R.id.btnPessoas)
        val btnGrupos = findViewById<Button>(R.id.btnGrupos)
        val btnCirculos = findViewById<Button>(R.id.btnCirculos)
        val btnEu = findViewById<Button>(R.id.btnEu)

        btnPessoas.setOnClickListener {
            Toast.makeText(this, "A abrir Pessoas...", Toast.LENGTH_SHORT).show()
            // Aqui futuramente colocas: startActivity(Intent(this, PessoasActivity::class.java))
        }

        btnGrupos.setOnClickListener {
            Toast.makeText(this, "A abrir Grupos...", Toast.LENGTH_SHORT).show()
        }

        btnCirculos.setOnClickListener {
            Toast.makeText(this, "A abrir Círculos...", Toast.LENGTH_SHORT).show()
        }

        btnEu.setOnClickListener {
            Toast.makeText(this, "A abrir Perfil...", Toast.LENGTH_SHORT).show()
        }
    }
}