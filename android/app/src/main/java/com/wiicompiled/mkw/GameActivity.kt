package com.wiicompiled.mkw

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity(), SurfaceHolder.Callback, SensorEventListener {

    private lateinit var surfaceView: SurfaceView
    private lateinit var hudFpsText: TextView
    private lateinit var hudStatusText: TextView
    private lateinit var hudTiltText: TextView

    private lateinit var btnGas: Button
    private lateinit var btnDrift: Button
    private lateinit var btnItem: Button
    private lateinit var btnPause: Button
    private lateinit var steeringArea: View

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // HUD performance polling
    private val hudHandler = Handler(Looper.getMainLooper())
    private val hudRunnable: Runnable = object : Runnable {
        override fun run() {
            try {
                hudFpsText.text = nativeGetPerfStats()
            } catch (e: Throwable) {
                // Native may not be ready yet; ignore
            }
            hudHandler.postDelayed(this, 500)
        }
    }

    companion object {
        const val BTN_A = 0
        const val BTN_B = 1
        const val BTN_L = 2
        const val BTN_START = 3

        init {
            try {
                val sdlClass = Class.forName("org.libsdl.app.SDL")
                sdlClass.getMethod("initialize").invoke(null)
            } catch (e: Throwable) {
                android.util.Log.w("WiiCompiled", "SDL.initialize reflection: ${e.message}")
            }
            System.loadLibrary("mkw_android")
        }
    }

    private external fun nativeInit(internalPath: String)
    private external fun nativeSurfaceCreated(surface: Any)
    private external fun nativeSurfaceDestroyed()
    private external fun nativeSetButton(buttonId: Int, isPressed: Boolean)
    private external fun nativeSetStick(stickX: Float, stickY: Float)
    private external fun nativeTiltEvent(angle: Float)
    private external fun nativeTouchEvent(action: Int, x: Float, y: Float, pointerId: Int)
    private external fun nativeGetPerfStats(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val sdlClass = Class.forName("org.libsdl.app.SDL")
            sdlClass.getMethod("setContext", android.app.Activity::class.java).invoke(null, this)
            sdlClass.getMethod("setupJNI").invoke(null)
        } catch (e: Throwable) {
            android.util.Log.w("WiiCompiled", "SDL reflection in onCreate: ${e.message}")
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val sustainedPerf = intent.getBooleanExtra("SUSTAINED_PERF", true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && sustainedPerf) {
            window.setSustainedPerformanceMode(true)
        }

        // Fullscreen immersive mode
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        setContentView(R.layout.activity_game)

        surfaceView = findViewById(R.id.gameSurfaceView)
        hudFpsText = findViewById(R.id.hudFpsText)
        hudStatusText = findViewById(R.id.hudStatusText)
        hudTiltText = findViewById(R.id.hudTiltText)

        btnGas = findViewById(R.id.btnGas)
        btnDrift = findViewById(R.id.btnDrift)
        btnItem = findViewById(R.id.btnItem)
        btnPause = findViewById(R.id.btnPause)
        steeringArea = findViewById(R.id.steeringArea)

        surfaceView.holder.addCallback(this)
        surfaceView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    nativeSetButton(BTN_A, true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    nativeSetButton(BTN_A, false)
                    true
                }
                else -> false
            }
        }

        setupButtonTouch(btnGas, BTN_A)
        setupButtonTouch(btnDrift, BTN_B)
        setupButtonTouch(btnItem, BTN_L)
        setupButtonTouch(btnPause, BTN_START)
        setupSteeringTouch(steeringArea)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        nativeInit(filesDir.absolutePath)
    }

    private fun setupButtonTouch(btn: Button, buttonId: Int) {
        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.88f).scaleY(0.88f).alpha(0.9f).setDuration(50).start()
                    nativeSetButton(buttonId, true)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(50).start()
                    nativeSetButton(buttonId, false)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSteeringTouch(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val halfW = v.width / 2.0f
                    val halfH = v.height / 2.0f
                    val normX = ((event.x - halfW) / halfW).coerceIn(-1.0f, 1.0f)
                    val normY = -((event.y - halfH) / halfH).coerceIn(-1.0f, 1.0f)
                    nativeSetStick(normX, normY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    nativeSetStick(0.0f, 0.0f)
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        hudHandler.post(hudRunnable)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        hudHandler.removeCallbacks(hudRunnable)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        nativeSurfaceCreated(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        nativeSurfaceDestroyed()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Steering tilt calculated from Y/X axis in landscape
            val tiltAngle = (event.values[1] / 9.8f).coerceIn(-1.0f, 1.0f)
            nativeTiltEvent(tiltAngle)
            hudTiltText.text = String.format("Tilt: %.1f°", tiltAngle * 45f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
