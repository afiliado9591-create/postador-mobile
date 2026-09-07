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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("postador", MODE_PRIVATE)
        val text = prefs.getString("text", "").orEmpty()
        val mediaUrl = prefs.getString("mediaUrl", "").orEmpty()

        if (mediaUrl.isBlank()) {
            mainHandler.postDelayed({
                openShareSheetSafely(text, null, "text/plain")
            }, 350)
            return
        }

        Toast.makeText(this, "Preparando compartilhamento...", Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                val result = downloadDirectMedia(mediaUrl)
                runOnUiThread {
                    openShareSheetSafely(text, result.first, result.second)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    val fallbackText = listOf(text, mediaUrl)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                    openShareSheetSafely(fallbackText, null, "text/plain")
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

    private fun openShareSheetSafely(text: String, media: Uri?, mime: String) {
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

            // Não encerra imediatamente: alguns Androids/MIUI fecham o chooser se a Activity morre na mesma hora.
            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed) finish()
            }, 1500)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Não consegui abrir o compartilhamento. Tente novamente.",
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
