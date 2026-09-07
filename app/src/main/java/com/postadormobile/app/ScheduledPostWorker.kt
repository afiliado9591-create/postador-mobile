package com.postadormobile.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ScheduledPostWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val id = inputData.getString(KEY_ID).orEmpty()
        if (id.isBlank()) return Result.success()

        val item = ScheduledPostStore.get(applicationContext, id) ?: return Result.success()
        if (item.status != ScheduledPostStore.STATUS_PENDING) return Result.success()

        ScheduledPostStore.updateStatus(applicationContext, id, ScheduledPostStore.STATUS_READY)

        val detail = if (item.groupName.isBlank()) {
            "Fila automática: chegou o horário. Toque na notificação para abrir a postagem."
        } else {
            "Fila automática: chegou o horário do grupo ${item.groupName}. Toque para preparar a postagem."
        }

        HistoryStore.add(
            applicationContext,
            id = "queue-${item.id}",
            text = item.text,
            mediaUrl = item.mediaUrl,
            target = item.target,
            status = HistoryStore.STATUS_RECEBIDA,
            detail = detail
        )

        showNotification(item)
        return Result.success()
    }

    private fun showNotification(item: ScheduledPostStore.Item) {
        val channelId = "scheduled_posts"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Fila automática",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos quando uma postagem agendada estiver pronta"
                }
            )
        }

        val intent = Intent(applicationContext, ShareActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("postId", "queue-${item.id}")
            putExtra("text", item.text)
            putExtra("mediaUrl", item.mediaUrl)
            putExtra("target", item.target)
            putExtra("groupName", item.groupName)
            putExtra("groupUrl", item.groupUrl)
            putExtra("scheduledQueueId", item.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val preview = item.text.trim().replace("\n", " ").take(90)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Hora de publicar • ${item.targetLabel}")
            .setContentText(if (preview.isBlank()) "Toque para abrir a postagem." else preview)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (item.groupName.isBlank()) {
                        if (preview.isBlank()) "Sua postagem agendada está pronta." else item.text.take(350)
                    } else {
                        "Grupo: ${item.groupName}\n\n${item.text.take(300)}"
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            manager.notify(item.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // A fila continua salva mesmo quando o usuário desativou notificações.
        }
    }

    companion object {
        private const val KEY_ID = "scheduled_post_id"

        fun workName(id: String) = "scheduled-post-$id"

        fun schedule(context: Context, item: ScheduledPostStore.Item) {
            val delay = (item.scheduledAt - System.currentTimeMillis()).coerceAtLeast(0L)
            val data = Data.Builder().putString(KEY_ID, item.id).build()
            val request = OneTimeWorkRequestBuilder<ScheduledPostWorker>()
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("scheduled-post")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(item.id),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, id: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        }
    }
}
