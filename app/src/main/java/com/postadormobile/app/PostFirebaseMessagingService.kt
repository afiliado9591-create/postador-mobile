package com.postadormobile.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

class PostFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["kind"] != "post_request") return

        val text = message.data["text"].orEmpty()
        val mediaUrl = message.data["mediaUrl"].orEmpty()
        val target = message.data["target"].orEmpty()
        val postId = UUID.randomUUID().toString()

        val prefs = getSharedPreferences("postador", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("text", text)
            .putString("mediaUrl", mediaUrl)
            .putString("target", target)
            .putString("postId", postId)
            .apply()

        HistoryStore.add(
            this,
            id = postId,
            text = text,
            mediaUrl = mediaUrl,
            target = target,
            status = HistoryStore.STATUS_RECEBIDA,
            detail = "Recebida do painel. Toque na notificação para compartilhar."
        )

        showNotification(postId, text, mediaUrl, target)
    }

    private fun showNotification(postId: String, text: String, mediaUrl: String, target: String) {
        val channelId = "post_requests"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Postagens recebidas",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val intent = Intent(this, ShareActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("postId", postId)
            putExtra("text", text)
            putExtra("mediaUrl", mediaUrl)
            putExtra("target", target)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            postId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val preview = text.trim().replace("\n", " ").take(70)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Postagem pronta")
            .setContentText(if (preview.isBlank()) "Toque para abrir e compartilhar." else preview)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(postId.hashCode(), notification)
    }
}
