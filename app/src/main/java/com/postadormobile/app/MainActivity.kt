package com.postadormobile.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.postadormobile.app.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var deviceToken: String = ""

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Sem essa permissão o app pode não mostrar as ordens recebidas.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        askNotificationPermission()

        binding.queueButton.setOnClickListener {
            startActivity(Intent(this, QueueActivity::class.java))
        }

        binding.refreshTokenButton.setOnClickListener {
            regenerateToken()
        }

        binding.copyTokenButton.setOnClickListener {
            if (deviceToken.isBlank()) {
                Toast.makeText(this, "Gere o token primeiro.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FCM token", deviceToken))
            Toast.makeText(this, "Token copiado.", Toast.LENGTH_SHORT).show()
        }

        loadToken()
        renderHistory()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) renderHistory()
    }

    private fun loadToken() {
        deviceToken = ""
        binding.tokenText.text = "Gerando token..."
        binding.refreshTokenButton.isEnabled = false
        binding.copyTokenButton.isEnabled = false

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            binding.refreshTokenButton.isEnabled = true

            if (!task.isSuccessful) {
                val error = task.exception
                val type = error?.javaClass?.simpleName ?: "erro desconhecido"
                val message = error?.localizedMessage?.take(220) ?: "sem mensagem"
                binding.tokenText.text = "Token indisponível\n$type: $message"
                return@addOnCompleteListener
            }

            val token = task.result.orEmpty().trim()
            if (token.isBlank()) {
                binding.tokenText.text = "Token indisponível\nO Firebase retornou um token vazio."
                return@addOnCompleteListener
            }

            deviceToken = token
            binding.tokenText.text = "✓ Token configurado neste aparelho"
            binding.copyTokenButton.isEnabled = true
        }
    }

    private fun regenerateToken() {
        deviceToken = ""
        binding.tokenText.text = "Atualizando token..."
        binding.refreshTokenButton.isEnabled = false
        binding.copyTokenButton.isEnabled = false

        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener {
            loadToken()
        }
    }

    private fun renderHistory() {
        val items = HistoryStore.list(this)
        binding.historyContainer.removeAllViews()
        binding.emptyHistoryText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        items.forEach { item ->
            binding.historyContainer.addView(createHistoryCard(item))
        }
    }

    private fun createHistoryCard(item: HistoryStore.Item): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
            background = historyBackground(item.status)
        }

        val statusText = when (item.status) {
            HistoryStore.STATUS_ENVIADA -> "✅ ENVIADA"
            HistoryStore.STATUS_FALHOU -> "❌ FALHOU"
            else -> "⏳ RECEBIDA"
        }

        card.addView(TextView(this).apply {
            text = statusText
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        card.addView(TextView(this).apply {
            text = dateFormat.format(Date(if (item.updatedAt > 0) item.updatedAt else item.createdAt))
            textSize = 12f
            setPadding(0, dp(3), 0, dp(7))
        })

        val destination = when (item.target) {
            "facebook", "facebook_feed" -> "Facebook Feed"
            "facebook_groups" -> "Facebook Grupos"
            "instagram" -> "Instagram"
            "tiktok" -> "TikTok"
            "chooser", "" -> "Escolher aplicativo"
            else -> item.target
        }
        card.addView(TextView(this).apply {
            text = "Destino: $destination"
            textSize = 13f
        })

        if (item.text.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = item.text.take(350)
                textSize = 15f
                setPadding(0, dp(8), 0, 0)
            })
        }

        if (item.mediaUrl.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = item.mediaUrl
                textSize = 13f
                setPadding(0, dp(8), 0, 0)
                autoLinkMask = Linkify.WEB_URLS
                linksClickable = true
            })
        }

        if (item.detail.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = item.detail
                textSize = 13f
                setPadding(0, dp(9), 0, 0)
                if (item.status == HistoryStore.STATUS_FALHOU) {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            })
        }

        return card
    }

    private fun historyBackground(status: String): GradientDrawable {
        val fill = when (status) {
            HistoryStore.STATUS_ENVIADA -> Color.rgb(232, 245, 233)
            HistoryStore.STATUS_FALHOU -> Color.rgb(255, 235, 238)
            else -> Color.rgb(255, 248, 225)
        }
        val border = when (status) {
            HistoryStore.STATUS_ENVIADA -> Color.rgb(46, 125, 50)
            HistoryStore.STATUS_FALHOU -> Color.rgb(198, 40, 40)
            else -> Color.rgb(249, 168, 37)
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(fill)
            setStroke(dp(1), border)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
