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
        const val BTN_X = 4
        const val BTN_Y = 5
        const val BTN_R = 6
        const val BTN_ZL = 7
        const val BTN_ZR = 8
        const val BTN_SELECT = 9
        const val BTN_DPAD_UP = 10
        const val BTN_DPAD_DOWN = 11
        const val BTN_DPAD_LEFT = 12
        const val BTN_DPAD_RIGHT = 13

        init {
            for (sdlClass in listOf(
                "org.libsdl.app.SDL",
                "org.libsdl.app.SDLActivity",
                "org.libsdl.app.SDLAudioManager",
                "org.libsdl.app.SDLControllerManager",
                "org.libsdl.app.SDLInputConnection",
                "org.libsdl.app.HIDDeviceManager"
            )) {
                try {
                    Class.forName(sdlClass)
                } catch (e: Throwable) {
                    android.util.Log.w("WiiCompiled", "Preload $sdlClass failed: ${e.message}")
                }
            }
            System.loadLibrary("mkw_android")
            try {
                val sdlClass = Class.forName("org.libsdl.app.SDL")
                sdlClass.getMethod("initialize").invoke(null)
            } catch (e: Throwable) {
                android.util.Log.w("WiiCompiled", "SDL.initialize error: ${e.message}")
            }
        }
    }

    private external fun nativeInit(internalPath: String, resMultiplier: Float)
    private external fun nativeSurfaceCreated(surface: Any)
    private external fun nativeSurfaceDestroyed()
    private external fun nativeSetButton(buttonId: Int, isPressed: Boolean)
    private external fun nativeSetStick(stickX: Float, stickY: Float)
    private external fun nativeTiltEvent(angle: Float)
    private external fun nativeTouchEvent(action: Int, x: Float, y: Float, pointerId: Int)
    private external fun nativeGetPerfStats(): String
    /** Called only when the Activity is truly being destroyed (user exiting the app). */
    private external fun nativeDestroy()

    private var activeResMultiplier: Float = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val sdlClass = Class.forName("org.libsdl.app.SDL")
            sdlClass.getMethod("setContext", android.app.Activity::class.java).invoke(null, this)
            sdlClass.getMethod("setupJNI").invoke(null)
            val sdlCtrlMgrClass = Class.forName("org.libsdl.app.SDLControllerManager")
            sdlCtrlMgrClass.getMethod("initialize").invoke(null)
            android.util.Log.i("WiiCompiled", "SDL.setContext, setupJNI and SDLControllerManager.initialize completed successfully")
        } catch (e: Throwable) {
            android.util.Log.e("WiiCompiled", "SDL setup failed: ${e.message}", e)
        }

        if (intent.getBooleanExtra("SUSTAINED_PERF", true)) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    val pManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    if (pManager?.isSustainedPerformanceModeSupported == true) {
                        window.setSustainedPerformanceMode(true)
                        android.util.Log.i("WiiCompiled", "Sustained performance mode ENABLED")
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w("WiiCompiled", "Could not set sustained performance mode: ${e.message}")
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                val gameManager = getSystemService(android.app.GameManager::class.java)
                gameManager?.setGameState(android.app.GameState(false, android.app.GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE))
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

        val touchOverlayContainer = findViewById<View>(R.id.touchOverlayContainer)

        // Universal hardware scaling for mobile GPUs:
        // Configures the SurfaceView buffer resolution so lower-end GPUs don't choke on 1080p/1440p panels,
        // letting the device's hardware display processor (DPU) scale the surface with zero GPU overhead.
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
        val resIdx = prefs.getInt("resolution_idx", 0)
        val touchControlsEnabled = prefs.getBoolean("touch_controls", true)
        val tiltControlsEnabled = prefs.getBoolean("tilt_controls", true)

        activeResMultiplier = when (resIdx) {
            1 -> 1.5f
            2 -> 2.0f
            3 -> 3.0f
            else -> 1.0f
        }

        touchOverlayContainer.visibility = if (touchControlsEnabled) View.VISIBLE else View.GONE
        android.util.Log.i("WiiCompiled", "Controls configured: touch=$touchControlsEnabled, tilt=$tiltControlsEnabled")

        customKeyMap = ControllerConfig.getMapping(this)
        android.util.Log.i("WiiCompiled", "Loaded ${customKeyMap.size} custom controller mappings")

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val aspect = if (screenH > 0) screenW.toFloat() / screenH.toFloat() else (16f / 9f)

        when (resIdx) {
            0 -> { // Native (480p/528p)
                val targetH = 528
                val targetW = (targetH * aspect).toInt()
                surfaceView.holder.setFixedSize(targetW, targetH)
                android.util.Log.i("WiiCompiled", "Hardware scaler configured: ${targetW}x${targetH} (Native 528p)")
            }
            1 -> { // HD (720p/792p)
                val targetH = 792
                val targetW = (targetH * aspect).toInt()
                surfaceView.holder.setFixedSize(targetW, targetH)
                android.util.Log.i("WiiCompiled", "Hardware scaler configured: ${targetW}x${targetH} (HD 792p, 1.5x)")
            }
            2 -> { // FHD (960p/1056p)
                val targetH = 1056
                val targetW = (targetH * aspect).toInt()
                surfaceView.holder.setFixedSize(targetW, targetH)
                android.util.Log.i("WiiCompiled", "Hardware scaler configured: ${targetW}x${targetH} (FHD 1056p, 2.0x)")
            }
            3 -> { // QHD (1440p/1584p)
                val targetH = 1584
                val targetW = (targetH * aspect).toInt()
                surfaceView.holder.setFixedSize(targetW, targetH)
                android.util.Log.i("WiiCompiled", "Hardware scaler configured: ${targetW}x${targetH} (QHD 1584p, 3.0x)")
            }
            else -> { // Full panel resolution
                surfaceView.holder.setSizeFromLayout()
                android.util.Log.i("WiiCompiled", "Full panel resolution configured: ${screenW}x${screenH}")
            }
        }

        surfaceView.holder.addCallback(this)
        surfaceView.setOnTouchListener(null)
        if (touchControlsEnabled) {
            setupButtonTouch(btnGas, BTN_A)
            setupButtonTouch(btnDrift, BTN_B)
            setupButtonTouch(btnItem, BTN_L)
            setupButtonTouch(btnPause, BTN_START)
            setupSteeringTouch(steeringArea)
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (tiltControlsEnabled) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else {
            accelerometer = null
        }

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

        // Seed pre-warmed shader pipeline cache from assets.
        //
        // The runtime imports the bundled seed (initial_pipeline_cache.db) into the live cache
        // on every start and prunes rows whose config_version is stale, so a bumped config
        // version self-heals. Always refresh that seed file from the APK so the runtime merge
        // sees the current build's rows; only bootstrap the live DB here when it is absent or
        // implausibly small, since the runtime merge handles first-boot and upgrade cases.
        try {
            val cacheDir = java.io.File(filesDir, "WiiCompiled/Cache")
            cacheDir.mkdirs()

            val seedFile = java.io.File(cacheDir, "initial_pipeline_cache.db")
            assets.open("initial_pipeline_cache.db").use { input ->
                java.io.FileOutputStream(seedFile).use { output ->
                    input.copyTo(output)
                }
            }

            val targetDb = java.io.File(cacheDir, "pipeline_cache.db")
            if (!targetDb.exists() || targetDb.length() < 100000L) {
                assets.open("initial_pipeline_cache.db").use { input ->
                    java.io.FileOutputStream(targetDb).use { output ->
                        input.copyTo(output)
                    }
                }
                // The runtime opens the live DB in WAL mode; stale sidecar files from a
                // previous session would otherwise be applied against the freshly seeded file.
                java.io.File(cacheDir, "pipeline_cache.db-wal").delete()
                java.io.File(cacheDir, "pipeline_cache.db-shm").delete()
                android.util.Log.i("WiiCompiled", "Seeded pre-warmed pipeline cache (${targetDb.length()} bytes)")
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

        nativeInit(filesDir.absolutePath, activeResMultiplier)
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
        customKeyMap = ControllerConfig.getMapping(this)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        // Cleanly stop the game/render thread only when the user is truly exiting.
        // Do NOT call this on backgrounding — the thread must survive to allow seamless resume.
        nativeDestroy()
        super.onDestroy()
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

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (event != null && (event.source and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD ||
            event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK)) {
            val mapped = mapKeyCodeToButton(keyCode)
            if (mapped != null) {
                nativeSetButton(mapped, true)
                return true
            }
        }
        // Intercept BACK button (often sent by controller B or back button) so it doesn't close the game
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            nativeSetButton(BTN_B, true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (event != null && (event.source and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD ||
            event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK)) {
            val mapped = mapKeyCodeToButton(keyCode)
            if (mapped != null) {
                nativeSetButton(mapped, false)
                return true
            }
        }
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            nativeSetButton(BTN_B, false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null && (event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK ||
            event.source and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD)) {
            // Left Stick (steering and acceleration)
            var stickX = event.getAxisValue(MotionEvent.AXIS_X)
            var stickY = -event.getAxisValue(MotionEvent.AXIS_Y)

            // D-Pad from Hat axes (common on many Bluetooth / Xbox controllers)
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            if (Math.abs(hatX) > 0.5f) {
                nativeSetButton(if (hatX > 0) BTN_DPAD_RIGHT else BTN_DPAD_LEFT, true)
            } else {
                nativeSetButton(BTN_DPAD_LEFT, false)
                nativeSetButton(BTN_DPAD_RIGHT, false)
            }

            if (Math.abs(hatY) > 0.5f) {
                nativeSetButton(if (hatY > 0) BTN_DPAD_DOWN else BTN_DPAD_UP, true)
            } else {
                nativeSetButton(BTN_DPAD_UP, false)
                nativeSetButton(BTN_DPAD_DOWN, false)
            }

            // Analog triggers (LT / RT on Xbox / PlayStation)
            val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_BRAKE))
            val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).coerceAtLeast(event.getAxisValue(MotionEvent.AXIS_GAS))
            if (lt > 0.4f) {
                nativeSetButton(BTN_ZL, true)
            } else {
                nativeSetButton(BTN_ZL, false)
            }
            if (rt > 0.4f) {
                // RT used as Gas (A) or ZR
                nativeSetButton(BTN_A, true)
            }

            // Apply deadzone for stick
            if (Math.abs(stickX) < 0.15f) stickX = 0f
            if (Math.abs(stickY) < 0.15f) stickY = 0f

            nativeSetStick(stickX.coerceIn(-1.0f, 1.0f), stickY.coerceIn(-1.0f, 1.0f))
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private var customKeyMap: Map<Int, Int> = emptyMap()

    private fun mapKeyCodeToButton(keyCode: Int): Int? {
        // First check user's custom controller configuration
        customKeyMap[keyCode]?.let { return it }

        // Default mapping fallback
        return when (keyCode) {
            // Xbox A / DualShock Cross / Nintendo B
            android.view.KeyEvent.KEYCODE_BUTTON_A -> BTN_A
            // Xbox B / DualShock Circle / Nintendo A
            android.view.KeyEvent.KEYCODE_BUTTON_B -> BTN_B
            // Xbox X / DualShock Square / Nintendo Y
            android.view.KeyEvent.KEYCODE_BUTTON_X -> BTN_X
            // Xbox Y / DualShock Triangle / Nintendo X
            android.view.KeyEvent.KEYCODE_BUTTON_Y -> BTN_Y
            // Left Bumper (LB) / L1 -> Item (L)
            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> BTN_L
            // Right Bumper (RB) / R1 -> Drift / Hop (R)
            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> BTN_R
            // Left Trigger (LT) / L2 -> ZL
            android.view.KeyEvent.KEYCODE_BUTTON_L2 -> BTN_ZL
            // Right Trigger (RT) / R2 -> ZR
            android.view.KeyEvent.KEYCODE_BUTTON_R2 -> BTN_ZR
            // Start / Menu -> Plus / Pause
            android.view.KeyEvent.KEYCODE_BUTTON_START -> BTN_START
            // Select / View / Share -> Minus
            android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> BTN_SELECT
            // D-Pad buttons
            android.view.KeyEvent.KEYCODE_DPAD_UP -> BTN_DPAD_UP
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> BTN_DPAD_DOWN
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> BTN_DPAD_LEFT
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> BTN_DPAD_RIGHT
            else -> null
        }
    }

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
