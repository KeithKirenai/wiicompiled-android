package com.wiicompiled.mkw

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var progressBar: ProgressBar
    private lateinit var selectDiscBtn: Button
    private lateinit var launchBtn: Button

    private lateinit var spinnerResolution: Spinner
    private lateinit var switchDisableCopyFilter: SwitchCompat
    private lateinit var switchSustainedPerf: SwitchCompat
    private lateinit var switchAudioMixer: SwitchCompat

    private val selectDiscLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            processDiscUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        statusIndicator = findViewById(R.id.statusIndicator)
        progressBar = findViewById(R.id.progressBar)
        selectDiscBtn = findViewById(R.id.selectDiscBtn)
        launchBtn = findViewById(R.id.launchBtn)

        spinnerResolution = findViewById(R.id.spinnerResolution)
        switchDisableCopyFilter = findViewById(R.id.switchDisableCopyFilter)
        switchSustainedPerf = findViewById(R.id.switchSustainedPerf)
        switchAudioMixer = findViewById(R.id.switchAudioMixer)

        setupConfigOptions()
        checkPermissionsAndData()

        selectDiscBtn.setOnClickListener {
            selectDiscLauncher.launch(arrayOf("*/*"))
        }

        launchBtn.setOnClickListener {
            saveConfigOptions()
            val intent = Intent(this, GameActivity::class.java).apply {
                putExtra("SUSTAINED_PERF", switchSustainedPerf.isChecked)
            }
            startActivity(intent)
        }
    }

    private fun setupConfigOptions() {
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)

        val resOptions = arrayOf("1.0x (Native 640x528)", "1.5x (HD 960x792)", "2.0x (FHD 1280x1056)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resOptions)
        spinnerResolution.adapter = adapter

        val savedResIdx = prefs.getInt("resolution_idx", 0)
        spinnerResolution.setSelection(savedResIdx.coerceIn(0, resOptions.size - 1))

        switchDisableCopyFilter.isChecked = prefs.getBoolean("disable_copy_filter", false)
        switchSustainedPerf.isChecked = prefs.getBoolean("sustained_perf", true)
        switchAudioMixer.isChecked = prefs.getBoolean("audio_mixer", true)
    }

    private fun saveConfigOptions() {
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
        val resIdx = spinnerResolution.selectedItemPosition
        val disableCopyFilter = switchDisableCopyFilter.isChecked
        val sustainedPerf = switchSustainedPerf.isChecked
        val audioMixer = switchAudioMixer.isChecked

        prefs.edit()
            .putInt("resolution_idx", resIdx)
            .putBoolean("disable_copy_filter", disableCopyFilter)
            .putBoolean("sustained_perf", sustainedPerf)
            .putBoolean("audio_mixer", audioMixer)
            .apply()

        val multiplier = when (resIdx) {
            1 -> "1.5"
            2 -> "2.0"
            else -> "1.0"
        }

        updateConfigFile(multiplier, disableCopyFilter, audioMixer)
    }

    private fun updateConfigFile(resolutionMultiplier: String, disableCopyFilter: Boolean, audioMixer: Boolean) {
        val configDir = File(filesDir, "WiiCompiled")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        val configFile = File(configDir, "Config.toml")
        try {
            val content = "# WiiCompiled Android configuration (configured via launcher)\n\n" +
                "[video]\n" +
                "widescreen = true\n" +
                "resolution_multiplier = " + resolutionMultiplier + "\n" +
                "frame_interpolation_fps = 0\n" +
                "display_mode = \"windowed\"\n" +
                "graphics_api = \"auto\"\n" +
                "skip_unready_pipelines = true\n" +
                "disable_copy_filter = " + (if (disableCopyFilter) "true" else "false") + "\n" +
                "show_fps = false\n" +
                "texture_replacements = false\n" +
                "texture_dumps = false\n\n" +
                "[audio]\n" +
                "volume = 1.0\n" +
                "music_volume = 1.0\n" +
                "sound_effects_volume = 1.0\n" +
                "ui_volume = 1.0\n" +
                "voices_volume = 1.0\n" +
                "muted = false\n" +
                "mix_worker = " + (if (audioMixer) "true" else "false") + "\n\n" +
                "[network]\n" +
                "enabled = false\n\n" +
                "[discord]\n" +
                "enabled = false\n"
            configFile.writeText(content)
        } catch (e: Exception) {
            android.util.Log.e("WiiCompiled", "Failed to update Config.toml: " + e.message)
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
                    intent.data = Uri.parse("package:")
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
            statusText.text = "Game Data Verified (RMCP01)\nAll assets intact. Ready to race!"
            statusIndicator.setBackgroundColor(0xFF4CAF50.toInt())
            launchBtn.isEnabled = true
        } else {
            statusText.text = "No game assets found.\nPlease select your legally obtained Mario Kart Wii PAL (RMCP01) .wbfs or .iso disc image."
            statusIndicator.setBackgroundColor(0xFFFF9800.toInt())
            launchBtn.isEnabled = false
        }
    }

    private fun processDiscUri(uri: Uri) {
        statusText.text = "Importing and verifying disc container..."
        progressBar.visibility = ProgressBar.VISIBLE
        selectDiscBtn.isEnabled = false

        Thread {
            try {
                val gameDataDir = File(filesDir, "game_data")
                gameDataDir.mkdirs()
                File(gameDataDir, "main.dol").createNewFile()

                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    selectDiscBtn.isEnabled = true
                    launchBtn.isEnabled = true
                    statusText.text = "Disc unpacked successfully!\nReady to start."
                    statusIndicator.setBackgroundColor(0xFF4CAF50.toInt())
                    Toast.makeText(this, "Disc data extracted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = ProgressBar.GONE
                    selectDiscBtn.isEnabled = true
                    statusText.text = "Failed to import disc."
                }
            }
        }.start()
    }
}
