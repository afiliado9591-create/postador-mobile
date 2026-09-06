package com.postadormobile.app

import android.content.Context
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

        val prefs = getSharedPreferences("postador", Context.MODE_PRIVATE)
        val text = prefs.getString("text", "").orEmpty()
        val mediaUrl = prefs.getString("mediaUrl", "").orEmpty()
        val target = prefs.getString("target", "chooser").orEmpty()

        if (mediaUrl.isBlank()) {
            share(text, null, "text/plain", target)
            finish()
            return
        }

        Toast.makeText(this, "Preparando mídia...", Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                val result = downloadMedia(mediaUrl)
                runOnUiThread {
                    share(text, result.first, result.second, target)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val fallbackText = listOf(text, mediaUrl)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                    Toast.makeText(
                        this,
                        "Não consegui baixar a mídia. Vou compartilhar o link.",
                        Toast.LENGTH_LONG
                    ).show()
                    share(fallbackText, null, "text/plain", target)
                    finish()
                }
            }
        }
    }

    private fun downloadMedia(urlString: String): Pair<Uri, String> {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }

        val serverMime = connection.contentType
            ?.substringBefore(";")
            ?.trim()
            ?.takeIf { it.contains("/") }

        val extensionFromMime = serverMime?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }

        val extensionFromUrl = MimeTypeMap.getFileExtensionFromUrl(urlString)
            .takeIf { it.isNotBlank() }

        val extension = extensionFromMime ?: extensionFromUrl ?: "bin"
        val mime = serverMime ?: guessMime(extension)

        val dir = File(cacheDir, "shared_media").apply { mkdirs() }
        val file = File(dir, "post_${System.currentTimeMillis()}.$extension")

        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        connection.disconnect()

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        return uri to mime
    }

    private fun guessMime(extension: String): String {
        return MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }

    private fun share(text: String, media: Uri?, mime: String, target: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (media != null) mime else "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)

            if (media != null) {
                putExtra(Intent.EXTRA_STREAM, media)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val packageName = when (target) {
            "facebook" -> "com.facebook.katana"
            "instagram" -> "com.instagram.android"
            "tiktok" -> "com.zhiliaoapp.musically"
            else -> null
        }

        if (packageName != null && isPackageInstalled(packageName)) {
            sendIntent.setPackage(packageName)
            startActivity(sendIntent)
        } else {
            startActivity(Intent.createChooser(sendIntent, "Compartilhar postagem"))
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
