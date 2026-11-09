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

    private var themeMode = 0 // 0 = Industrial Pro, 1 = Calorex claro
    private var bg = Color.rgb(3, 12, 25)
    private var panel = Color.rgb(9, 28, 48)
    private var blue = Color.rgb(0, 153, 255)
    private var green = Color.rgb(0, 220, 125)
    private var white = Color.WHITE

    private fun applyThemeColors() {
        if (themeMode == 0) {
            bg = Color.rgb(10, 10, 12)
            panel = Color.rgb(27, 28, 31)
            blue = Color.rgb(220, 25, 35)
            green = Color.rgb(30, 220, 65)
            white = Color.WHITE
        } else {
            bg = Color.rgb(247, 247, 245)
            panel = Color.WHITE
            blue = Color.rgb(190, 20, 48)
            green = Color.rgb(30, 155, 75)
            white = Color.rgb(45, 48, 52)
        }
    }

    private var setTemp = 32
    private var timerDurationMs = 30 * 60 * 1000L
    private var remainingMs = timerDurationMs
    private var timer: CountDownTimer? = null
    private var timerRunning = false
    private var boilerOn = true
    private lateinit var prefs: android.content.SharedPreferences
    private var actualTemp = 22f
    private var manualOverride = false
    private var automationEnabled = true
    private var targetTemperature = setTemp
    private var lastScheduledActive: Boolean? = null
    private val coolingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val coolingRunnable = object : Runnable {
        override fun run() {
            if (boilerOn) return
            if (setTemp > 10) {
                setTemp = (setTemp - 1).coerceAtLeast(10)
                updateTemperature()
                coolingHandler.postDelayed(this, 2_000L)
            }
        }
    }

    private fun beginGradualCooling() {
        coolingHandler.removeCallbacks(coolingRunnable)
        if (!boilerOn && setTemp > 10) {
            coolingHandler.postDelayed(coolingRunnable, 2_000L)
        }
    }

    private fun stopGradualCooling() {
        coolingHandler.removeCallbacks(coolingRunnable)
    }
    private val heatingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var heatingRunning = false
    private val heatingRunnable = object : Runnable {
        override fun run() {
            if (!boilerOn || !automationEnabled || setTemp >= targetTemperature) {
                heatingRunning = false
                return
            }
            setTemp = (setTemp + 1).coerceAtMost(targetTemperature)
            updateTemperature()
            if (setTemp < targetTemperature) {
                heatingHandler.postDelayed(this, 2_000L)
            } else {
                heatingRunning = false
            }
        }
    }

    private fun beginGradualHeating(target: Int) {
        targetTemperature = target.coerceIn(10, 50)
        // Schedule checking runs every second; do not restart this 2-second timer each pass.
        if (boilerOn && automationEnabled && setTemp < targetTemperature && !heatingRunning) {
            heatingRunning = true
            heatingHandler.postDelayed(heatingRunnable, 2_000L)
        } else if (setTemp >= targetTemperature) {
            stopGradualHeating()
        }
    }

    private fun stopGradualHeating() {
        heatingHandler.removeCallbacks(heatingRunnable)
        heatingRunning = false
    }
    private var servoOpen = 99
    private var wifiConnected = true
    private var displayedMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    private val weeklyPlans = Array(7) { WeeklyPlan("06:00", "07:00", 38, true) }
    private var flameView: FlameView? = null
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
    private lateinit var homeBoilerStatus: TextView
    private lateinit var pauseButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("kalorex_smart", MODE_PRIVATE)
        loadPreferences()
        applyThemeColors()
        buildScreen()
    }

    private lateinit var pageContainer: FrameLayout
    private lateinit var navHome: TextView
    private lateinit var navCalendar: TextView
    private lateinit var navSchedule: TextView
    private lateinit var navSettings: TextView
    private var currentPage = "home"
    private val scheduleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scheduleCheck = object : Runnable {
        override fun run() {
            applyScheduleForCurrentTime()
            scheduleHandler.postDelayed(this, 1_000L)
        }
    }

    override fun onResume() {
        super.onResume()
        scheduleHandler.removeCallbacks(scheduleCheck)
        updateStatus()
        updateTemperature()
        scheduleHandler.post(scheduleCheck)
    }

    override fun onPause() {
        scheduleHandler.removeCallbacks(scheduleCheck)
        super.onPause()
    }

    private fun buildScreen() {
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        header.addView(textButton("☰", 25).apply {
            setOnClickListener { showMenu() }
        }, LinearLayout.LayoutParams(dp(44), dp(50)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        titleBox.addView(label("🔥 KALOREX", 30, white, true))
        header.addView(titleBox, LinearLayout.LayoutParams(0, dp(54), 1f))
        header.addView(textButton("⚙", 24).apply {
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(dp(44), dp(50)))
        main.addView(header)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, dp(5), 0)
            setBackgroundColor(panel)
        }
        navHome = navItem("⌂", "Inicio") { showPage("home") }
        navCalendar = navItem("📅", "Calendario") { showPage("calendar") }
        navSchedule = navItem("⏰", "Programación") { showPage("schedule") }
        navSettings = navItem("⚙", "Ajustes") { showSettings() }
        listOf(navHome, navCalendar, navSchedule, navSettings).forEach {
            nav.addView(it, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        main.addView(nav)

        pageContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        main.addView(pageContainer)
        setContentView(main)

        showPage("home")
        updateStatus()
        applyScheduleForCurrentTime()
    }

    private fun navItem(symbol: String, caption: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = "$symbol\n$caption"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setOnClickListener { action() }
        }

    private fun showPage(page: String) {
        currentPage = page
        pageContainer.removeAllViews()
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(0), dp(8), dp(5))
        }
        when (page) {
            "calendar" -> buildCalendarPage(content)
            "schedule" -> buildSchedulePage(content)
            "servo" -> buildServoPage(content)
            else -> buildHomePage(content)
        }
        scroll.addView(content)
        pageContainer.addView(scroll)
        val activeColor = blue
        navHome.setTextColor(if (page == "home") activeColor else Color.LTGRAY)
        navCalendar.setTextColor(if (page == "calendar") activeColor else Color.LTGRAY)
        navSchedule.setTextColor(if (page == "schedule") activeColor else Color.LTGRAY)
        navSettings.setTextColor(Color.LTGRAY)
    }

    private fun buildHomePage(root: LinearLayout) {
        // Reloj + estado WiFi en la parte superior, estilo panel de control.
        val clockWifi = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val clockCard = panelBox()
        clockCard.orientation = LinearLayout.HORIZONTAL
        clockCard.gravity = Gravity.CENTER_VERTICAL
        clockCard.addView(label("◷", 28, blue, true))
        val clockText = label("", 17, white, true)
        clockCard.addView(clockText, LinearLayout.LayoutParams(0, -2, 1f))
        val wifiCard = panelBox()
        wifiCard.gravity = Gravity.CENTER
        wifiCard.addView(label("⌁", 25, green, true))
        wifiCard.addView(label(if (wifiConnected) "En línea" else "Sin conexión", 12,
            if (wifiConnected) green else Color.RED, true))
        clockWifi.addView(clockCard, LinearLayout.LayoutParams(0, dp(52), 1.5f))
        clockWifi.addView(space(dp(8)))
        clockWifi.addView(wifiCard, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(clockWifi)

        // Flama animada: se muestra únicamente cuando el boiler está encendido.
        flameView = FlameView(this).apply { visibility = if (boilerOn) View.VISIBLE else View.GONE }
        root.addView(flameView, LinearLayout.LayoutParams(-1, dp(105)))

        val clockHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val clockUpdate = object : Runnable {
            override fun run() {
                if (currentPage != "home") return
                val now = Calendar.getInstance()
                clockText.text = "%02d:%02d:%02d\n%s".format(
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND),
                    arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")[now.get(Calendar.DAY_OF_WEEK)-1]
                )
                clockHandler.postDelayed(this, 1_000L)
            }
        }
        clockUpdate.run()

        // Temperatura y objetivo.
        val gaugePanel = panelBox()
        gaugePanel.gravity = Gravity.CENTER
        gaugePanel.addView(sectionTitle("TEMPERATURA ACTUAL"))
        gauge = TemperatureGauge(this, setTemp) { value ->
            setTemp = value.coerceIn(10, 50)
            updateTemperature()
        }
        gaugePanel.addView(gauge, LinearLayout.LayoutParams(-1, dp(190)))
        gaugePanel.addView(label("Objetivo programado: ${targetTemperature} °C", 15, blue, true).apply {
            gravity = Gravity.CENTER
        })
        root.addView(gaugePanel)

        // Tarjeta de programación con interruptor en el panel principal.
        val automationPanel = panelBox()
        val automationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        automationRow.addView(label("⏰  Programación automática", 16, white, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val automationSwitch = Switch(this).apply {
            isChecked = automationEnabled
            setOnCheckedChangeListener { _, checked ->
                automationEnabled = checked
                savePreferences()
                if (checked) {
                    manualOverride = false
                    applyScheduleForCurrentTime()
                    Toast.makeText(this@MainActivity, "Programación activada", Toast.LENGTH_SHORT).show()
                } else {
                    manualOverride = true
                    stopGradualHeating()
                    Toast.makeText(this@MainActivity, "Programación desconectada", Toast.LENGTH_SHORT).show()
                }
            }
        }
        automationRow.addView(automationSwitch)
        automationPanel.addView(automationRow)
        automationPanel.addView(label(
            if (automationEnabled) "✓ Activa · Próximo evento: ${nextScheduleDescription()}"
            else "Desconectada · Control manual",
            13, if (automationEnabled) green else Color.LTGRAY
        ))
        root.addView(automationPanel)

        // Encendido y estado del boiler.
        val powerPanel = panelBox()
        val powerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val boilerInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        boilerInfo.addView(label("♨  Boiler", 15, white, true))
        homeBoilerStatus = label(if (boilerOn) "ENCENDIDO" else "APAGADO", 14,
            if (boilerOn) green else Color.RED, true)
        boilerInfo.addView(homeBoilerStatus)
        powerRow.addView(boilerInfo, LinearLayout.LayoutParams(0, -2, 1f))
        powerButton = actionButton(if (boilerOn) "🔥  ⏻ APAGAR" else "⏻  ENCENDER",
            if (boilerOn) Color.rgb(30, 155, 75) else Color.rgb(190, 45, 55))
        powerButton.setOnClickListener {
            boilerOn = !boilerOn
            manualOverride = true
            savePreferences()
            if (boilerOn) {
                stopGradualCooling()
                if (automationEnabled) beginGradualHeating(targetTemperature)
            } else {
                stopGradualHeating()
                beginGradualCooling()
            }
            updateStatus()
            showPage("home")
        }
        powerRow.addView(powerButton, LinearLayout.LayoutParams(dp(128), dp(44)))
        powerPanel.addView(powerRow)
        root.addView(powerPanel)

        // El servomotor se controla desde su propia pestaña.
        val servoShortcut = panelBox()
        servoShortcut.addView(actionButton("♧  ABRIR CONTROL DE SERVOMOTOR", blue).apply {
            setOnClickListener { showPage("servo") }
        }, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(servoShortcut)

        // Estado del sistema al pie del dashboard.
        val statusPanel = panelBox()
        statusPanel.addView(sectionTitle("ESTADO DEL SISTEMA"))
        boilerStatus = label(if (boilerOn) "● Boiler encendido" else "● Boiler apagado", 15,
            if (boilerOn) green else Color.RED, true)
        statusPanel.addView(boilerStatus)
        wifiText = label(if (wifiConnected) "● WiFi conectado" else "● WiFi desconectado", 14,
            if (wifiConnected) green else Color.RED)
        statusPanel.addView(wifiText)
        onlineText = label(
            if (boilerOn && setTemp < targetTemperature) "Calentando · Incremento gradual"
            else if (!boilerOn) "Enfriamiento gradual"
            else "Temperatura estable",
            13, Color.LTGRAY
        )
        statusPanel.addView(onlineText)
        root.addView(statusPanel)
    }

    private fun buildServoPage(root: LinearLayout) {
        val servoPanel = panelBox()
        servoPanel.addView(sectionTitle("CONTROL DE SERVOMOTOR (GAS)"))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label("Apertura de válvula", 16, white, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        servoStatus = label("$servoOpen%", 18, green, true)
        heading.addView(servoStatus)
        servoPanel.addView(heading)
        val seek = SeekBar(this).apply {
            max = 100
            progress = servoOpen
            progressTintList = android.content.res.ColorStateList.valueOf(green)
            thumbTintList = android.content.res.ColorStateList.valueOf(white)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    servoOpen = progress
                    servoStatus.text = "$servoOpen%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        servoPanel.addView(seek)
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        controls.addView(actionButton("CERRAR", panel).apply { setOnClickListener { seek.progress = 0 } },
            LinearLayout.LayoutParams(dp(100), dp(42)))
        controls.addView(space(dp(8)))
        controls.addView(actionButton("ABRIR", blue).apply { setOnClickListener { seek.progress = 100 } },
            LinearLayout.LayoutParams(dp(100), dp(42)))
        servoPanel.addView(controls)
        servoPanel.addView(label("Control de demostración: no acciona un servomotor físico sin conexión al hardware.", 12, Color.LTGRAY))
        root.addView(servoPanel)
    }

    private fun nextScheduleDescription(): String {
        val now = Calendar.getInstance()
        var nearest: Calendar? = null
        var nearestPlan: WeeklyPlan? = null
        for (offset in 0..7) {
            val candidate = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val index = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                else -> 6
            }
            val plan = weeklyPlans[index]
            if (!plan.enabled) continue
            val time = plan.startTime.split(":")
            candidate.set(Calendar.HOUR_OF_DAY, time[0].toInt())
            candidate.set(Calendar.MINUTE, time[1].toInt())
            if (candidate.after(now) && (nearest == null || candidate.before(nearest))) {
                nearest = candidate
                nearestPlan = plan
            }
        }
        val event = nearest ?: return "Sin próximos eventos programados"
        val date = "%02d/%02d/%04d".format(
            event.get(Calendar.DAY_OF_MONTH), event.get(Calendar.MONTH) + 1,
            event.get(Calendar.YEAR)
        )
        val plan = nearestPlan!!
        return "Próximo evento: $date · ${plan.startTime} · ${plan.temperature} °C"
    }

    private fun buildCalendarPage(root: LinearLayout) {
        val calendarPanel = panelBox()
        calendarPanel.addView(sectionTitle("CALENDARIO MENSUAL"))
        val monthHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        monthHeader.addView(textButton("‹", 30).apply {
            setOnClickListener {
                displayedMonth.add(Calendar.MONTH, -1)
                renderMonthlyCalendar()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(44)))
        monthlyTitle = label("", 17, white, true).apply { gravity = Gravity.CENTER }
        monthHeader.addView(monthlyTitle, LinearLayout.LayoutParams(0, dp(44), 1f))
        monthHeader.addView(textButton("›", 30).apply {
            setOnClickListener {
                displayedMonth.add(Calendar.MONTH, 1)
                renderMonthlyCalendar()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(44)))
        calendarPanel.addView(monthHeader)
        monthlyCalendarGrid = GridLayout(this).apply {
            columnCount = 7
            useDefaultMargins = false
        }
        calendarPanel.addView(monthlyCalendarGrid)
        calendarPanel.addView(label("Toca un día para consultar la programación.", 12, Color.LTGRAY))
        root.addView(calendarPanel)
        renderMonthlyCalendar()
    }

    private fun buildSchedulePage(root: LinearLayout) {
        val weeklyPanel = panelBox()
        weeklyPanel.addView(sectionTitle("PROGRAMACIÓN SEMANAL"))
        weeklyPanel.addView(label("Configura encendido, apagado y temperatura por día.", 13, Color.LTGRAY))
        weeklyPanel.addView(actionButton(if (automationEnabled) "⏰ PROGRAMACIÓN CONECTADA" else "⏰ CONECTAR PROGRAMACIÓN", green).apply {
            setOnClickListener {
                automationEnabled = !automationEnabled
                savePreferences()
                if (automationEnabled) {
                    manualOverride = false
                    applyScheduleForCurrentTime()
                } else {
                    manualOverride = true
                    stopGradualHeating()
                }
                showPage("schedule")
                Toast.makeText(
                    this@MainActivity,
                    if (automationEnabled) "Programación automática activada" else "Programación desconectada",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, LinearLayout.LayoutParams(-1, dp(44)))
        val names = arrayOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        names.forEachIndexed { index, name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, dp(5))
            }
            val plan = weeklyPlans[index]
            row.addView(label(name, 14, white, true), LinearLayout.LayoutParams(dp(78), -2))
            val detail = label(planText(plan), 12, Color.LTGRAY)
            row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(actionButton("EDITAR", blue).apply {
                setOnClickListener { editWeeklyPlan(index, name, detail) }
            }, LinearLayout.LayoutParams(dp(82), dp(42)))
            weeklyPanel.addView(row)
            if (index < names.lastIndex) {
                weeklyPanel.addView(View(this).apply {
                    setBackgroundColor(Color.rgb(30, 52, 73))
                    layoutParams = LinearLayout.LayoutParams(-1, dp(1))
                })
            }
        }
        root.addView(weeklyPanel)
    }

    private fun panelBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(7), dp(10), dp(7))
        setBackgroundColor(panel)
        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, dp(4), 0, dp(3))
        layoutParams = params
    }

    private fun sectionTitle(text: String): TextView =
        label(text, 13, Color.rgb(130, 185, 225), true).apply {
            setPadding(0, 0, 0, dp(3))
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


    private fun loadPreferences() {
        setTemp = prefs.getInt("setTemp", setTemp).coerceIn(10, 50)
        targetTemperature = prefs.getInt("targetTemperature", setTemp).coerceIn(10, 50)
        boilerOn = prefs.getBoolean("boilerOn", boilerOn)
        automationEnabled = prefs.getBoolean("automationEnabled", automationEnabled)
        themeMode = prefs.getInt("themeMode", themeMode).coerceIn(0, 1)
        wifiConnected = prefs.getBoolean("wifiConnected", wifiConnected)
        servoOpen = prefs.getInt("servoOpen", servoOpen).coerceIn(0, 100)
        timerDurationMs = prefs.getLong("timerDurationMs", timerDurationMs).coerceIn(60_000L, 86_400_000L)
        remainingMs = prefs.getLong("remainingMs", timerDurationMs).coerceIn(0L, timerDurationMs)
        for (i in weeklyPlans.indices) {
            val plan = weeklyPlans[i]
            plan.startTime = prefs.getString("plan${i}_start", plan.startTime) ?: plan.startTime
            plan.endTime = prefs.getString("plan${i}_end", plan.endTime) ?: plan.endTime
            plan.temperature = prefs.getInt("plan${i}_temp", plan.temperature).coerceIn(10, 50)
            plan.enabled = prefs.getBoolean("plan${i}_enabled", plan.enabled)
        }
    }

    private fun savePreferences() {
        if (!::prefs.isInitialized) return
        val editor = prefs.edit()
            .putInt("setTemp", setTemp)
            .putInt("targetTemperature", targetTemperature)
            .putBoolean("boilerOn", boilerOn)
            .putBoolean("automationEnabled", automationEnabled)
            .putInt("themeMode", themeMode)
            .putBoolean("wifiConnected", wifiConnected)
            .putInt("servoOpen", servoOpen)
            .putLong("timerDurationMs", timerDurationMs)
            .putLong("remainingMs", remainingMs)
        weeklyPlans.forEachIndexed { i, plan ->
            editor.putString("plan${i}_start", plan.startTime)
                .putString("plan${i}_end", plan.endTime)
                .putInt("plan${i}_temp", plan.temperature)
                .putBoolean("plan${i}_enabled", plan.enabled)
        }
        editor.apply()
    }

    private inner class FlameView(context: android.content.Context) : View(context) {
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var phase = 0f
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 55, 10); style = Paint.Style.FILL }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 180, 20); style = Paint.Style.FILL }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 245, 190); style = Paint.Style.FILL }
        private val animation = object : Runnable {
            override fun run() {
                phase += 0.18f
                invalidate()
                handler.postDelayed(this, 45L)
            }
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(animation) }
        override fun onDetachedFromWindow() { handler.removeCallbacks(animation); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!boilerOn) return
            val cx = width / 2f
            val base = height * 0.88f
            val scale = min(width, height) * 0.42f
            canvas.drawRoundRect(RectF(cx - scale * 1.25f, base - dp(8), cx + scale * 1.25f, base + dp(2)), dp(6).toFloat(), dp(6).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(65, 70, 75) })
            val sway = sin(phase.toDouble()).toFloat() * scale * 0.10f
            val flame = Path().apply {
                moveTo(cx - scale * 0.65f, base)
                cubicTo(cx - scale * 0.95f, base - scale * 0.55f, cx - scale * 0.25f + sway, base - scale * 0.72f, cx + sway, base - scale * 1.25f)
                cubicTo(cx + scale * 0.58f, base - scale * 0.70f, cx + scale * 0.95f, base - scale * 0.45f, cx + scale * 0.62f, base)
                close()
            }
            canvas.drawPath(flame, outerPaint)
            val inner = Path().apply {
                moveTo(cx - scale * 0.38f, base)
                cubicTo(cx - scale * 0.42f, base - scale * 0.45f, cx + sway, base - scale * 0.50f, cx + scale * 0.10f + sway, base - scale * 0.88f)
                cubicTo(cx + scale * 0.50f, base - scale * 0.40f, cx + scale * 0.42f, base - scale * 0.30f, cx + scale * 0.36f, base)
                close()
            }
            canvas.drawPath(inner, innerPaint)
            canvas.drawOval(RectF(cx - scale * 0.20f, base - scale * 0.42f, cx + scale * 0.20f, base - scale * 0.05f), corePaint)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateTemperature() {
        actualTemp = setTemp.toFloat()
        if (::gauge.isInitialized) {
            gauge.setTemperature(setTemp)
            gauge.setTargetTemperature(targetTemperature)
        }
        flameView?.visibility = if (boilerOn) View.VISIBLE else View.GONE
        flameView?.invalidate()
        savePreferences()
    }

    private fun updateStatus() {
        if (::boilerStatus.isInitialized) {
            boilerStatus.text = if (boilerOn) "● Boiler encendido" else "● Boiler apagado"
            boilerStatus.setTextColor(if (boilerOn) green else Color.rgb(255, 90, 90))
            if (::homeBoilerStatus.isInitialized) {
                homeBoilerStatus.text = if (boilerOn) "ENCENDIDO" else "APAGADO"
                homeBoilerStatus.setTextColor(if (boilerOn) green else Color.rgb(255, 90, 90))
            }
            powerButton.text = if (boilerOn) "🔥  ⏻ APAGAR" else "⏻ ENCENDER"
            powerButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    if (boilerOn) green else Color.rgb(190, 45, 55)
                )
            )
            wifiText.text = if (wifiConnected) "● WiFi conectado" else "● WiFi desconectado"
            wifiText.setTextColor(if (wifiConnected) green else Color.rgb(255, 90, 90))
            onlineText.text = if (boilerOn && setTemp < targetTemperature) "Calentando · Incremento gradual"
                else if (!boilerOn) "Enfriamiento gradual" else "Temperatura estable"
        }
        flameView?.visibility = if (boilerOn) View.VISIBLE else View.GONE
        flameView?.invalidate()
        savePreferences()
    }

    private fun applyScheduleForCurrentTime() {
        val now = Calendar.getInstance()
        val dayIndex = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }
        val plan = weeklyPlans[dayIndex]

        if (!automationEnabled) return

        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startParts = plan.startTime.split(":")
        val endParts = plan.endTime.split(":")
        val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
        val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()

        val scheduledActive = plan.enabled && if (endMinutes >= startMinutes) {
            nowMinutes >= startMinutes && nowMinutes < endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }

        // A manual selection remains in effect until the schedule changes state
        // (for example, its programmed start or stop time is reached).
        if (lastScheduledActive != null && scheduledActive != lastScheduledActive) {
            manualOverride = false
        }
        lastScheduledActive = scheduledActive

        if (manualOverride) return

        val wasOn = boilerOn
        boilerOn = scheduledActive
        if (scheduledActive) {
            stopGradualCooling()
            // Always refresh the target and ensure the gradual-heating task is running.
            // This also handles the case where the boiler was already ON when the schedule starts.
            targetTemperature = plan.temperature.coerceIn(10, 50)
            if (!wasOn || setTemp < targetTemperature) {
                beginGradualHeating(targetTemperature)
            } else {
                stopGradualHeating()
            }
        } else if (wasOn) {
            stopGradualHeating()
            beginGradualCooling()
        }
        updateStatus()
        updateTemperature()
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
                savePreferences()
                manualOverride = false
                applyScheduleForCurrentTime()
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
                    2 -> showPage("servo")
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
            "Tema de la aplicación: ${if (themeMode == 0) "Industrial Pro" else "Calorex claro"}",
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
                    3 -> showThemeSettings()
                    4 -> AlertDialog.Builder(this)
                        .setTitle("KalorexSmart")
                        .setMessage("Interfaz de demostración. Para controlar un boiler real se requiere integrar el módulo WiFi y el sistema de control compatible.")
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showThemeSettings() {
        val choices = arrayOf("Industrial Pro · Negro y rojo", "Calorex claro · Rojo y blanco")
        AlertDialog.Builder(this)
            .setTitle("Tema de la aplicación")
            .setSingleChoiceItems(choices, themeMode) { dialog, which ->
                themeMode = which
                savePreferences()
                applyThemeColors()
                dialog.dismiss()
                buildScreen()
                Toast.makeText(this, "Tema aplicado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
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
                savePreferences()
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
                    savePreferences()
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
        savePreferences()
        timer?.cancel()
        scheduleHandler.removeCallbacks(scheduleCheck)
        coolingHandler.removeCallbacks(coolingRunnable)
        heatingHandler.removeCallbacks(heatingRunnable)
        super.onDestroy()
    }

    private inner class TemperatureGauge(
        context: android.content.Context,
        initialTemperature: Int,
        private val onTemperatureChanged: (Int) -> Unit
    ) : View(context) {

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 200, 203)
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 19, 21)
            style = Paint.Style.FILL
        }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(48, 49, 52)
            style = Paint.Style.FILL
        }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val decimalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 205, 210)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val bounds = RectF()
        private var temperature = initialTemperature.coerceIn(0, 70)
        private var target = targetTemperature.coerceIn(0, 70)
        private var dragging = false

        fun setTemperature(value: Int) {
            temperature = value.coerceIn(0, 70)
            invalidate()
        }

        fun setTargetTemperature(value: Int) {
            target = value.coerceIn(0, 70)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width / 2f
            val cy = height / 2f + dp(8)
            val radius = min(width, height) * 0.39f
            bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

            // Soft raised circular face and the pale outer track, inspired by the Calorex dial.
            canvas.drawCircle(cx, cy, radius + dp(11).toFloat(), shadowPaint)
            canvas.drawCircle(cx, cy, radius + dp(5).toFloat(), centerPaint)

            trackPaint.strokeWidth = dp(15).toFloat()
            trackPaint.color = Color.rgb(65, 67, 70)
            canvas.drawArc(bounds, 135f, 270f, false, trackPaint)

            val sweep = (temperature / 70f) * 270f
            arcPaint.strokeWidth = dp(15).toFloat()
            arcPaint.color = when {
                temperature >= 50 -> Color.rgb(255, 20, 35)
                temperature >= 35 -> Color.rgb(255, 85, 15)
                else -> Color.rgb(255, 170, 20)
            }
            canvas.drawArc(bounds, 135f, sweep, false, arcPaint)

            // Graduations around the dial.
            val tickRadiusOuter = radius + dp(23)
            val tickRadiusInner = radius + dp(17)
            for (i in 0..70) {
                val angle = Math.toRadians((135f + i / 70f * 270f).toDouble())
                val major = i % 10 == 0
                val r1 = if (major) tickRadiusInner - dp(2) else tickRadiusInner
                val r2 = tickRadiusOuter
                tickPaint.strokeWidth = if (major) dp(2).toFloat() else dp(1).toFloat()
                canvas.drawLine(
                    cx + r1 * cos(angle).toFloat(),
                    cy + r1 * sin(angle).toFloat(),
                    cx + r2 * cos(angle).toFloat(),
                    cy + r2 * sin(angle).toFloat(),
                    tickPaint
                )
            }

            valuePaint.textSize = dp(57).toFloat()
            canvas.drawText(temperature.toString(), cx - dp(4), cy + dp(13), valuePaint)
            decimalPaint.textSize = dp(24).toFloat()
            canvas.drawText("°", cx + dp(35), cy - dp(2), decimalPaint)
            decimalPaint.textSize = dp(17).toFloat()
            canvas.drawText(".0", cx + dp(38), cy + dp(20), decimalPaint)

            captionPaint.textSize = dp(11).toFloat()
            canvas.drawText("WATER TEMPERATURE", cx, cy + dp(48), captionPaint)

            // End labels.
            captionPaint.textSize = dp(12).toFloat()
            canvas.drawText("0°", cx - radius * 0.82f, cy + radius * 0.98f, captionPaint)
            canvas.drawText("70°", cx + radius * 0.82f, cy + radius * 0.98f, captionPaint)


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
            val cy = height / 2f + dp(8)
            var angle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
            if (angle < 0f) angle += 360f
            var relative = angle - 135f
            if (relative < 0f) relative += 360f
            relative = relative.coerceIn(0f, 270f)
            val value = (relative / 270f * 70f).toInt().coerceIn(0, 70)
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
