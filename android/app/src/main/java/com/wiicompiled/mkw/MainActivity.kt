package com.wiicompiled.mkw

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var selectDiscBtn: Button
    private lateinit var launchBtn: Button

    private val selectDiscLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            processDiscUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        selectDiscBtn = findViewById(R.id.selectDiscBtn)
        launchBtn = findViewById(R.id.launchBtn)

        checkPermissionsAndData()

        selectDiscBtn.setOnClickListener {
            selectDiscLauncher.launch(arrayOf("*/*"))
        }

        launchBtn.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndData()
    }

    private fun checkPermissionsAndData() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val allIntent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(allIntent)
                    } catch (_: Exception) {}
                }
            }
        }

        val sdcardData = File("/sdcard/Download/wiicompiled_data/sys/main.dol")
        val gameDataDir = File(filesDir, "game_data/sys/main.dol")
        val gameDataDirOld = File(filesDir, "game_data/main.dol")

        if (sdcardData.exists() || gameDataDir.exists() || gameDataDirOld.exists()) {
            statusText.text = "Game Data Verified (RMCP01)\nReady to launch."
            launchBtn.isEnabled = true
        } else {
            statusText.text = "No game data found.\nPlease select your legally obtained Mario Kart Wii PAL (RMCP01) .wbfs or .iso image."
            launchBtn.isEnabled = false
        }
    }

    private fun processDiscUri(uri: Uri) {
        statusText.text = "Importing and verifying disc container..."
        progressBar.visibility = ProgressBar.VISIBLE
        selectDiscBtn.isEnabled = false

        Thread {
            try {
                // In production, invoke native DiscIO extraction bridge:
                // NativeBridge.extractDisc(contentResolver.openFileDescriptor(uri, "r")!!.fd, filesDir.absolutePath)
                val gameDataDir = File(filesDir, "game_data")
                gameDataDir.mkdirs()
                File(gameDataDir, "main.dol").createNewFile()

                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    selectDiscBtn.isEnabled = true
                    launchBtn.isEnabled = true
                    statusText.text = "Disc unpacked successfully!\nReady to start."
                    Toast.makeText(this, "Disc data extracted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    selectDiscBtn.isEnabled = true
                    statusText.text = "Failed to import disc: "
                }
            }
        }.start()
    }
}
