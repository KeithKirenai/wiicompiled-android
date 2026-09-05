package com.wiicompiled.mkw

import android.content.Context
import android.content.Intent
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
    private lateinit var btnGas: Button
    private lateinit var btnDrift: Button
    private lateinit var btnItem: Button
    private lateinit var btnPause: Button
    private lateinit var steeringArea: View

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                val gameManager = getSystemService(Context.GAME_SERVICE) as? android.app.GameManager
                gameManager?.setGameState(android.app.GameState(false, android.app.GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE))
                android.util.Log.i("WiiCompiled", "GameManager registered MODE_GAMEPLAY_UNINTERRUPTIBLE")
            } catch (e: Throwable) {
                android.util.Log.w("WiiCompiled", "GameManager setGameState error: ${e.message}")
            }
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

        btnGas = findViewById(R.id.btnGas)
        btnDrift = findViewById(R.id.btnDrift)
        btnItem = findViewById(R.id.btnItem)
        btnPause = findViewById(R.id.btnPause)
        steeringArea = findViewById(R.id.steeringArea)

        // Universal hardware scaling for mobile GPUs:
        // Configures the SurfaceView buffer resolution so lower-end GPUs don't choke on 1080p/1440p panels,
        // letting the device's hardware display processor (DPU) scale the surface with zero GPU overhead.
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
        val resIdx = prefs.getInt("resolution_idx", 0)
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val aspect = if (screenH > 0) screenW.toFloat() / screenH.toFloat() else (16f / 9f)

        when (resIdx) {
            0 -> { // Performance (540p)
                val targetH = 540
                val targetW = (targetH * aspect).toInt()
                surfaceView.holder.setFixedSize(targetW, targetH)
                android.util.Log.i("WiiCompiled", "Hardware scaler configured: ${targetW}x${targetH} (540p Performance)")
            }
            1 -> { // Native (720p)
                val targetH = 720
                val targetW = (targetH * aspect).toInt()
                surfaceView.holder.setFixedSize(targetW, targetH)
                android.util.Log.i("WiiCompiled", "Hardware scaler configured: ${targetW}x${targetH} (720p Native)")
            }
            else -> { // HD / FHD full panel resolution
                surfaceView.holder.setSizeFromLayout()
                android.util.Log.i("WiiCompiled", "Full panel resolution configured: ${screenW}x${screenH}")
            }
        }

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

        // Seed dsp_coef.bin from assets
        try {
            val dspBytes = assets.open("dsp_coef.bin").use { it.readBytes() }
            val targets = listOf(
                java.io.File(filesDir, "dsp_coef.bin"),
                java.io.File(java.io.File(filesDir, "WiiCompiled"), "dsp_coef.bin"),
                java.io.File(java.io.File(filesDir, "game_data"), "dsp_coef.bin"),
                java.io.File("/sdcard/Download/wiicompiled_data/dsp_coef.bin")
            )
            for (target in targets) {
                try {
                    val parent = target.parentFile
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs()
                    }
                    if (parent != null && parent.exists() && (!target.exists() || target.length() < 1000L)) {
                        target.writeBytes(dspBytes)
                        android.util.Log.i("WiiCompiled", "Seeded dsp_coef.bin to ${target.absolutePath}")
                    }
                } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            android.util.Log.w("WiiCompiled", "Could not seed dsp_coef.bin from assets: ${e.message}")
        }

        // Seed pre-warmed shader pipeline cache from assets if missing or smaller
        try {
            val cacheDir = java.io.File(filesDir, "WiiCompiled/Cache")
            cacheDir.mkdirs()
            val targetDb = java.io.File(cacheDir, "pipeline_cache.db")
            if (!targetDb.exists() || targetDb.length() < 100000L) {
                assets.open("initial_pipeline_cache.db").use { input ->
                    java.io.FileOutputStream(targetDb).use { output ->
                        input.copyTo(output)
                    }
                }
                android.util.Log.i("WiiCompiled", "Seeded pre-warmed initial_pipeline_cache.db (${targetDb.length()} bytes)")
            }
        } catch (e: Throwable) {
            android.util.Log.w("WiiCompiled", "Could not seed pipeline cache from assets: ${e.message}")
        }

        // Seed wii_bootstrap assets
        try {
            val bootstrapDir = java.io.File(filesDir, "WiiCompiled/wii_bootstrap")
            if (!java.io.File(bootstrapDir, "shared2/wc24").exists()) {
                copyAssetFolder("wii_bootstrap", bootstrapDir)
                android.util.Log.i("WiiCompiled", "Extracted wii_bootstrap assets to ${bootstrapDir.absolutePath}")
            }
        } catch (e: Throwable) {
            android.util.Log.w("WiiCompiled", "Could not extract wii_bootstrap assets: ${e.message}")
        }

        // Check for required Mario Kart Wii game assets
        val hasSdcard = java.io.File("/sdcard/Download/wiicompiled_data/files").isDirectory &&
                        java.io.File("/sdcard/Download/wiicompiled_data/sys/main.dol").exists()
        val hasInternal = java.io.File(filesDir, "game_data/files").isDirectory &&
                          java.io.File(filesDir, "game_data/sys/main.dol").exists()

        if (!hasSdcard && !hasInternal) {
            android.util.Log.e("WiiCompiled", "No game data found on device!")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ Game Assets Missing")
                .setMessage("Mario Kart Wii (RMCP01) game files were not found on this device.\n\nTo play:\n1. Place your extracted game assets into:\n   /sdcard/Download/wiicompiled_data/\n   (with 'files' and 'sys' folders)\n\nOR\n\n2. Return to the Launcher and select your .iso or .wbfs disc image.")
                .setPositiveButton("Open Launcher") { _, _ ->
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .setNegativeButton("Exit") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
            return
        }

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
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
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
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun copyAssetFolder(assetPath: String, targetDir: java.io.File) {
        val list = assets.list(assetPath) ?: return
        if (list.isEmpty()) {
            targetDir.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                java.io.FileOutputStream(targetDir).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            targetDir.mkdirs()
            for (file in list) {
                val subAsset = if (assetPath.isEmpty()) file else "$assetPath/$file"
                copyAssetFolder(subAsset, java.io.File(targetDir, file))
            }
        }
    }
}
