package com.postadormobile.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ShareActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var postId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("postador", MODE_PRIVATE)
        postId = intent.getStringExtra("postId") ?: prefs.getString("postId", "").orEmpty()
        val text = intent.getStringExtra("text") ?: prefs.getString("text", "").orEmpty()
        val mediaUrl = intent.getStringExtra("mediaUrl") ?: prefs.getString("mediaUrl", "").orEmpty()

        if (mediaUrl.isBlank()) {
            mainHandler.postDelayed({
                openShareSheetSafely(
                    text = text,
                    media = null,
                    mime = "text/plain",
                    successDetail = "Compartilhamento aberto com sucesso. Confirme a publicação no aplicativo escolhido."
                )
            }, 350)
            return
        }

        Toast.makeText(this, "Preparando compartilhamento...", Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                val result = downloadDirectMedia(mediaUrl)
                runOnUiThread {
                    openShareSheetSafely(
                        text = text,
                        media = result.first,
                        mime = result.second,
                        successDetail = "Imagem ou vídeo preparado e enviado para a tela de compartilhamento."
                    )
                }
            } catch (_: Exception) {
                runOnUiThread {
                    val fallbackText = listOf(text, mediaUrl)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                    openShareSheetSafely(
                        text = fallbackText,
                        media = null,
                        mime = "text/plain",
                        successDetail = "O link não era uma mídia direta e foi enviado como texto/link."
                    )
                }
            }
        }
    }

    private fun downloadDirectMedia(urlString: String): Pair<Uri, String> {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 Android PostadorMobile")
        }

        try {
            connection.connect()

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }

            val mime = connection.contentType
                ?.substringBefore(";")
                ?.trim()
                ?.lowercase()
                ?: throw IllegalStateException("Sem content-type")

            if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                throw IllegalStateException("Não é mídia direta")
            }

            val extByMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            val extByUrl = MimeTypeMap.getFileExtensionFromUrl(urlString).takeIf { it.isNotBlank() }
            val extension = extByMime ?: extByUrl ?: if (mime.startsWith("video/")) "mp4" else "jpg"

            val dir = File(cacheDir, "shared_media").apply { mkdirs() }
            val file = File(dir, "post_${System.currentTimeMillis()}.$extension")

            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            return uri to mime
        } finally {
            connection.disconnect()
        }
    }

    private fun openShareSheetSafely(
        text: String,
        media: Uri?,
        mime: String,
        successDetail: String
    ) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (media != null) mime else "text/plain"

                if (text.isNotBlank()) {
                    putExtra(Intent.EXTRA_TEXT, text)
                }

                if (media != null) {
                    putExtra(Intent.EXTRA_STREAM, media)
                    clipData = ClipData.newUri(contentResolver, "postador_media", media)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val chooser = Intent.createChooser(sendIntent, "Compartilhar postagem")
            if (media != null) {
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(chooser)
            HistoryStore.update(this, postId, HistoryStore.STATUS_ENVIADA, successDetail)

            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed) finish()
            }, 1500)
        } catch (_: Exception) {
            HistoryStore.update(
                this,
                postId,
                HistoryStore.STATUS_FALHOU,
                "Não foi possível abrir a tela de compartilhamento no Android."
            )
            Toast.makeText(
                this,
                "Falhou ao abrir o compartilhamento. Veja em Minhas postagens.",
                Toast.LENGTH_LONG
            ).show()
            mainHandler.postDelayed({ finish() }, 1800)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdown()
        super.onDestroy()
    }
}
