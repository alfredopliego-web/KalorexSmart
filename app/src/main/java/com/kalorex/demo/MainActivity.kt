package com.kalorex.demo

import android.app.Activity
import android.app.AlertDialog
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
import android.graphics.Typeface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
            fillViewport = true
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
