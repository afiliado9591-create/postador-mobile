package com.postadormobile.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class QueueActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var textInput: EditText
    private lateinit var mediaInput: EditText
    private lateinit var targetSpinner: Spinner
    private lateinit var dateButton: Button
    private lateinit var countInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var queueContainer: LinearLayout
    private lateinit var emptyQueue: TextView
    private var scheduledAt: Long = System.currentTimeMillis() + 5 * 60 * 1000L

    private val destinations = listOf(
        Destination("Facebook Feed", "facebook_feed"),
        Destination("Facebook Grupos", "facebook_groups"),
        Destination("Instagram", "instagram"),
        Destination("TikTok", "tiktok"),
        Destination("Escolher aplicativo", "chooser")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Fila automática"
        buildUi()
        renderQueue()
    }

    override fun onResume() {
        super.onResume()
        if (::queueContainer.isInitialized) renderQueue()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { fillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Fila automática no celular"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "O app agenda as postagens, avisa no horário e deixa tudo pronto para você confirmar no Facebook, Instagram ou TikTok."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(6), 0, dp(16))
        })

        root.addView(sectionTitle("Nova campanha"))

        textInput = EditText(this).apply {
            hint = "Texto da postagem"
            minLines = 4
            gravity = android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        root.addView(textInput, fullWidth())

        mediaInput = EditText(this).apply {
            hint = "Link da imagem, vídeo ou página (opcional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(mediaInput, fullWidth(top = 10))

        root.addView(label("Destino"))
        targetSpinner = Spinner(this)
        targetSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            destinations.map { it.label }
        )
        root.addView(targetSpinner, fullWidth())

        dateButton = Button(this).apply {
            text = formatSchedule(scheduledAt)
            setOnClickListener { chooseDateTime() }
        }
        root.addView(dateButton, fullWidth(top = 10))

        val two = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        val countWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        }
        countWrap.addView(label("Quantidade (1 a 20)"))
        countInput = EditText(this).apply {
            setText("1")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        countWrap.addView(countInput, fullWidth())

        val intervalWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        }
        intervalWrap.addView(label("Intervalo em minutos"))
        intervalInput = EditText(this).apply {
            setText("60")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        intervalWrap.addView(intervalInput, fullWidth())

        two.addView(countWrap)
        two.addView(intervalWrap)
        root.addView(two, fullWidth(top = 8))

        root.addView(Button(this).apply {
            text = "Adicionar à fila"
            setOnClickListener { createCampaign() }
        }, fullWidth(top = 12))

        root.addView(TextView(this).apply {
            text = "Dica: para Facebook Feed e Grupos o app prepara tudo e você só confirma a publicação no Facebook."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(10), 0, dp(20))
        })

        root.addView(sectionTitle("Próximas postagens"))
        emptyQueue = TextView(this).apply {
            text = "A fila está vazia."
            textSize = 14f
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(emptyQueue)

        queueContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(queueContainer, fullWidth())
    }

    private fun createCampaign() {
        val text = textInput.text.toString().trim()
        val media = mediaInput.text.toString().trim()
        if (text.isBlank() && media.isBlank()) {
            Toast.makeText(this, "Digite um texto ou coloque um link.", Toast.LENGTH_SHORT).show()
            return
        }

        val count = countInput.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 1
        val interval = intervalInput.text.toString().toLongOrNull()?.coerceIn(15, 1440) ?: 60L
        val destination = destinations[targetSpinner.selectedItemPosition.coerceIn(destinations.indices)]
        val firstTime = scheduledAt.coerceAtLeast(System.currentTimeMillis() + 30_000L)

        repeat(count) { index ->
            val item = ScheduledPostStore.Item(
                id = UUID.randomUUID().toString(),
                text = text,
                mediaUrl = media,
                target = destination.target,
                targetLabel = destination.label,
                scheduledAt = firstTime + index * interval * 60_000L
            )
            ScheduledPostStore.add(this, item)
            ScheduledPostWorker.schedule(this, item)
        }

        Toast.makeText(this, "$count postagem(ns) adicionada(s) à fila.", Toast.LENGTH_LONG).show()
        textInput.text.clear()
        mediaInput.text.clear()
        countInput.setText("1")
        renderQueue()
    }

    private fun renderQueue() {
        queueContainer.removeAllViews()
        val items = ScheduledPostStore.list(this)
        emptyQueue.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        items.forEach { item ->
            queueContainer.addView(queueCard(item))
        }
    }

    private fun queueCard(item: ScheduledPostStore.Item): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(
                when (item.status) {
                    ScheduledPostStore.STATUS_READY -> Color.rgb(255, 248, 225)
                    ScheduledPostStore.STATUS_OPENED -> Color.rgb(232, 245, 233)
                    ScheduledPostStore.STATUS_SKIPPED -> Color.rgb(245, 245, 245)
                    else -> Color.WHITE
                }
            )
        }

        val status = when (item.status) {
            ScheduledPostStore.STATUS_READY -> "🟡 PRONTA"
            ScheduledPostStore.STATUS_OPENED -> "✅ ABERTA"
            ScheduledPostStore.STATUS_SKIPPED -> "⏭ PULADA"
            else -> "⏳ AGENDADA"
        }

        box.addView(TextView(this).apply {
            text = "$status • ${item.targetLabel}"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        box.addView(TextView(this).apply {
            text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.scheduledAt))
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(3), 0, dp(6))
        })
        if (item.text.isNotBlank()) {
            box.addView(TextView(this).apply {
                text = item.text.take(240)
                textSize = 14f
            })
        }
        if (item.mediaUrl.isNotBlank()) {
            box.addView(TextView(this).apply {
                text = item.mediaUrl.take(180)
                textSize = 12f
                setPadding(0, dp(5), 0, 0)
            })
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }

        actions.addView(Button(this).apply {
            text = "Abrir agora"
            setOnClickListener { openNow(item) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })

        actions.addView(Button(this).apply {
            text = "Pular"
            isEnabled = item.status != ScheduledPostStore.STATUS_SKIPPED
            setOnClickListener {
                ScheduledPostWorker.cancel(this@QueueActivity, item.id)
                ScheduledPostStore.updateStatus(this@QueueActivity, item.id, ScheduledPostStore.STATUS_SKIPPED)
                renderQueue()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })

        actions.addView(Button(this).apply {
            text = "Excluir"
            setOnClickListener {
                ScheduledPostWorker.cancel(this@QueueActivity, item.id)
                ScheduledPostStore.delete(this@QueueActivity, item.id)
                renderQueue()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })

        box.addView(actions)

        return box.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            elevation = dp(1).toFloat()
        }
    }

    private fun openNow(item: ScheduledPostStore.Item) {
        ScheduledPostWorker.cancel(this, item.id)
        ScheduledPostStore.updateStatus(this, item.id, ScheduledPostStore.STATUS_OPENED)

        val postId = "queue-${item.id}"
        HistoryStore.add(
            this,
            id = postId,
            text = item.text,
            mediaUrl = item.mediaUrl,
            target = item.target,
            status = HistoryStore.STATUS_RECEBIDA,
            detail = "Aberta manualmente pela fila automática."
        )

        startActivity(Intent(this, ShareActivity::class.java).apply {
            putExtra("postId", postId)
            putExtra("text", item.text)
            putExtra("mediaUrl", item.mediaUrl)
            putExtra("target", item.target)
            putExtra("scheduledQueueId", item.id)
        })
        renderQueue()
    }

    private fun chooseDateTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = scheduledAt }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, month)
                        cal.set(Calendar.DAY_OF_MONTH, day)
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        scheduledAt = cal.timeInMillis
                        dateButton.text = formatSchedule(scheduledAt)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatSchedule(time: Long): String =
        "Data e hora: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(time))

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(8))
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun fullWidth(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Destination(val label: String, val target: String)
}
