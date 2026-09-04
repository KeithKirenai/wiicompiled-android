package com.wiicompiled.mkw

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity(), SurfaceHolder.Callback, SensorEventListener {

    private lateinit var surfaceView: SurfaceView
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    companion object {
        init {
            System.loadLibrary("mkw_android")
        }
    }

    private external fun nativeInit(internalPath: String)
    private external fun nativeSurfaceCreated(surface: Any)
    private external fun nativeSurfaceDestroyed()
    private external fun nativeTouchEvent(action: Int, x: Float, y: Float, pointerId: Int)
    private external fun nativeTiltEvent(angle: Float)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen immersive
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        setContentView(surfaceView)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        nativeInit(filesDir.absolutePath)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val pointerId = event.getPointerId(pointerIndex)

        nativeTouchEvent(action, x, y, pointerId)
        return true
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Steering tilt calculated from Y/X axis in landscape
            val tiltAngle = event.values[1] / 9.8f
            nativeTiltEvent(tiltAngle)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
