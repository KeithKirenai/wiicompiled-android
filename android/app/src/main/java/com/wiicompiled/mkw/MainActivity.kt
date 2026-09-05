package com.wiicompiled.mkw

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.wiicompiled.mkw.extractor.WiiDiscExtractor
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var progressBar: ProgressBar
    private lateinit var selectDiscBtn: Button
    private lateinit var launchBtn: Button
    private lateinit var exportLogsBtn: Button

    private lateinit var spinnerResolution: Spinner
    private lateinit var switchDisableCopyFilter: SwitchCompat
    private lateinit var switchDisableBloom: SwitchCompat
    private lateinit var switchSustainedPerf: SwitchCompat
    private lateinit var switchAudioMixer: SwitchCompat
    private lateinit var switchTouchControls: SwitchCompat
    private lateinit var switchTiltControls: SwitchCompat

    private var isExtracting = false

    private val selectDiscLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            processDiscUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTitle = findViewById(R.id.statusTitle)
        statusText = findViewById(R.id.statusText)
        statusIndicator = findViewById(R.id.statusIndicator)
        progressBar = findViewById(R.id.progressBar)
        selectDiscBtn = findViewById(R.id.selectDiscBtn)
        launchBtn = findViewById(R.id.launchBtn)
        exportLogsBtn = findViewById(R.id.exportLogsBtn)
        val btnRemapper = findViewById<Button>(R.id.btnRemapper)

        spinnerResolution = findViewById(R.id.spinnerResolution)
        switchDisableCopyFilter = findViewById(R.id.switchDisableCopyFilter)
        switchDisableBloom = findViewById(R.id.switchDisableBloom)
        switchSustainedPerf = findViewById(R.id.switchSustainedPerf)
        switchAudioMixer = findViewById(R.id.switchAudioMixer)
        switchTouchControls = findViewById(R.id.switchTouchControls)
        switchTiltControls = findViewById(R.id.switchTiltControls)

        setupConfigOptions()
        checkPermissionsAndData()

        selectDiscBtn.setOnClickListener {
            if (!isExtracting) {
                // Opens the system document picker allowing the user to select either a .wbfs or .iso
                selectDiscLauncher.launch(arrayOf("*/*"))
            }
        }

        launchBtn.setOnClickListener {
            launchGame()
        }

        btnRemapper.setOnClickListener {
            startActivity(Intent(this, RemapperActivity::class.java))
        }

        exportLogsBtn.setOnClickListener {
            exportLogs()
        }
    }

    private fun exportLogs() {
        try {
            val logsDir = File(filesDir, "WiiCompiled/Logs")
            val cacheLogs = File(cacheDir, "diagnostic_logs.txt")
            val sb = java.lang.StringBuilder()
            sb.append("=== WiiCompiled Android Diagnostic Log ===\n")
            sb.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})\n")
            sb.append("Hardware: ${android.os.Build.HARDWARE}, Board: ${android.os.Build.BOARD}\n")
            sb.append("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}\n\n")

            // Append internal engine logs
            if (logsDir.exists() && logsDir.isDirectory) {
                val files = logsDir.listFiles()?.sortedByDescending { it.lastModified() }
                if (!files.isNullOrEmpty()) {
                    sb.append("--- Native Logs Found (${files.size}) ---\n")
                    for (logFile in files.take(3)) {
                        sb.append("\n=== File: ${logFile.name} (${logFile.length()} bytes) ===\n")
                        try {
                            sb.append(logFile.readText())
                        } catch (re: Exception) {
                            sb.append("[Error reading file: ${re.message}]\n")
                        }
                    }
                } else {
                    sb.append("--- No native log files found in ${logsDir.absolutePath} ---\n")
                }
            } else {
                sb.append("--- Logs directory does not exist yet ---\n")
            }

            // Append logcat dump of the app
            sb.append("\n--- Logcat (Filtered for WiiCompiled/MKW) ---\n")
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "500"))
                val lines = process.inputStream.bufferedReader().readLines()
                val filtered = lines.filter {
                    it.contains("WiiCompiled", ignoreCase = true) ||
                    it.contains("mkw", ignoreCase = true) ||
                    it.contains("aurora", ignoreCase = true) ||
                    it.contains("AndroidRuntime", ignoreCase = true)
                }
                sb.append(filtered.takeLast(200).joinToString("\n"))
            } catch (le: Exception) {
                sb.append("[Could not dump logcat: ${le.message}]\n")
            }

            cacheLogs.writeText(sb.toString())

            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                cacheLogs
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "WiiCompiled Android Crash Logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Logs"))
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Log Export Error")
                .setMessage("Could not export logs: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun launchGame() {
        saveConfigOptions()
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("SUSTAINED_PERF", switchSustainedPerf.isChecked)
        }
        startActivity(intent)
    }

    private fun setupConfigOptions() {
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)

        val resOptions = arrayOf(
            "1.0x (Native 480p/528p)",
            "1.5x (HD 720p/792p)",
            "2.0x (FHD 960p/1056p)"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resOptions)
        spinnerResolution.adapter = adapter

        val savedResIdx = prefs.getInt("resolution_idx", 0)
        spinnerResolution.setSelection(savedResIdx.coerceIn(0, resOptions.size - 1))

        switchDisableCopyFilter.isChecked = prefs.getBoolean("disable_copy_filter", true)
        switchDisableBloom.isChecked = prefs.getBoolean("disable_bloom", true)
        switchSustainedPerf.isChecked = prefs.getBoolean("sustained_perf", true)
        switchAudioMixer.isChecked = prefs.getBoolean("audio_mixer", true)
        switchTouchControls.isChecked = prefs.getBoolean("touch_controls", true)
        switchTiltControls.isChecked = prefs.getBoolean("tilt_controls", true)
    }

    private fun saveConfigOptions() {
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
        val resIdx = spinnerResolution.selectedItemPosition
        val disableCopyFilter = switchDisableCopyFilter.isChecked
        val disableBloom = switchDisableBloom.isChecked
        val sustainedPerf = switchSustainedPerf.isChecked
        val audioMixer = switchAudioMixer.isChecked
        val touchControls = switchTouchControls.isChecked
        val tiltControls = switchTiltControls.isChecked

        prefs.edit()
            .putInt("resolution_idx", resIdx)
            .putBoolean("disable_copy_filter", disableCopyFilter)
            .putBoolean("disable_bloom", disableBloom)
            .putBoolean("sustained_perf", sustainedPerf)
            .putBoolean("audio_mixer", audioMixer)
            .putBoolean("touch_controls", touchControls)
            .putBoolean("tilt_controls", tiltControls)
            .apply()

        val multiplier = when (resIdx) {
            0 -> "1.0"
            1 -> "1.5"
            2 -> "2.0"
            else -> "1.0"
        }

        updateConfigFile(multiplier, disableCopyFilter, disableBloom, audioMixer)
    }

    private fun updateConfigFile(
        resolutionMultiplier: String,
        disableCopyFilter: Boolean,
        disableBloom: Boolean,
        audioMixer: Boolean,
        customDvdRoot: String? = null
    ) {
        val configDir = File(filesDir, "WiiCompiled")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        val configFile = File(configDir, "Config.toml")
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
        val dvdRoot = customDvdRoot ?: prefs.getString("dvd_root", null)

        try {
            val content = "# WiiCompiled Android configuration (configured via launcher)\n\n" +
                (if (!dvdRoot.isNullOrEmpty()) "[paths]\ndvd_root = \"$dvdRoot\"\n\n" else "") +
                "[video]\n" +
                "widescreen = true\n" +
                "resolution_multiplier = " + resolutionMultiplier + "\n" +
                "frame_interpolation_fps = 0\n" +
                "display_mode = \"windowed\"\n" +
                "graphics_api = \"vulkan\"\n" +
                "skip_unready_pipelines = true\n" +
                "disable_copy_filter = " + (if (disableCopyFilter) "true" else "false") + "\n" +
                "disabled_post_processing_paths = " + (if (disableBloom) "16" else "0") + "\n" +
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
        if (!isExtracting) {
            checkPermissionsAndData()
        }
    }

    private var hasPromptedStoragePermission = false

    private fun checkPermissionsAndData() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager() && !hasPromptedStoragePermission) {
                hasPromptedStoragePermission = true
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

        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
        val configuredRoot = prefs.getString("dvd_root", null)?.let { File(it) }

        val isConfiguredValid = configuredRoot != null &&
                File(configuredRoot, "sys/main.dol").exists() &&
                File(configuredRoot, "files").isDirectory &&
                (File(configuredRoot, "files").list()?.isNotEmpty() == true)

        val sdcardSys = File("/sdcard/Download/wiicompiled_data/sys/main.dol")
        val sdcardFiles = File("/sdcard/Download/wiicompiled_data/files")
        val internalSys = File(filesDir, "game_data/sys/main.dol")
        val internalFiles = File(filesDir, "game_data/files")

        val hasSdcardData = sdcardSys.exists() && sdcardFiles.isDirectory && (sdcardFiles.list()?.isNotEmpty() == true)
        val hasInternalData = internalSys.exists() && internalFiles.isDirectory && (internalFiles.list()?.isNotEmpty() == true)

        if (isConfiguredValid || hasSdcardData || hasInternalData) {
            val activeDir = when {
                isConfiguredValid -> configuredRoot!!.absolutePath
                hasSdcardData -> "/sdcard/Download/wiicompiled_data"
                else -> File(filesDir, "game_data").absolutePath
            }
            statusTitle.text = "Ready"
            statusTitle.setTextColor(0xFF4CAF50.toInt())
            statusText.text = "Disc Data Verified\n$activeDir"
            statusIndicator.setBackgroundColor(0xFF4CAF50.toInt())
            launchBtn.isEnabled = true
            selectDiscBtn.text = "SELECT DIFFERENT DISC (.WBFS / .ISO)"
        } else {
            statusTitle.text = "Disc Image Required"
            statusTitle.setTextColor(0xFFFF9800.toInt())
            statusIndicator.setBackgroundColor(0xFFFF9800.toInt())
            launchBtn.isEnabled = false
            statusText.text = "No disc data found.\nPlease select a valid Wii disc image (.wbfs / .iso)."
            selectDiscBtn.text = "SELECT DISC IMAGE (.WBFS / .ISO)"
        }
    }

    private fun getExtractionTargetDirectory(): File {
        val downloadDir = File("/sdcard/Download")
        return if (downloadDir.exists() && downloadDir.canWrite()) {
            File(downloadDir, "wiicompiled_data")
        } else {
            File(filesDir, "game_data")
        }
    }

    private fun processDiscUri(uri: Uri) {
        if (isExtracting) return
        isExtracting = true

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progressBar.isIndeterminate = false
        progressBar.max = 100
        progressBar.progress = 0
        progressBar.visibility = ProgressBar.VISIBLE
        selectDiscBtn.isEnabled = false
        launchBtn.isEnabled = false

        statusTitle.text = "Installing Game Assets..."
        statusTitle.setTextColor(0xFF2196F3.toInt())
        statusIndicator.setBackgroundColor(0xFF2196F3.toInt())
        statusText.text = "Opening disc image (.wbfs / .iso)..."

        val targetDir = getExtractionTargetDirectory()

        Thread {
            try {
                val source = WiiDiscExtractor.openDiscSource(this, uri)
                source.use { discSource ->
                    val result = WiiDiscExtractor.extract(
                        source = discSource,
                        destDirectory = targetDir,
                        isCancelled = { isFinishing || isDestroyed }
                    ) { status, percent ->
                        runOnUiThread {
                            progressBar.progress = percent
                            statusText.text = "$status ($percent%)"
                        }
                    }

                    runOnUiThread {
                        isExtracting = false
                        progressBar.visibility = ProgressBar.GONE
                        selectDiscBtn.isEnabled = true
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                        if (result.success) {
                            val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
                            prefs.edit().putString("dvd_root", targetDir.absolutePath).apply()
                            saveConfigOptions()

                            checkPermissionsAndData()
                            AlertDialog.Builder(this)
                                .setTitle("✅ Installation Complete")
                                .setMessage(
                                    "Mario Kart Wii (RMCP01) unpacked successfully!\n\n" +
                                    "• Format: WBFS/ISO container\n" +
                                    "• Files unpacked: ${result.extractedFilesCount}\n" +
                                    "• Total size: ${String.format("%.2f", result.totalBytesExtracted / 1024.0 / 1024.0 / 1024.0)} GB\n" +
                                    "• System DOL: ${if (result.dolVerified) "Verified (Match)" else "Extracted"}\n" +
                                    "• Static Relay: ${if (result.relVerified) "Verified (Match)" else "Extracted"}\n\n" +
                                    "Installed to:\n${targetDir.absolutePath}"
                                )
                                .setPositiveButton("Start Game") { _, _ ->
                                    launchGame()
                                }
                                .setNegativeButton("Close", null)
                                .show()
                        } else {
                            checkPermissionsAndData()
                            AlertDialog.Builder(this)
                                .setTitle("⚠️ Installation Error")
                                .setMessage(result.errorMessage ?: "Failed to extract disc.")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isExtracting = false
                    progressBar.visibility = ProgressBar.GONE
                    selectDiscBtn.isEnabled = true
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    checkPermissionsAndData()

                    AlertDialog.Builder(this)
                        .setTitle("⚠️ Disc Import Error")
                        .setMessage("An error occurred while opening or reading the selected disc file:\n\n${e.message}\n\nPlease ensure you selected a valid, uncorrupted .wbfs or .iso file.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }
}
