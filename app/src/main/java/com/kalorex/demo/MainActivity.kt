package com.kalorex.demo

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(13, 18, 25)
    private val panel = Color.rgb(25, 33, 43)
    private val muted = Color.rgb(155, 168, 182)
    private var temperature = 35
    private var servoAngle = 90
    private var timer: CountDownTimer? = null
    private var remainingMs = 30 * 60 * 1000L
    private var running = false

    private lateinit var tempValue: TextView
    private lateinit var tempSeek: SeekBar
    private lateinit var servoText: TextView
    private lateinit var timerText: TextView
    private lateinit var minutesInput: EditText
    private lateinit var secondsInput: EditText
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var wifiText: TextView
    private lateinit var tempPanel: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateTemperature()
        updateTimerLabel()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(20))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("KALOREX", 24, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        val demo = label("DEMO", 12, Color.rgb(45, 210, 145), true)
        demo.setPadding(dp(12), dp(7), dp(12), dp(7))
        demo.background = rounded(Color.rgb(24, 64, 54), 30)
        header.addView(demo)
        root.addView(header)
        root.addView(label("CONTROL INTELIGENTE · SIMULACIÓN", 11, muted, false).apply {
            setPadding(0, dp(5), 0, dp(20))
        })

        tempPanel = panel()
        tempPanel.gravity = Gravity.CENTER_HORIZONTAL
        tempPanel.setPadding(dp(18), dp(18), dp(18), dp(18))
        tempPanel.addView(label("TEMPERATURA", 12, muted, true))
        tempValue = label("35 °C", 42, Color.rgb(45, 190, 235), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(2))
        }
        tempPanel.addView(tempValue)
        tempPanel.addView(label("Ajuste de 10 °C a 50 °C", 12, muted, false).apply {
            gravity = Gravity.CENTER
        })
        tempSeek = SeekBar(this).apply {
            max = 40
            progress = 25
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(45, 190, 235))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        tempSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                temperature = 10 + progress
                updateTemperature()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        tempPanel.addView(tempSeek)
        val scale = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        scale.addView(label("10°", 11, muted, false), LinearLayout.LayoutParams(0, -2, 1f))
        scale.addView(label("30°", 11, muted, false).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        scale.addView(label("50°", 11, muted, false).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        tempPanel.addView(scale)
        root.addView(tempPanel, marginBottom(16))

        val servo = panel()
        servo.setPadding(dp(16), dp(16), dp(16), dp(16))
        servo.addView(label("SERVO SIMULADO", 12, muted, true))
        servoText = label("Apertura: 90°", 20, Color.WHITE, true).apply { setPadding(0, dp(8), 0, dp(5)) }
        servo.addView(servoText)
        servo.addView(label("El ajuste de temperatura mueve el servo virtual.", 12, muted, false))
        root.addView(servo, marginBottom(16))

        val timerPanel = panel()
        timerPanel.setPadding(dp(16), dp(16), dp(16), dp(16))
        timerPanel.addView(label("TEMPORIZADOR", 12, muted, true))
        timerText = label("00:30:00", 30, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(12))
        }
        timerPanel.addView(timerText)
        val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        minutesInput = numberInput("30", "Minutos")
        secondsInput = numberInput("00", "Segundos")
        fields.addView(minutesInput, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })
        fields.addView(secondsInput, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(8) })
        timerPanel.addView(fields)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        startButton = button("INICIAR")
        startButton.setOnClickListener { toggleTimer() }
        val reset = button("REINICIAR")
        reset.setOnClickListener { resetTimer() }
        buttons.addView(startButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        buttons.addView(reset, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        timerPanel.addView(buttons.apply { setPadding(0, dp(12), 0, 0) })
        root.addView(timerPanel, marginBottom(16))

        val wifi = panel()
        wifi.orientation = LinearLayout.HORIZONTAL
        wifi.gravity = Gravity.CENTER_VERTICAL
        wifi.setPadding(dp(16), dp(14), dp(16), dp(14))
        wifi.addView(label("WiFi", 15, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        wifiText = label("● ● ● ●  DEMO", 13, Color.rgb(45, 210, 145), true)
        wifi.addView(wifiText)
        root.addView(wifi, marginBottom(12))

        statusText = label("MODO DEMOSTRACIÓN · SIN CONEXIÓN AL BOILER", 10, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(statusText)
    }

    private fun updateTemperature() {
        val color = when {
            temperature < 20 -> Color.rgb(40, 160, 255)
            temperature < 30 -> Color.rgb(35, 205, 175)
            temperature < 40 -> Color.rgb(245, 175, 55)
            else -> Color.rgb(245, 75, 65)
        }
        tempValue.text = "$temperature °C"
        tempValue.setTextColor(color)
        tempSeek.progressTintList = android.content.res.ColorStateList.valueOf(color)
        tempPanel.background = rounded(panel, 20).apply {
            setStroke(dp(1), Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)))
        }
        servoAngle = ((temperature - 10) * 180) / 40
        servoText.text = "Apertura: $servoAngle°"
    }

    private fun toggleTimer() {
        if (running) {
            timer?.cancel()
            running = false
            startButton.text = "CONTINUAR"
            return
        }
        if (remainingMs <= 0) {
            val mins = minutesInput.text.toString().toLongOrNull() ?: 0
            val secs = secondsInput.text.toString().toLongOrNull() ?: 0
            remainingMs = (mins * 60 + secs).coerceAtLeast(1) * 1000
        } else if (timerText.text == "00:30:00" && minutesInput.text.toString() != "30") {
            val mins = minutesInput.text.toString().toLongOrNull() ?: 0
            val secs = secondsInput.text.toString().toLongOrNull() ?: 0
            remainingMs = (mins * 60 + secs).coerceAtLeast(1) * 1000
        }
        running = true
        startButton.text = "PAUSAR"
        timer = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                updateTimerLabel()
            }
            override fun onFinish() {
                remainingMs = 0
                running = false
                updateTimerLabel()
                startButton.text = "INICIAR"
                statusText.text = "TEMPORIZADOR TERMINADO · SIMULACIÓN"
            }
        }.start()
    }

    private fun resetTimer() {
        timer?.cancel()
        running = false
        val mins = minutesInput.text.toString().toLongOrNull() ?: 30
        val secs = secondsInput.text.toString().toLongOrNull() ?: 0
        remainingMs = (mins * 60 + secs).coerceAtLeast(0) * 1000
        startButton.text = "INICIAR"
        statusText.text = "MODO DEMOSTRACIÓN · SIN CONEXIÓN AL BOILER"
        updateTimerLabel()
    }

    private fun updateTimerLabel() {
        val total = (remainingMs / 1000).coerceAtLeast(0)
        timerText.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }

    private fun numberInput(value: String, hintText: String): EditText = EditText(this).apply {
        setText(value)
        hint = hintText
        textSize = 16f
        setTextColor(Color.WHITE)
        setHintTextColor(muted)
        gravity = Gravity.CENTER
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        background = rounded(Color.rgb(35, 45, 57), 12)
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun button(textValue: String): Button = Button(this).apply {
        text = textValue
        textSize = 12f
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(39, 112, 157), 12)
        isAllCaps = false
    }

    private fun label(textValue: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(panel, 20)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun marginBottom(value: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(value)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
