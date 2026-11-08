package com.kalorex.demo

import android.app.Activity
import android.app.AlertDialog
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.util.Locale
import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import android.content.SharedPreferences
import android.graphics.Typeface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : Activity() {

    private val bg = Color.rgb(248, 248, 248)
    private val panel = Color.WHITE
    private val blue = Color.rgb(198, 0, 28)
    private val green = Color.rgb(35, 155, 75)
    private val white = Color.rgb(25, 30, 38)

    private var setTemp = 32
    private var timerDurationMs = 30 * 60 * 1000L
    private var remainingMs = timerDurationMs
    private var timer: CountDownTimer? = null
    private var timerRunning = false
    private var boilerOn = true
    private var servoOpen = 99
    private var wifiConnected = true
    private lateinit var prefs: SharedPreferences
    private var currentPage = "Inicio"
    private val dayNames = arrayOf("Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
    private val dayStart = Array(7) { "06:00" }
    private val dayEnd = Array(7) { "08:00" }
    private val dayTemp = IntArray(7) { 40 }
    private val dayEnabled = BooleanArray(7) { true }

    private lateinit var boilerStatus: TextView
    private lateinit var servoStatus: TextView
    private lateinit var timerText: TextView
    private lateinit var timerSwitch: Switch
    private lateinit var gauge: TemperatureGauge
    private lateinit var wifiText: TextView
    private lateinit var onlineText: TextView
    private lateinit var powerButton: Button
    private lateinit var pauseButton: Button
    private lateinit var clockText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("kalorex_settings", MODE_PRIVATE)
        loadSavedValues()
        WeeklyScheduleReceiver.scheduleAll(this)
        buildScreen()
    }

    private fun buildScreen() {
        showPage("Inicio")
    }

    private fun showPage(page: String) {
        currentPage = page
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        scroll.addView(root)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.rgb(194, 0, 24)); setPadding(dp(4), 0, dp(4), 0) }
        header.addView(textButton("☰", 27).apply { setOnClickListener { showMenu() } }, LinearLayout.LayoutParams(dp(48), dp(54)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        titleBox.addView(label("🔥 KALOREX SMART", 23, white, true))
        titleBox.addView(label(page, 14, Color.LTGRAY))
        (titleBox.getChildAt(0) as TextView).setTextColor(Color.WHITE)
        (titleBox.getChildAt(1) as TextView).setTextColor(Color.WHITE)
        header.addView(titleBox, LinearLayout.LayoutParams(0, dp(60), 1f))
        header.addView(textButton("⚙", 25).apply { setOnClickListener { showSettings() } }, LinearLayout.LayoutParams(dp(48), dp(54)))
        root.addView(header)
        clockText = label(currentDateTime(), 14, Color.DKGRAY, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(clockText)

        when (page) {
            "Servomotor" -> buildServoPage(root)
            "Calendario" -> buildCalendarPage(root)
            "Temporizador" -> buildTimerPage(root)
            "Ajustes" -> buildSettingsPage(root)
            else -> buildHomePage(root)
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("Inicio", "Calendario", "Temporizador", "Servomotor", "Ajustes").forEach { item ->
            val b = Button(this).apply {
                text = when (item) { "Inicio" -> "⌂\nInicio"; "Calendario" -> "▦\nCalendario"; "Temporizador" -> "◷\nReloj"; "Servomotor" -> "⚙\nServo"; else -> "⚙\nAjustes" }
                textSize = 10f
                setTextColor(if (item == page) Color.WHITE else Color.rgb(30, 35, 40))
                setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (item == page) Color.rgb(194, 0, 24) else Color.WHITE))
                setOnClickListener { showPage(item) }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        root.addView(nav)
        setContentView(scroll)
        updateStatus()
        updateTemperature()
    }

    private fun buildHomePage(root: LinearLayout) {
        val gaugeWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        gauge = TemperatureGauge(this, setTemp) { value -> setTemp = value; saveValues(); updateTemperature() }
        gaugeWrap.setBackgroundColor(Color.WHITE)
        gaugeWrap.addView(gauge, LinearLayout.LayoutParams(-1, dp(300)))
        label("10 °C                                      70 °C", 14, Color.DKGRAY, true).apply { gravity = Gravity.CENTER }.also { gaugeWrap.addView(it) }
        root.addView(gaugeWrap)
        val status = panelBox()
        status.addView(sectionTitle("ESTADO DEL SISTEMA"))
        boilerStatus = label(if (boilerOn) "● Boiler encendido" else "● Boiler apagado", 17, green, true); status.addView(boilerStatus)
        wifiText = label(if (wifiConnected) "● WiFi conectado" else "● WiFi desconectado", 15, green); status.addView(wifiText)
        onlineText = label("Modo demostración · Control local", 13, Color.LTGRAY); status.addView(onlineText)
        root.addView(status)
        val powerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        powerButton = actionButton(if (boilerOn) "🔥 APAGAR BOILER" else "🔥 ENCENDER BOILER", Color.rgb(190, 45, 55))
        powerButton.setOnClickListener { boilerOn = !boilerOn; saveValues(); updateStatus(); gauge.invalidate() }
        powerRow.addView(powerButton, LinearLayout.LayoutParams(0, dp(56), 1f)); powerRow.addView(space(dp(10)))
        val wifiButton = actionButton("WIFI", blue).apply { setOnClickListener { wifiConnected = !wifiConnected; saveValues(); updateStatus() } }
        powerRow.addView(wifiButton, LinearLayout.LayoutParams(0, dp(56), 1f)); root.addView(powerRow)
    }

    private fun buildServoPage(root: LinearLayout) {
        val panel = panelBox()
        panel.addView(sectionTitle("SERVOMOTOR / VÁLVULA"))
        // Ilustración del servo integrada en el código, no requiere archivo externo.
        panel.addView(ServoIllustration(this), LinearLayout.LayoutParams(-1, dp(220)))
        servoStatus = label("Posición actual: $servoOpen%", 22, white, true).apply { gravity = Gravity.CENTER }
        panel.addView(servoStatus)
        val seek = SeekBar(this).apply { max = 100; progress = servoOpen; progressTintList = android.content.res.ColorStateList.valueOf(blue) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { servoOpen = progress; servoStatus.text = "Posición actual: $servoOpen%"; saveValues() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        panel.addView(seek)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("CERRAR", Color.rgb(55, 75, 100)).apply { setOnClickListener { seek.progress = 0 } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(space(dp(8)))
        row.addView(actionButton("ABRIR", blue).apply { setOnClickListener { seek.progress = 100 } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        panel.addView(row)
        panel.addView(label("Control de demostración. El movimiento real requiere conectar el módulo de servomotor.", 13, Color.LTGRAY))
        root.addView(panel)
    }

    private fun buildCalendarPage(root: LinearLayout) {
        val panel = panelBox()
        panel.addView(sectionTitle("PROGRAMACIÓN SEMANAL"))
        dayNames.forEachIndexed { i, day ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(9), dp(10), dp(9)); setBackgroundColor(Color.rgb(250, 250, 250)) }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(label(day, if (i == 0) 20 else 17, Color.rgb(25, 30, 38), true), LinearLayout.LayoutParams(0, dp(48), 1f))
            val active = Switch(this).apply { isChecked = dayEnabled[i]; text = "Activo"; setTextColor(white); setOnCheckedChangeListener { _, checked -> dayEnabled[i] = checked; saveValues() } }
            top.addView(active); card.addView(top)
            val fields = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val start = timeField(dayStart[i], "Inicio")
            val finish = timeField(dayEnd[i], "Fin")
            val temp = timeField(dayTemp[i].toString(), "°C")
            start.addTextChangedListener(simpleWatcher { dayStart[i] = it; saveValues() })
            finish.addTextChangedListener(simpleWatcher { dayEnd[i] = it; saveValues() })
            temp.addTextChangedListener(simpleWatcher { it.toIntOrNull()?.let { v -> dayTemp[i] = v.coerceIn(10, 70); saveValues() } })
            fields.addView(start, LinearLayout.LayoutParams(0, dp(48), 1f)); fields.addView(space(dp(5)))
            fields.addView(finish, LinearLayout.LayoutParams(0, dp(48), 1f)); fields.addView(space(dp(5)))
            fields.addView(temp, LinearLayout.LayoutParams(0, dp(48), 0.7f)); card.addView(fields)
            val cp = LinearLayout.LayoutParams(-1, -2); cp.setMargins(0, dp(5), 0, dp(5)); panel.addView(card, cp)
        }
        root.addView(panel)
        root.addView(actionButton("GUARDAR PROGRAMACIÓN", Color.rgb(190, 20, 35)).apply { setOnClickListener { saveValues(); WeeklyScheduleReceiver.scheduleAll(this@MainActivity); Toast.makeText(this@MainActivity, "Programación semanal guardada", Toast.LENGTH_SHORT).show() } })
    }

    private fun timeField(value: String, hintText: String): EditText = EditText(this).apply {
        setText(value); hint = hintText; textSize = 15f; setTextColor(Color.rgb(25, 30, 38)); setHintTextColor(Color.GRAY)
        gravity = Gravity.CENTER; setSingleLine(true); setPadding(dp(4), dp(2), dp(4), dp(2))
        inputType = if (hintText == "°C") android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME
        setBackgroundColor(Color.rgb(245, 245, 245))
    }

    private fun simpleWatcher(onText: (String) -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onText(s?.toString().orEmpty()) }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }

    private fun buildTimerPage(root: LinearLayout) {
        val panel = panelBox()
        panel.addView(sectionTitle("TEMPORIZADOR · CUENTA REGRESIVA"))
        timerText = label(formatTime(remainingMs), 56, Color.rgb(194, 0, 24), true).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(22), dp(8), dp(22)); minHeight = dp(160); setBackgroundColor(Color.rgb(250, 250, 250)) }
        panel.addView(timerText, LinearLayout.LayoutParams(-1, dp(190)))
        timerSwitch = Switch(this).apply { text = "Activar temporizador"; textSize = 18f; setTextColor(white); isChecked = timerRunning; setOnCheckedChangeListener { _, checked -> if (checked) startTimer() else stopTimer(reset = false) } }
        panel.addView(timerSwitch)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(actionButton("AJUSTAR", Color.rgb(55, 75, 100)).apply { setOnClickListener { showTimerSettings() } }, LinearLayout.LayoutParams(0, dp(52), 1f)); buttons.addView(space(dp(8)))
        pauseButton = actionButton("PAUSAR", blue).apply { setOnClickListener { if (timerRunning) { stopTimer(false); timerSwitch.isChecked = false; text = "REANUDAR" } else { timerSwitch.isChecked = true; text = "PAUSAR" } } }
        buttons.addView(pauseButton, LinearLayout.LayoutParams(0, dp(52), 1f)); panel.addView(buttons)
        root.addView(panel)
    }

    private fun buildSettingsPage(root: LinearLayout) {
        val p = panelBox(); p.addView(sectionTitle("AJUSTES"))
        p.addView(actionButton("Temperatura objetivo: $setTemp °C", blue).apply { setOnClickListener { showTemperatureSettings() } })
        p.addView(actionButton("Duración temporizador", blue).apply { setOnClickListener { showTimerSettings() } })
        p.addView(actionButton("Estado WiFi: ${if (wifiConnected) "Conectado" else "Desconectado"}", blue).apply { setOnClickListener { wifiConnected = !wifiConnected; saveValues(); showPage("Ajustes") } })
        root.addView(p)
    }

    private fun loadSavedValues() {
        setTemp = prefs.getInt("setTemp", setTemp); servoOpen = prefs.getInt("servoOpen", servoOpen)
        boilerOn = prefs.getBoolean("boilerOn", boilerOn); wifiConnected = prefs.getBoolean("wifiConnected", wifiConnected)
        timerDurationMs = prefs.getLong("timerDuration", timerDurationMs); remainingMs = prefs.getLong("remaining", timerDurationMs)
        for (i in 0..6) { dayStart[i] = prefs.getString("start$i", dayStart[i]) ?: dayStart[i]; dayEnd[i] = prefs.getString("end$i", dayEnd[i]) ?: dayEnd[i]; dayTemp[i] = prefs.getInt("temp$i", dayTemp[i]); dayEnabled[i] = prefs.getBoolean("enabled$i", dayEnabled[i]) }
    }

    private fun saveValues() {
        if (!::prefs.isInitialized) return
        prefs.edit().putInt("setTemp", setTemp).putInt("servoOpen", servoOpen).putBoolean("boilerOn", boilerOn)
            .putBoolean("wifiConnected", wifiConnected).putLong("timerDuration", timerDurationMs).putLong("remaining", remainingMs).apply().also { }
        val edit = prefs.edit()
        for (i in 0..6) edit.putString("start$i", dayStart[i]).putString("end$i", dayEnd[i]).putInt("temp$i", dayTemp[i]).putBoolean("enabled$i", dayEnabled[i])
        edit.apply()
    }

    private fun panelBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setBackgroundColor(panel)
        elevation = dp(3).toFloat()
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, dp(10), 0, dp(8))
        layoutParams = params
    }

    private fun sectionTitle(text: String): TextView =
        label(text, 13, Color.rgb(190, 0, 24), true).apply {
            setPadding(0, 0, 0, dp(8))
        }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
        }

    private fun textButton(text: String, size: Int): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(white)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }

    private fun actionButton(text: String, color: Int): Button =
        Button(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))
        }

    private fun space(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateTemperature() {
        if (::gauge.isInitialized) gauge.setTemperature(setTemp)
        if (::clockText.isInitialized) clockText.text = currentDateTime()
    }

    private fun currentDateTime(): String = java.text.SimpleDateFormat("EEEE, dd MMM yyyy · hh:mm:ss a", Locale("es", "MX")).format(java.util.Date()).replaceFirstChar { it.uppercase() }

    private fun updateStatus() {
        if (::boilerStatus.isInitialized) {
            boilerStatus.text = if (boilerOn) "● Boiler encendido" else "● Boiler apagado"
            boilerStatus.setTextColor(if (boilerOn) green else Color.rgb(255, 90, 90))
            powerButton.text = if (boilerOn) "APAGAR BOILER" else "ENCENDER BOILER"
            powerButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    if (boilerOn) Color.rgb(190, 45, 55) else green
                )
            )
            wifiText.text = if (wifiConnected) "● WiFi conectado" else "● WiFi desconectado"
            wifiText.setTextColor(if (wifiConnected) green else Color.rgb(255, 90, 90))
            onlineText.text = if (wifiConnected) "Modo demostración · Control local" else "Sin conexión · Modo local"
        }
    }

    private fun showMenu() {
        val options = arrayOf("Panel principal", "Temporizador", "Servomotor", "Configuración")
        AlertDialog.Builder(this)
            .setTitle("KalorexSmart")
            .setItems(options) { _, which ->
                when (which) {
                    1 -> showTimerSettings()
                    2 -> showServoInfo()
                    3 -> showSettings()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showSettings() {
        val options = arrayOf(
            "Temperatura objetivo: $setTemp °C",
            "Temporizador: ${timerDurationMs / 60000} minutos",
            "Estado WiFi: ${if (wifiConnected) "Conectado" else "Desconectado"}",
            "Información de la aplicación"
        )
        AlertDialog.Builder(this)
            .setTitle("Configuración")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTemperatureSettings()
                    1 -> showTimerSettings()
                    2 -> {
                        wifiConnected = !wifiConnected
                        updateStatus()
                    }
                    3 -> AlertDialog.Builder(this)
                        .setTitle("KalorexSmart")
                        .setMessage("Interfaz de demostración. Para controlar un boiler real se requiere integrar el módulo WiFi y el sistema de control compatible.")
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showTemperatureSettings() {
        val seek = SeekBar(this).apply {
            max = 40
            progress = setTemp - 10
        }
        val valueText = TextView(this).apply {
            text = "Temperatura: $setTemp °C"
            textSize = 18f
            setTextColor(white)
            gravity = Gravity.CENTER
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(4))
            setBackgroundColor(panel)
            addView(valueText)
            addView(seek)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setTemp = progress + 10
                valueText.text = "Temperatura: $setTemp °C"
                updateTemperature()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        AlertDialog.Builder(this)
            .setTitle("Ajustar temperatura")
            .setView(layout)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showTimerSettings() {
        val edit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText((timerDurationMs / 60000L).toString())
            hint = "Minutos"
        }
        AlertDialog.Builder(this)
            .setTitle("Duración del temporizador")
            .setMessage("Ingresa la duración en minutos:")
            .setView(edit)
            .setPositiveButton("Guardar") { _, _ ->
                val minutes = edit.text.toString().toLongOrNull()
                if (minutes != null && minutes in 1..1440) {
                    timerDurationMs = minutes * 60_000L
                    remainingMs = timerDurationMs
                    timerText.text = formatTime(remainingMs)
                    if (timerRunning) startTimer()
                } else {
                    Toast.makeText(this, "Ingresa de 1 a 1440 minutos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showServoInfo() {
        AlertDialog.Builder(this)
            .setTitle("Servomotor")
            .setMessage("Apertura actual: $servoOpen%\n\nEste control es una demostración visual. Para mover un servomotor real se requiere conexión con el hardware.")
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun startTimer() {
        timer?.cancel()
        if (remainingMs <= 0L) remainingMs = timerDurationMs
        timerRunning = true
        pauseButton.text = "PAUSAR"
        timer = object : CountDownTimer(remainingMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                saveValues()
                timerText.text = formatTime(remainingMs)
            }
            override fun onFinish() {
                remainingMs = 0L
                saveValues()
                timerRunning = false
                timerText.text = "00:00"
                timerSwitch.isChecked = false
                boilerOn = false
                updateStatus()
                Toast.makeText(this@MainActivity, "Temporizador terminado", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun stopTimer(reset: Boolean) {
        timer?.cancel()
        timer = null
        timerRunning = false
        if (reset) remainingMs = timerDurationMs
        saveValues()
        if (::timerText.isInitialized) timerText.text = formatTime(remainingMs)
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    private inner class ServoIllustration(context: android.content.Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f
            p.color = Color.rgb(225, 228, 232); p.style = Paint.Style.FILL
            canvas.drawRoundRect(cx - dp(105), cy - dp(72), cx + dp(105), cy + dp(72), dp(18).toFloat(), dp(18).toFloat(), p)
            p.color = Color.rgb(32, 39, 49)
            canvas.drawRoundRect(cx - dp(82), cy - dp(58), cx + dp(82), cy + dp(58), dp(14).toFloat(), dp(14).toFloat(), p)
            p.color = Color.rgb(12, 18, 25)
            canvas.drawRoundRect(cx - dp(65), cy - dp(43), cx + dp(65), cy + dp(43), dp(10).toFloat(), dp(10).toFloat(), p)
            p.color = Color.rgb(220, 125, 15)
            canvas.drawRoundRect(cx - dp(48), cy - dp(17), cx + dp(48), cy + dp(17), dp(4).toFloat(), dp(4).toFloat(), p)
            p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD; p.textSize = dp(13).toFloat()
            canvas.drawText("SERVO MOTOR", cx, cy + dp(5), p)
            p.color = Color.rgb(205, 145, 25)
            canvas.drawCircle(cx, cy - dp(57), dp(22).toFloat(), p)
            p.color = Color.rgb(70, 75, 85); canvas.drawRoundRect(cx - dp(8), cy - dp(95), cx + dp(8), cy - dp(58), dp(4).toFloat(), dp(4).toFloat(), p)
            p.color = Color.rgb(180, 45, 40); canvas.drawRoundRect(cx + dp(75), cy + dp(35), cx + dp(130), cy + dp(50), dp(6).toFloat(), dp(6).toFloat(), p)
            p.color = Color.rgb(25, 35, 45); canvas.drawRoundRect(cx + dp(118), cy + dp(30), cx + dp(150), cy + dp(56), dp(4).toFloat(), dp(4).toFloat(), p)
        }
    }

    private inner class TemperatureGauge(
        context: android.content.Context,
        initialTemperature: Int,
        private val onTemperatureChanged: (Int) -> Unit
    ) : View(context) {

        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(17).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textAlign = Paint.Align.CENTER
            textSize = dp(14).toFloat()
        }
        private val bounds = RectF()
        private var temperature = initialTemperature
        private var dragging = false

        fun setTemperature(value: Int) {
            temperature = value.coerceIn(10, 50)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f + dp(12)
            val radius = min(width, height) * 0.36f
            bounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

            arcPaint.color = Color.rgb(220, 225, 230)
            canvas.drawArc(bounds, 135f, 270f, false, arcPaint)

            val sweep = ((temperature - 10) / 40f) * 270f
            arcPaint.color = when {
                temperature >= 40 -> Color.rgb(220, 0, 35)
                temperature >= 30 -> Color.rgb(80, 140, 190)
                else -> Color.rgb(100, 160, 190)
            }
            canvas.drawArc(bounds, 135f, sweep, false, arcPaint)

            textPaint.textSize = dp(54).toFloat()
            canvas.drawText("$temperature°", centerX, centerY + dp(8), textPaint)
            textPaint.textSize = dp(18).toFloat()
            canvas.drawText("TEMPERATURA", centerX, centerY + dp(38), textPaint)
            smallPaint.textSize = dp(13).toFloat()
            canvas.drawText("°C", centerX, centerY + dp(60), smallPaint)
            if (boilerOn) {
                val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 105, 20); style = Paint.Style.FILL }
                val fx = centerX
                val fy = centerY + radius * 0.60f
                val path = android.graphics.Path().apply {
                    moveTo(fx, fy + dp(22))
                    cubicTo(fx - dp(28), fy + dp(2), fx - dp(12), fy - dp(20), fx, fy - dp(30))
                    cubicTo(fx + dp(23), fy - dp(5), fx + dp(24), fy + dp(8), fx, fy + dp(22))
                    close()
                }
                canvas.drawPath(path, flamePaint)
                flamePaint.color = Color.YELLOW
                canvas.drawOval(fx - dp(6).toFloat(), fy, fx + dp(6).toFloat(), fy + dp(13), flamePaint)
            }

            val markerAngle = Math.toRadians((135f + sweep).toDouble())
            val markerX = centerX + radius * cos(markerAngle).toFloat()
            val markerY = centerY + radius * sin(markerAngle).toFloat()
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                style = Paint.Style.FILL
            }
            canvas.drawCircle(markerX, markerY, dp(7).toFloat(), markerPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    updateFromTouch(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) updateFromTouch(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    performClick()
                    return true
                }
            }
            return true
        }

        private fun updateFromTouch(x: Float, y: Float) {
            val cx = width / 2f
            val cy = height / 2f + dp(12)
            var angle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
            if (angle < 0f) angle += 360f
            // Map the 270-degree gauge arc (135° through 405°) to 10–50°C.
            var relative = angle - 135f
            if (relative < 0f) relative += 360f
            relative = relative.coerceIn(0f, 270f)
            val value = (10 + (relative / 270f * 40f)).toInt().coerceIn(10, 50)
            temperature = value
            invalidate()
            onTemperatureChanged(value)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}






/** Alarmas semanales persistentes. Requiere declarar este receiver en AndroidManifest.xml. */
class WeeklyScheduleReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("kalorex_settings", Context.MODE_PRIVATE)
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) { scheduleAll(context); return }
        val start = intent?.getBooleanExtra("isStart", true) ?: true
        val target = intent?.getIntExtra("targetTemp", 40) ?: 40
        prefs.edit().putBoolean("boilerOn", start).putInt("scheduledTarget", if (start) target else prefs.getInt("setTemp", 32)).apply()
        scheduleAll(context)
    }

    companion object {
        fun scheduleAll(context: Context) {
            val prefs = context.getSharedPreferences("kalorex_settings", Context.MODE_PRIVATE)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = Calendar.getInstance()
            for (day in 0..6) {
                if (!prefs.getBoolean("enabled$day", true)) continue
                scheduleEvent(context, alarm, prefs, day, prefs.getString("start$day", "06:00") ?: "06:00", true, now)
                scheduleEvent(context, alarm, prefs, day, prefs.getString("end$day", "08:00") ?: "08:00", false, now)
            }
        }

        private fun scheduleEvent(context: Context, alarm: AlarmManager, prefs: android.content.SharedPreferences, day: Int, time: String, isStart: Boolean, now: Calendar) {
            val parts = time.trim().split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
            if (hour !in 0..23 || minute !in 0..59) return
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, day + 1)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                while (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 7)
            }
            val id = day * 2 + if (isStart) 0 else 1
            val intent = Intent(context, WeeklyScheduleReceiver::class.java).apply {
                action = "com.kalorex.demo.WEEKLY_$id"
                putExtra("isStart", isStart)
                putExtra("targetTemp", prefs.getInt("temp$day", 40))
            }
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else alarm.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }
}





