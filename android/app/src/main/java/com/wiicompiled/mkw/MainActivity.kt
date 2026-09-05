package com.wiicompiled.mkw

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.wiicompiled.mkw.extractor.WiiDiscExtractor
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var selectDiscBtn: Button
    private lateinit var launchBtn: Button
    private lateinit var exportLogsBtn: Button

    // Graphics & Engine
    private lateinit var spinnerResolution: Spinner
    private lateinit var switchWidescreen: MaterialSwitch
    private lateinit var switchSkipUnreadyPipelines: MaterialSwitch
    private lateinit var switchDisableCopyFilter: MaterialSwitch
    private lateinit var switchDisableBloom: MaterialSwitch
    private lateinit var switchSustainedPerf: MaterialSwitch
    private lateinit var switchAudioMixer: MaterialSwitch

    // Input Controls
    private lateinit var switchTouchControls: MaterialSwitch
    private lateinit var switchTiltControls: MaterialSwitch

    // Audio Volume & Controls
    private lateinit var textMasterVolume: TextView
    private lateinit var sliderMasterVolume: Slider
    private lateinit var textMusicVolume: TextView
    private lateinit var sliderMusicVolume: Slider
    private lateinit var textSfxVolume: TextView
    private lateinit var sliderSfxVolume: Slider
    private lateinit var switchAudioMuted: MaterialSwitch

    // Features & Network
    private lateinit var spinnerFrameInterpolation: Spinner
    private lateinit var switchRumble: MaterialSwitch
    private lateinit var switchTextureReplacements: MaterialSwitch
    private lateinit var switchShowFps: MaterialSwitch
    private lateinit var switchNetworkEnabled: MaterialSwitch

    private var isExtracting = false

    private val selectDiscLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            processDiscUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyIfAvailable(this)
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

        // Graphics & Engine bindings
        spinnerResolution = findViewById(R.id.spinnerResolution)
        switchWidescreen = findViewById(R.id.switchWidescreen)
        switchSkipUnreadyPipelines = findViewById(R.id.switchSkipUnreadyPipelines)
        switchDisableCopyFilter = findViewById(R.id.switchDisableCopyFilter)
        switchDisableBloom = findViewById(R.id.switchDisableBloom)
        switchSustainedPerf = findViewById(R.id.switchSustainedPerf)
        switchAudioMixer = findViewById(R.id.switchAudioMixer)

        // Input controls
        switchTouchControls = findViewById(R.id.switchTouchControls)
        switchTiltControls = findViewById(R.id.switchTiltControls)

        // Audio controls
        textMasterVolume = findViewById(R.id.textMasterVolume)
        sliderMasterVolume = findViewById(R.id.sliderMasterVolume)
        textMusicVolume = findViewById(R.id.textMusicVolume)
        sliderMusicVolume = findViewById(R.id.sliderMusicVolume)
        textSfxVolume = findViewById(R.id.textSfxVolume)
        sliderSfxVolume = findViewById(R.id.sliderSfxVolume)
        switchAudioMuted = findViewById(R.id.switchAudioMuted)

        // Features & Network
        spinnerFrameInterpolation = findViewById(R.id.spinnerFrameInterpolation)
        switchRumble = findViewById(R.id.switchRumble)
        switchTextureReplacements = findViewById(R.id.switchTextureReplacements)
        switchShowFps = findViewById(R.id.switchShowFps)
        switchNetworkEnabled = findViewById(R.id.switchNetworkEnabled)

        setupConfigOptions()
        checkPermissionsAndData()

        selectDiscBtn.setOnClickListener {
            if (!isExtracting) {
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
            MaterialAlertDialogBuilder(this)
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

        // 1. Resolution
        val resOptions = arrayOf(
            "1.0x (Native 480p/528p)",
            "1.5x (HD 720p/792p)",
            "2.0x (FHD 960p/1056p)",
            "3.0x (QHD 1440p/1584p)"
        )
        val resAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resOptions)
        spinnerResolution.adapter = resAdapter
        val savedResIdx = prefs.getInt("resolution_idx", 0)
        spinnerResolution.setSelection(savedResIdx.coerceIn(0, resOptions.size - 1))

        // 2. Graphics toggles
        switchWidescreen.isChecked = prefs.getBoolean("widescreen", true)
        switchSkipUnreadyPipelines.isChecked = prefs.getBoolean("skip_unready_pipelines", true)
        switchDisableCopyFilter.isChecked = prefs.getBoolean("disable_copy_filter", true)
        switchDisableBloom.isChecked = prefs.getBoolean("disable_bloom", true)
        switchSustainedPerf.isChecked = prefs.getBoolean("sustained_perf", true)
        switchAudioMixer.isChecked = prefs.getBoolean("audio_mixer", true)

        // 3. Input controls
        switchTouchControls.isChecked = prefs.getBoolean("touch_controls", true)
        switchTiltControls.isChecked = prefs.getBoolean("tilt_controls", true)

        // 4. Audio controls
        val masterVol = prefs.getInt("audio_volume", 100)
        sliderMasterVolume.value = masterVol.toFloat()
        textMasterVolume.text = "$masterVol%"
        sliderMasterVolume.addOnChangeListener { _, value, _ ->
            textMasterVolume.text = "${value.toInt()}%"
        }

        val musicVol = prefs.getInt("audio_music_volume", 100)
        sliderMusicVolume.value = musicVol.toFloat()
        textMusicVolume.text = "$musicVol%"
        sliderMusicVolume.addOnChangeListener { _, value, _ ->
            textMusicVolume.text = "${value.toInt()}%"
        }

        val sfxVol = prefs.getInt("audio_sfx_volume", 100)
        sliderSfxVolume.value = sfxVol.toFloat()
        textSfxVolume.text = "$sfxVol%"
        sliderSfxVolume.addOnChangeListener { _, value, _ ->
            textSfxVolume.text = "${value.toInt()}%"
        }

        switchAudioMuted.isChecked = prefs.getBoolean("audio_muted", false)

        // 5. Features & Network
        val hfrOptions = arrayOf(
            "Disabled (Native 60 FPS)",
            "120 FPS Interpolation",
            "180 FPS Interpolation"
        )
        val hfrAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, hfrOptions)
        spinnerFrameInterpolation.adapter = hfrAdapter
        val savedHfrIdx = prefs.getInt("hfr_idx", 0)
        spinnerFrameInterpolation.setSelection(savedHfrIdx.coerceIn(0, hfrOptions.size - 1))

        switchRumble.isChecked = prefs.getBoolean("rumble", true)
        switchTextureReplacements.isChecked = prefs.getBoolean("texture_replacements", false)
        switchShowFps.isChecked = prefs.getBoolean("show_fps", false)
        switchNetworkEnabled.isChecked = prefs.getBoolean("network_enabled", false)

        val autoSaveChecked = android.widget.CompoundButton.OnCheckedChangeListener { _, _ -> saveConfigOptions() }
        switchWidescreen.setOnCheckedChangeListener(autoSaveChecked)
        switchSkipUnreadyPipelines.setOnCheckedChangeListener(autoSaveChecked)
        switchDisableCopyFilter.setOnCheckedChangeListener(autoSaveChecked)
        switchDisableBloom.setOnCheckedChangeListener(autoSaveChecked)
        switchSustainedPerf.setOnCheckedChangeListener(autoSaveChecked)
        switchAudioMixer.setOnCheckedChangeListener(autoSaveChecked)
        switchTouchControls.setOnCheckedChangeListener(autoSaveChecked)
        switchTiltControls.setOnCheckedChangeListener(autoSaveChecked)
        switchAudioMuted.setOnCheckedChangeListener(autoSaveChecked)
        switchRumble.setOnCheckedChangeListener(autoSaveChecked)
        switchTextureReplacements.setOnCheckedChangeListener(autoSaveChecked)
        switchShowFps.setOnCheckedChangeListener(autoSaveChecked)
        switchNetworkEnabled.setOnCheckedChangeListener(autoSaveChecked)

        val autoSaveSelected = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveConfigOptions()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        spinnerResolution.onItemSelectedListener = autoSaveSelected
        spinnerFrameInterpolation.onItemSelectedListener = autoSaveSelected

        val autoSaveSlider = object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                saveConfigOptions()
            }
        }
        sliderMasterVolume.addOnSliderTouchListener(autoSaveSlider)
        sliderMusicVolume.addOnSliderTouchListener(autoSaveSlider)
        sliderSfxVolume.addOnSliderTouchListener(autoSaveSlider)
    }

    private fun saveConfigOptions() {
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)

        val resIdx = spinnerResolution.selectedItemPosition
        val widescreen = switchWidescreen.isChecked
        val skipUnreadyPipelines = switchSkipUnreadyPipelines.isChecked
        val disableCopyFilter = switchDisableCopyFilter.isChecked
        val disableBloom = switchDisableBloom.isChecked
        val sustainedPerf = switchSustainedPerf.isChecked
        val audioMixer = switchAudioMixer.isChecked

        val touchControls = switchTouchControls.isChecked
        val tiltControls = switchTiltControls.isChecked

        val masterVol = sliderMasterVolume.value.toInt()
        val musicVol = sliderMusicVolume.value.toInt()
        val sfxVol = sliderSfxVolume.value.toInt()
        val audioMuted = switchAudioMuted.isChecked

        val hfrIdx = spinnerFrameInterpolation.selectedItemPosition
        val rumble = switchRumble.isChecked
        val textureReplacements = switchTextureReplacements.isChecked
        val showFps = switchShowFps.isChecked
        val networkEnabled = switchNetworkEnabled.isChecked

        prefs.edit()
            .putInt("resolution_idx", resIdx)
            .putBoolean("widescreen", widescreen)
            .putBoolean("skip_unready_pipelines", skipUnreadyPipelines)
            .putBoolean("disable_copy_filter", disableCopyFilter)
            .putBoolean("disable_bloom", disableBloom)
            .putBoolean("sustained_perf", sustainedPerf)
            .putBoolean("audio_mixer", audioMixer)
            .putBoolean("touch_controls", touchControls)
            .putBoolean("tilt_controls", tiltControls)
            .putInt("audio_volume", masterVol)
            .putInt("audio_music_volume", musicVol)
            .putInt("audio_sfx_volume", sfxVol)
            .putBoolean("audio_muted", audioMuted)
            .putInt("hfr_idx", hfrIdx)
            .putBoolean("rumble", rumble)
            .putBoolean("texture_replacements", textureReplacements)
            .putBoolean("show_fps", showFps)
            .putBoolean("network_enabled", networkEnabled)
            .apply()

        val multiplier = when (resIdx) {
            0 -> "1.0"
            1 -> "1.5"
            2 -> "2.0"
            3 -> "3.0"
            else -> "1.0"
        }

        val frameInterpolationFps = when (hfrIdx) {
            1 -> 120
            2 -> 180
            else -> 0
        }

        updateConfigFile(
            resolutionMultiplier = multiplier,
            widescreen = widescreen,
            skipUnreadyPipelines = skipUnreadyPipelines,
            disableCopyFilter = disableCopyFilter,
            disableBloom = disableBloom,
            audioMixer = audioMixer,
            masterVolume = masterVol / 100.0f,
            musicVolume = musicVol / 100.0f,
            sfxVolume = sfxVol / 100.0f,
            audioMuted = audioMuted,
            frameInterpolationFps = frameInterpolationFps,
            rumble = rumble,
            textureReplacements = textureReplacements,
            showFps = showFps,
            networkEnabled = networkEnabled
        )
    }

    private fun updateConfigFile(
        resolutionMultiplier: String,
        widescreen: Boolean,
        skipUnreadyPipelines: Boolean,
        disableCopyFilter: Boolean,
        disableBloom: Boolean,
        audioMixer: Boolean,
        masterVolume: Float,
        musicVolume: Float,
        sfxVolume: Float,
        audioMuted: Boolean,
        frameInterpolationFps: Int,
        rumble: Boolean,
        textureReplacements: Boolean,
        showFps: Boolean,
        networkEnabled: Boolean,
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
                "widescreen = " + (if (widescreen) "true" else "false") + "\n" +
                "resolution_multiplier = " + resolutionMultiplier + "\n" +
                "frame_interpolation_fps = " + frameInterpolationFps + "\n" +
                "display_mode = \"windowed\"\n" +
                "graphics_api = \"vulkan\"\n" +
                "skip_unready_pipelines = " + (if (skipUnreadyPipelines) "true" else "false") + "\n" +
                "disable_copy_filter = " + (if (disableCopyFilter) "true" else "false") + "\n" +
                "disabled_post_processing_paths = " + (if (disableBloom) "16" else "0") + "\n" +
                "show_fps = " + (if (showFps) "true" else "false") + "\n" +
                "texture_replacements = " + (if (textureReplacements) "true" else "false") + "\n" +
                "texture_dumps = false\n\n" +
                "[audio]\n" +
                "volume = " + String.format(java.util.Locale.US, "%.2f", masterVolume) + "\n" +
                "music_volume = " + String.format(java.util.Locale.US, "%.2f", musicVolume) + "\n" +
                "sound_effects_volume = " + String.format(java.util.Locale.US, "%.2f", sfxVolume) + "\n" +
                "ui_volume = 1.0\n" +
                "voices_volume = 1.0\n" +
                "muted = " + (if (audioMuted) "true" else "false") + "\n" +
                "mix_worker = " + (if (audioMixer) "true" else "false") + "\n\n" +
                "[controller]\n" +
                "rumble = " + (if (rumble) "true" else "false") + "\n" +
                "wii_remotes = false\n\n" +
                "[network]\n" +
                "enabled = " + (if (networkEnabled) "true" else "false") + "\n\n" +
                "[discord]\n" +
                "enabled = false\n"
            configFile.writeText(content)
        } catch (e: Exception) {
            android.util.Log.e("WiiCompiled", "Failed to update Config.toml: " + e.message)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isExtracting) {
            saveConfigOptions()
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
        progressBar.visibility = View.VISIBLE
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
                        progressBar.visibility = View.GONE
                        selectDiscBtn.isEnabled = true
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                        if (result.success) {
                            val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
                            prefs.edit().putString("dvd_root", targetDir.absolutePath).apply()
                            saveConfigOptions()

                            checkPermissionsAndData()
                            MaterialAlertDialogBuilder(this)
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
                            MaterialAlertDialogBuilder(this)
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
                    progressBar.visibility = View.GONE
                    selectDiscBtn.isEnabled = true
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    checkPermissionsAndData()

                    MaterialAlertDialogBuilder(this)
                        .setTitle("⚠️ Disc Import Error")
                        .setMessage("An error occurred while opening or reading the selected disc file:\n\n${e.message}\n\nPlease ensure you selected a valid, uncorrupted .wbfs or .iso file.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }
}
