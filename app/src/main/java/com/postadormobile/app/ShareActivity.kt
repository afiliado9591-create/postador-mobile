package com.postadormobile.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("postador", MODE_PRIVATE)
        val text = prefs.getString("text", "").orEmpty()
        val mediaUrl = prefs.getString("mediaUrl", "").orEmpty()

        if (mediaUrl.isBlank()) {
            openShareSheet(text, null, "text/plain")
            finish()
            return
        }

        Toast.makeText(this, "Preparando compartilhamento...", Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                val result = downloadDirectMedia(mediaUrl)
                runOnUiThread {
                    openShareSheet(text, result.first, result.second)
                    finish()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    val fallbackText = listOf(text, mediaUrl)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                    openShareSheet(fallbackText, null, "text/plain")
                    finish()
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
            connection.disconnect()
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
        connection.disconnect()

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        return uri to mime
    }

    private fun openShareSheet(text: String, media: Uri?, mime: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (media != null) mime else "text/plain"
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            if (media != null) {
                putExtra(Intent.EXTRA_STREAM, media)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(contentResolver, "media", media)
            }
        }

        val chooser = Intent.createChooser(sendIntent, "Compartilhar postagem")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(chooser)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
