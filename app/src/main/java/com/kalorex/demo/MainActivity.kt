          package com.kalorex.demo

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import android.graphics.Typeface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import java.util.Calendar
import java.util.Locale
import android.app.TimePickerDialog

class MainActivity : Activity() {

    private val bg = Color.rgb(3, 12, 25)
    private val panel = Color.rgb(9, 28, 48)
    private val blue = Color.rgb(0, 153, 255)
    private val green = Color.rgb(0, 220, 125)
    private val white = Color.WHITE

    private var setTemp = 32
    private var timerDurationMs = 30 * 60 * 1000L
    private var remainingMs = timerDurationMs
    private var timer: CountDownTimer? = null
    private var timerRunning = false
    private var boilerOn = true
    private var servoOpen = 99
    private var wifiConnected = true
    private var displayedMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private val weeklyPlans = Array(7) { WeeklyPlan("06:00", "07:00", 38, true) }
    private var monthlyCalendarGrid: GridLayout? = null
    private var monthlyTitle: TextView? = null

    private data class WeeklyPlan(
        var startTime: String,
        var endTime: String,
        var temperature: Int,
        var enabled: Boolean
    )

    private lateinit var boilerStatus: TextView
    private lateinit var servoStatus: TextView
    private lateinit var timerText: TextView
    private lateinit var timerSwitch: Switch
    private lateinit var gauge: TemperatureGauge
    private lateinit var wifiText: TextView
    private lateinit var onlineText: TextView
    private lateinit var powerButton: Button
    private lateinit var pauseButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(18))
        }
        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val menu = textButton("☰", 28).apply { setOnClickListener { showMenu() } }
        header.addView(menu, LinearLayout.LayoutParams(dp(48), dp(54)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        titleBox.addView(label("🔥 KALOREX", 27, white, true))
        titleBox.addView(label("Control Inteligente de Boiler", 14, Color.LTGRAY))
        header.addView(titleBox, LinearLayout.LayoutParams(0, dp(60), 1f))
        header.addView(textButton("⚙", 25).apply {
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        root.addView(header)

        val gaugeWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }
        gauge = TemperatureGauge(this, setTemp) { value ->
            setTemp = value
            updateTemperature()
        }
        gaugeWrap.addView(gauge, LinearLayout.LayoutParams(-1, dp(270)))
        val range = label("Rango: 10 °C – 50 °C", 13, Color.LTGRAY)
        range.gravity = Gravity.CENTER
        gaugeWrap.addView(range)
        root.addView(gaugeWrap)

        val statusPanel = panelBox()
        statusPanel.addView(sectionTitle("ESTADO DEL SISTEMA"))
        boilerStatus = label("● Boiler encendido", 17, green, true)
        statusPanel.addView(boilerStatus)
        wifiText = label("● WiFi conectado", 15, green)
        statusPanel.addView(wifiText)
        onlineText = label("Modo demostración · Control local", 13, Color.LTGRAY)
        statusPanel.addView(onlineText)
        root.addView(statusPanel)

        val powerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        powerButton = actionButton("APAGAR BOILER", Color.rgb(190, 45, 55))
        powerButton.setOnClickListener {
            boilerOn = !boilerOn
            updateStatus()
        }
        powerRow.addView(powerButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        powerRow.addView(space(dp(10)))
        val wifiButton = actionButton("WIFI", blue)
        wifiButton.setOnClickListener {
            wifiConnected = !wifiConnected
            updateStatus()
        }
        powerRow.addView(wifiButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(powerRow)

        val servoPanel = panelBox()
        servoPanel.addView(sectionTitle("SERVOMOTOR / VÁLVULA"))
        servoStatus = label("Apertura: $servoOpen%", 18, white, true)
        servoPanel.addView(servoStatus)
        val servoSeek = SeekBar(this).apply {
            max = 100
            progress = servoOpen
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            thumbTintList = android.content.res.ColorStateList.valueOf(white)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    servoOpen = progress
                    servoStatus.text = "Apertura: $servoOpen%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        servoPanel.addView(servoSeek)
        val servoButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val closeServo = actionButton("CERRAR", Color.rgb(55, 75, 100))
        closeServo.setOnClickListener { servoSeek.progress = 0 }
        val openServo = actionButton("ABRIR", blue)
        openServo.setOnClickListener { servoSeek.progress = 100 }
        servoButtons.addView(closeServo, LinearLayout.LayoutParams(0, dp(46), 1f))
        servoButtons.addView(space(dp(8)))
        servoButtons.addView(openServo, LinearLayout.LayoutParams(0, dp(46), 1f))
        servoPanel.addView(servoButtons)
        root.addView(servoPanel)

        val timerPanel = panelBox()
        timerPanel.addView(sectionTitle("TEMPORIZADOR"))
        timerText = label(formatTime(remainingMs), 30, white, true).apply {
            gravity = Gravity.CENTER
        }
        timerPanel.addView(timerText)
        timerSwitch = Switch(this).apply {
            text = "Activar temporizador"
            setTextColor(white)
            isChecked = false
            setOnCheckedChangeListener { _, checked ->
                if (checked) startTimer() else stopTimer(reset = false)
            }
        }
        timerPanel.addView(timerSwitch)
        val timerButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val setTimer = actionButton("AJUSTAR", Color.rgb(55, 75, 100))
        setTimer.setOnClickListener { showTimerSettings() }
        pauseButton = actionButton("PAUSAR", blue)
        pauseButton.setOnClickListener {
            if (timerRunning) {
                stopTimer(reset = false)
                timerSwitch.isChecked = false
                pauseButton.text = "REANUDAR"
            } else {
                timerSwitch.isChecked = true
                pauseButton.text = "PAUSAR"
            }
        }
        timerButtons.addView(setTimer, LinearLayout.LayoutParams(0, dp(46), 1f))
        timerButtons.addView(space(dp(8)))
        timerButtons.addView(pauseButton, LinearLayout.LayoutParams(0, dp(46), 1f))
        timerPanel.addView(timerButtons)
        root.addView(timerPanel)

        val calendarPanel = panelBox()
        calendarPanel.addView(sectionTitle("CALENDARIO MENSUAL"))
        val monthHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prevMonth = textButton("‹", 30).apply {
            setOnClickListener {
                displayedMonth.add(Calendar.MONTH, -1)
                renderMonthlyCalendar()
            }
        }
        monthHeader.addView(prevMonth, LinearLayout.LayoutParams(dp(42), dp(44)))
        monthlyTitle = label("", 17, white, true).apply { gravity = Gravity.CENTER }
        monthHeader.addView(monthlyTitle, LinearLayout.LayoutParams(0, dp(44), 1f))
        val nextMonth = textButton("›", 30).apply {
            setOnClickListener {
                displayedMonth.add(Calendar.MONTH, 1)
                renderMonthlyCalendar()
            }
        }
        monthHeader.addView(nextMonth, LinearLayout.LayoutParams(dp(42), dp(44)))
        calendarPanel.addView(monthHeader)
        monthlyCalendarGrid = GridLayout(this).apply {
            columnCount = 7
            useDefaultMargins = false
        }
        calendarPanel.addView(monthlyCalendarGrid)
        calendarPanel.addView(label("Toca un día para ver o agregar una programación.", 12, Color.LTGRAY))
        root.addView(calendarPanel)

        val weeklyPanel = panelBox()
        weeklyPanel.addView(sectionTitle("PROGRAMACIÓN SEMANAL"))
        weeklyPanel.addView(label("Configura horario y temperatura para cada día.", 13, Color.LTGRAY))
        val dayNames = arrayOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        for (index in dayNames.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, dp(5))
            }
            val plan = weeklyPlans[index]
            val dayLabel = label(dayNames[index], 14, white, true)
            row.addView(dayLabel, LinearLayout.LayoutParams(dp(78), -2))
            val detail = label(planText(plan), 12, Color.LTGRAY)
            row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
            val edit = actionButton("EDITAR", blue).apply {
                setOnClickListener { editWeeklyPlan(index, dayNames[index], detail) }
            }
            row.addView(edit, LinearLayout.LayoutParams(dp(82), dp(42)))
            weeklyPanel.addView(row)
            if (index < dayNames.lastIndex) {
                weeklyPanel.addView(View(this).apply {
                    setBackgroundColor(Color.rgb(30, 52, 73))
                    layoutParams = LinearLayout.LayoutParams(-1, dp(1))
                })
            }
        }
        root.addView(weeklyPanel)

        renderMonthlyCalendar()

        val footer = label("KALOREX SMART · DEMO", 12, Color.GRAY).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        root.addView(footer)

        setContentView(scroll)
        updateTemperature()
        updateStatus()
    }

    private fun panelBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        setBackgroundColor(panel)
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, dp(10), 0, dp(8))
        layoutParams = params
    }

    private fun sectionTitle(text: String): TextView =
        label(text, 13, Color.rgb(130, 185, 225), true).apply {
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
            setTextColor(white)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))
        }

    private fun space(width: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateTemperature() {
        if (::gauge.isInitialized) gauge.setTemperature(setTemp)
    }

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

    private fun planText(plan: WeeklyPlan): String =
        if (plan.enabled) "${plan.startTime}–${plan.endTime} · ${plan.temperature} °C"
        else "Desactivado"

    private fun renderMonthlyCalendar() {
        val grid = monthlyCalendarGrid ?: return
        val title = monthlyTitle ?: return
        val monthName = arrayOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
        title.text = "${monthName[displayedMonth.get(Calendar.MONTH)]} ${displayedMonth.get(Calendar.YEAR)}"
        grid.removeAllViews()
        val weekdays = arrayOf("L", "M", "M", "J", "V", "S", "D")
        weekdays.forEach { day ->
            val cell = label(day, 12, Color.rgb(130, 185, 225), true).apply {
                gravity = Gravity.CENTER
            }
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(34)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        val calendar = displayedMonth.clone() as Calendar
        val offset = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        repeat(offset) {
            grid.addView(View(this), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(42)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        val today = Calendar.getInstance()
        for (day in 1..daysInMonth) {
            val isToday = today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                today.get(Calendar.MONTH) == calendar.get(Calendar.MONTH) &&
                today.get(Calendar.DAY_OF_MONTH) == day
            val button = TextView(this).apply {
                text = day.toString()
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(if (isToday) white else Color.LTGRAY)
                setBackgroundColor(if (isToday) blue else Color.TRANSPARENT)
                setOnClickListener {
                    val picked = displayedMonth.clone() as Calendar
                    picked.set(Calendar.DAY_OF_MONTH, day)
                    showMonthlyDayDialog(picked)
                }
            }
            grid.addView(button, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(42)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
    }

    private fun showMonthlyDayDialog(date: Calendar) {
        val dateText = "%02d/%02d/%04d".format(
            date.get(Calendar.DAY_OF_MONTH),
            date.get(Calendar.MONTH) + 1,
            date.get(Calendar.YEAR)
        )
        AlertDialog.Builder(this)
            .setTitle("Programación del $dateText")
            .setMessage("Puedes crear una programación para este día desde la sección semanal. La vista mensual sirve para consultar y seleccionar fechas.")
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun editWeeklyPlan(index: Int, dayName: String, detail: TextView) {
        val plan = weeklyPlans[index]
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val enabled = Switch(this).apply {
            text = "Programación activa"
            setTextColor(white)
            isChecked = plan.enabled
        }
        val temperatureText = label("Temperatura: ${plan.temperature} °C", 16, white, true).apply {
            gravity = Gravity.CENTER
        }
        val temperature = SeekBar(this).apply {
            max = 40
            progress = plan.temperature - 10
        }
        val startButton = actionButton("Encendido: ${plan.startTime}", blue)
        val endButton = actionButton("Apagado: ${plan.endTime}", blue)
        var start = plan.startTime
        var end = plan.endTime
        startButton.setOnClickListener {
            val parts = start.split(":")
            TimePickerDialog(this, { _, hour, minute ->
                start = "%02d:%02d".format(hour, minute)
                startButton.text = "Encendido: $start"
            }, parts[0].toInt(), parts[1].toInt(), true).show()
        }
        endButton.setOnClickListener {
            val parts = end.split(":")
            TimePickerDialog(this, { _, hour, minute ->
                end = "%02d:%02d".format(hour, minute)
                endButton.text = "Apagado: $end"
            }, parts[0].toInt(), parts[1].toInt(), true).show()
        }
        temperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                temperatureText.text = "Temperatura: ${progress + 10} °C"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        content.addView(enabled)
        content.addView(startButton)
        content.addView(endButton)
        content.addView(temperatureText)
        content.addView(temperature)
        AlertDialog.Builder(this)
            .setTitle("Editar $dayName")
            .setView(content)
            .setPositiveButton("Guardar") { _, _ ->
                plan.enabled = enabled.isChecked
                plan.startTime = start
                plan.endTime = end
                plan.temperature = temperature.progress + 10
                detail.text = planText(plan)
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
                timerText.text = formatTime(remainingMs)
            }
            override fun onFinish() {
                remainingMs = 0L
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

            arcPaint.color = Color.rgb(35, 55, 75)
            canvas.drawArc(bounds, 135f, 270f, false, arcPaint)

            val sweep = ((temperature - 10) / 40f) * 270f
            arcPaint.color = when {
                temperature >= 40 -> Color.rgb(255, 90, 60)
                temperature >= 30 -> blue
                else -> green
            }
            canvas.drawArc(bounds, 135f, sweep, false, arcPaint)

            textPaint.textSize = dp(54).toFloat()
            canvas.drawText("$temperature°", centerX, centerY + dp(8), textPaint)
            textPaint.textSize = dp(18).toFloat()
            canvas.drawText("TEMPERATURA", centerX, centerY + dp(38), textPaint)
            smallPaint.textSize = dp(13).toFloat()
            canvas.drawText("°C", centerX, centerY + dp(60), smallPaint)

            val markerAngle = Math.toRadians((135f + sweep).toDouble())
            val markerX = centerX + radius * cos(markerAngle).toFloat()
            val markerY = centerY + radius * sin(markerAngle).toFloat()
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = white
                style = Paint.Style.FILL
            }
            canvas.drawCircle(markerX, markerY, dp(7).toFloat(), markerPaint)

            if (temperature >= 50) {
                // Llama visual de temperatura máxima (indicador de demostración).
                val flameCenterX = centerX
                val flameBottom = centerY - radius + dp(28)
                val flameHeight = dp(43).toFloat()
                val flameWidth = dp(31).toFloat()
                val outerFlame = Path().apply {
                    moveTo(flameCenterX, flameBottom)
                    cubicTo(
                        flameCenterX - flameWidth, flameBottom - flameHeight * 0.35f,
                        flameCenterX - flameWidth * 0.45f, flameBottom - flameHeight * 0.82f,
                        flameCenterX, flameBottom - flameHeight
                    )
                    cubicTo(
                        flameCenterX + flameWidth * 0.05f, flameBottom - flameHeight * 0.65f,
                        flameCenterX + flameWidth, flameBottom - flameHeight * 0.55f,
                        flameCenterX, flameBottom
                    )
                    close()
                }
                val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(255, 95, 25)
                    style = Paint.Style.FILL
                }
                canvas.drawPath(outerFlame, flamePaint)
                val innerFlame = Path().apply {
                    moveTo(flameCenterX, flameBottom - dp(5))
                    cubicTo(
                        flameCenterX - dp(10), flameBottom - dp(14),
                        flameCenterX - dp(5), flameBottom - dp(25),
                        flameCenterX, flameBottom - dp(31)
                    )
                    cubicTo(
                        flameCenterX + dp(3), flameBottom - dp(20),
                        flameCenterX + dp(11), flameBottom - dp(14),
                        flameCenterX, flameBottom - dp(5)
                    )
                    close()
                }
                flamePaint.color = Color.rgb(255, 220, 60)
                canvas.drawPath(innerFlame, flamePaint)
            }
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
