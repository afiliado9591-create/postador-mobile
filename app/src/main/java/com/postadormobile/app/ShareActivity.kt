package com.postadormobile.app

import android.content.ClipData
import android.content.ClipboardManager
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

        Toast.makeText(this, "Preparando postagem...", Toast.LENGTH_SHORT).show()

        executor.execute {
            try {
                val result = downloadMedia(mediaUrl)
                runOnUiThread {
                    share(text, result.first, result.second, target)
                    finish()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    // URLs de páginas (TikTok, Shopee, sites etc.) não são mídia direta.
                    // Nesse caso compartilhamos/copiamos texto + link em vez de baixar HTML.
                    val fallbackText = listOf(text, mediaUrl)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
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
            setRequestProperty("User-Agent", "PostadorMobile/1.1")
        }

        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }

        val serverMime = connection.contentType
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.contains("/") }

        // Só baixa quando for realmente imagem ou vídeo. HTML e outros links viram link normal.
        if (serverMime == null || (!serverMime.startsWith("image/") && !serverMime.startsWith("video/"))) {
            connection.disconnect()
            throw IllegalStateException("URL não é mídia direta")
        }

        val extensionFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(serverMime)
        val extensionFromUrl = MimeTypeMap.getFileExtensionFromUrl(urlString).takeIf { it.isNotBlank() }
        val extension = extensionFromMime ?: extensionFromUrl ?: if (serverMime.startsWith("video/")) "mp4" else "jpg"

        val dir = File(cacheDir, "shared_media").apply { mkdirs() }
        val file = File(dir, "post_${System.currentTimeMillis()}.$extension")

        connection.inputStream.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        connection.disconnect()

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        return uri to serverMime
    }

    private fun share(text: String, media: Uri?, mime: String, target: String) {
        if (target == "facebook") {
            shareToFacebook(text, media, mime)
            return
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (media != null) mime else "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (media != null) {
                putExtra(Intent.EXTRA_STREAM, media)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val targetPackage = when (target) {
            "instagram" -> "com.instagram.android"
            "tiktok" -> "com.zhiliaoapp.musically"
            else -> null
        }

        if (targetPackage != null && isPackageInstalled(targetPackage)) {
            sendIntent.setPackage(targetPackage)
            startActivity(sendIntent)
        } else {
            startActivity(Intent.createChooser(sendIntent, "Compartilhar postagem"))
        }
    }

    private fun shareToFacebook(text: String, media: Uri?, mime: String) {
        val facebookPackage = "com.facebook.katana"

        // Facebook frequentemente ignora EXTRA_TEXT em compartilhamentos.
        // Copiamos a legenda/link para que o usuário possa colar no compositor.
        if (text.isNotBlank()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Postagem", text))
        }

        if (!isPackageInstalled(facebookPackage)) {
            val generic = Intent(Intent.ACTION_SEND).apply {
                type = if (media != null) mime else "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                if (media != null) {
                    putExtra(Intent.EXTRA_STREAM, media)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(Intent.createChooser(generic, "Compartilhar postagem"))
            return
        }

        if (media != null) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                setPackage(facebookPackage)
                putExtra(Intent.EXTRA_STREAM, media)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Toast.makeText(this, "Mídia enviada. O texto também foi copiado para você colar no Facebook.", Toast.LENGTH_LONG).show()
            startActivity(sendIntent)
            return
        }

        // Para texto/link, abrir o Facebook é mais previsível que ACTION_SEND, que pode abrir vazio.
        val launchIntent = packageManager.getLaunchIntentForPackage(facebookPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Toast.makeText(this, "Texto e link copiados. No Facebook, toque e segure para colar.", Toast.LENGTH_LONG).show()
            startActivity(launchIntent)
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
