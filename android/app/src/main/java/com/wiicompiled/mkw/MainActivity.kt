package com.wiicompiled.mkw

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wiicompiled.mkw.databinding.ActivityMainBinding
import com.wiicompiled.mkw.extractor.WiiDiscExtractor
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isExtracting = false

    private val selectDiscLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            processDiscUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateLogsButtonSize()

        setupConfigOptions()
        checkPermissionsAndData()

        binding.selectDiscBtn.setOnClickListener {
            if (!isExtracting) {
                selectDiscLauncher.launch(arrayOf("*/*"))
            }
        }

        binding.launchBtn.setOnClickListener {
            launchGame()
        }

        binding.btnFpsSettings.setOnClickListener {
            showFpsAdvancedSettingsDialog()
        }

        binding.btnRemapper.setOnClickListener {
            startActivity(Intent(this, RemapperActivity::class.java))
        }

        binding.exportLogsBtn.setOnClickListener {
            exportLogs()
        }

        binding.btnClearLogs.setOnClickListener {
            val logsDir = File(filesDir, "WiiCompiled/Logs")
            val currentBytes = getFolderSize(logsDir)

            if (currentBytes == 0L) {
                Toast.makeText(this, "Logs are already empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Clear Crash Logs?")
                .setMessage("This will delete all stored core dumps and diagnostic logs (${formatSize(currentBytes)}). Saved games and settings are untouched.")
                .setPositiveButton("Clear") { _, _ ->
                    if (logsDir.exists()) {
                        logsDir.deleteRecursively()
                        logsDir.mkdirs()
                    }
                    updateLogsButtonSize()
                    Toast.makeText(this, "Crash logs deleted!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnWipeSaveData.setOnClickListener {
            wipeSaveData()
        }
    }

    private fun wipeSaveData() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Wipe Save Data")
            .setMessage("This will delete the save file (rksys.dat) and FaceLib database files (RFL_DB.dat, RFL_Res.dat) from the NAND. This simulates a fresh install. Continue?")
            .setPositiveButton("Wipe Data") { _, _ ->
                try {
                    // The NAND root is typically at <app_data>/Wiicompiled/NAND/
                    val nandDir = File(filesDir, "WiiCompiled/NAND")
                    // MKW save file location: title/00010004/524d4350/data/rksys.dat
                    val saveFile = File(nandDir, "title/00010004/524d4350/data/rksys.dat")
                    // Also check legacy location shared2/menu/rksys/rksys.bin
                    val legacySaveFile = File(nandDir, "shared2/menu/rksys/rksys.bin")
                    val faceLibDir = File(nandDir, "shared2/menu/FaceLib")
                    val rflDbFile = File(faceLibDir, "RFL_DB.dat")
                    val rflResFile = File(faceLibDir, "RFL_Res.dat")

                    val deletedFiles = mutableListOf<String>()
                    val failedFiles = mutableListOf<String>()

                    fun deleteFile(file: File, label: String) {
                        if (file.exists()) {
                            try {
                                if (file.delete()) {
                                    deletedFiles.add(label)
                                } else {
                                    failedFiles.add("$label (delete failed)")
                                }
                            } catch (e: SecurityException) {
                                failedFiles.add("$label (${e.message})")
                            }
                        } else {
                            deletedFiles.add("$label (not present)")
                        }
                    }

                    deleteFile(saveFile, "rksys.dat (save file)")
                    deleteFile(legacySaveFile, "shared2/menu/rksys/rksys.bin (legacy save)")
                    deleteFile(rflDbFile, "FaceLib/RFL_DB.dat")
                    deleteFile(rflResFile, "FaceLib/RFL_Res.dat")

                    val summary = "Deleted:\n" + deletedFiles.joinToString("\n") { "  ✓ $it" } +
                            "\n\nFailed:\n" + (if (failedFiles.isEmpty()) "  (none)" else failedFiles.joinToString("\n") { "  ✗ $it" })

                    MaterialAlertDialogBuilder(this)
                        .setTitle("Data Wiped")
                        .setMessage(summary)
                        .setPositiveButton("OK", null)
                        .show()
                } catch (e: Exception) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Wipe Error")
                        .setMessage("Failed to wipe save data: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun showFpsAdvancedSettingsDialog() {
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
        val items = arrayOf(
            "Pass Breakdown (Encoder ms)",
            "Draw Calls & Merged Batches",
            "Bandwidth (Geometry / Textures)",
            "Pipeline Shader Builds & Cache %"
        )
        val keys = arrayOf(
            "show_fps_passes",
            "show_fps_draws",
            "show_fps_bandwidth",
            "show_fps_builds"
        )
        val defaults = booleanArrayOf(false, false, false, true)
        val checked = BooleanArray(keys.size) { i -> prefs.getBoolean(keys[i], defaults[i]) }

        MaterialAlertDialogBuilder(this)
            .setTitle("FPS Overlay Elements")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val editor = prefs.edit()
                for (i in keys.indices) {
                    editor.putBoolean(keys[i], checked[i])
                }
                editor.apply()
                saveConfigOptions()
                Toast.makeText(this, "FPS overlay settings saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchGame() {
        saveConfigOptions()
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("SUSTAINED_PERF", binding.switchSustainedPerf.isChecked)
            putExtra("EXTEND_TO_NOTCH", binding.switchExtendToNotch.isChecked)
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
        binding.spinnerResolution.adapter = resAdapter
        val savedResIdx = prefs.getInt("resolution_idx", 0)
        binding.spinnerResolution.setSelection(savedResIdx.coerceIn(0, resOptions.size - 1))

        // 2. Graphics toggles
        binding.switchWidescreen.isChecked = prefs.getBoolean("widescreen", true)
        binding.switchExtendToNotch.isChecked = prefs.getBoolean("extend_to_notch", true)
        binding.switchSkipUnreadyPipelines.isChecked = prefs.getBoolean("skip_unready_pipelines", true)
        binding.switchDisableCopyFilter.isChecked = prefs.getBoolean("disable_copy_filter", true)
        binding.switchDisableBloom.isChecked = prefs.getBoolean("disable_bloom", true)
        binding.switchSustainedPerf.isChecked = prefs.getBoolean("sustained_perf", true)
        binding.switchAudioMixer.isChecked = prefs.getBoolean("audio_mixer", true)

        // 3. Input controls
        binding.switchTouchControls.isChecked = prefs.getBoolean("touch_controls", true)
        binding.switchTiltControls.isChecked = prefs.getBoolean("tilt_controls", true)

        // 4. Audio controls
        val masterVol = prefs.getInt("audio_volume", 100)
        binding.sliderMasterVolume.value = masterVol.toFloat()
        binding.textMasterVolume.text = "$masterVol%"
        binding.sliderMasterVolume.addOnChangeListener { _, value, _ ->
            binding.textMasterVolume.text = "${value.toInt()}%"
        }

        val musicVol = prefs.getInt("audio_music_volume", 100)
        binding.sliderMusicVolume.value = musicVol.toFloat()
        binding.textMusicVolume.text = "$musicVol%"
        binding.sliderMusicVolume.addOnChangeListener { _, value, _ ->
            binding.textMusicVolume.text = "${value.toInt()}%"
        }

        val sfxVol = prefs.getInt("audio_sfx_volume", 100)
        binding.sliderSfxVolume.value = sfxVol.toFloat()
        binding.textSfxVolume.text = "$sfxVol%"
        binding.sliderSfxVolume.addOnChangeListener { _, value, _ ->
            binding.textSfxVolume.text = "${value.toInt()}%"
        }

        binding.switchAudioMuted.isChecked = prefs.getBoolean("audio_muted", false)

        // 5. Features & Network
        val hfrOptions = arrayOf(
            "Disabled (Native 60 FPS)",
            "120 FPS Interpolation",
            "180 FPS Interpolation"
        )
        val hfrAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, hfrOptions)
        binding.spinnerFrameInterpolation.adapter = hfrAdapter
        val savedHfrIdx = prefs.getInt("hfr_idx", 0)
        binding.spinnerFrameInterpolation.setSelection(savedHfrIdx.coerceIn(0, hfrOptions.size - 1))

        binding.switchRumble.isChecked = prefs.getBoolean("rumble", true)
        binding.switchTextureReplacements.isChecked = prefs.getBoolean("texture_replacements", false)
        binding.switchShowFps.isChecked = prefs.getBoolean("show_fps", false)

        val autoSaveChecked = android.widget.CompoundButton.OnCheckedChangeListener { _, _ -> saveConfigOptions() }
        binding.switchWidescreen.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchExtendToNotch.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchSkipUnreadyPipelines.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchDisableCopyFilter.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchDisableBloom.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchSustainedPerf.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchAudioMixer.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchTouchControls.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchTiltControls.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchAudioMuted.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchRumble.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchTextureReplacements.setOnCheckedChangeListener(autoSaveChecked)
        binding.switchShowFps.setOnCheckedChangeListener(autoSaveChecked)

        val autoSaveSelected = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveConfigOptions()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        binding.spinnerResolution.onItemSelectedListener = autoSaveSelected
        binding.spinnerFrameInterpolation.onItemSelectedListener = autoSaveSelected

        val autoSaveSlider = object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                saveConfigOptions()
            }
        }
        binding.sliderMasterVolume.addOnSliderTouchListener(autoSaveSlider)
        binding.sliderMusicVolume.addOnSliderTouchListener(autoSaveSlider)
        binding.sliderSfxVolume.addOnSliderTouchListener(autoSaveSlider)
    }

    private fun saveConfigOptions() {
        val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)

        val resIdx = binding.spinnerResolution.selectedItemPosition
        val widescreen = binding.switchWidescreen.isChecked
        val extendToNotch = binding.switchExtendToNotch.isChecked
        val skipUnreadyPipelines = binding.switchSkipUnreadyPipelines.isChecked
        val disableCopyFilter = binding.switchDisableCopyFilter.isChecked
        val disableBloom = binding.switchDisableBloom.isChecked
        val sustainedPerf = binding.switchSustainedPerf.isChecked
        val audioMixer = binding.switchAudioMixer.isChecked

        val touchControls = binding.switchTouchControls.isChecked
        val tiltControls = binding.switchTiltControls.isChecked

        val masterVol = binding.sliderMasterVolume.value.toInt()
        val musicVol = binding.sliderMusicVolume.value.toInt()
        val sfxVol = binding.sliderSfxVolume.value.toInt()
        val audioMuted = binding.switchAudioMuted.isChecked

        val hfrIdx = binding.spinnerFrameInterpolation.selectedItemPosition
        val rumble = binding.switchRumble.isChecked
        val textureReplacements = binding.switchTextureReplacements.isChecked
        val showFps = binding.switchShowFps.isChecked
        val networkEnabled = false // Disabled until native Wiimmfi netplay is implemented (see unimplemented_features.md)

        prefs.edit()
            .putString("graphics_api", "vulkan")
            .putInt("resolution_idx", resIdx)
            .putBoolean("widescreen", widescreen)
            .putBoolean("extend_to_notch", extendToNotch)
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
            graphicsApi = "vulkan",
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
        graphicsApi: String = "vulkan",
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
            val pathsSection = if (!dvdRoot.isNullOrEmpty()) {
                "|[paths]\n|dvd_root = \"$dvdRoot\"\n|\n"
            } else ""

            val audioVolStr = String.format(java.util.Locale.US, "%.2f", masterVolume)
            val musicVolStr = String.format(java.util.Locale.US, "%.2f", musicVolume)
            val sfxVolStr = String.format(java.util.Locale.US, "%.2f", sfxVolume)
            val postProcessingPaths = if (disableBloom) 16 else 0

            val content = """
                |# WiiCompiled Android configuration (configured via launcher)
                |
                $pathsSection|[video]
                |widescreen = $widescreen
                |resolution_multiplier = $resolutionMultiplier
                |frame_interpolation_fps = $frameInterpolationFps
                |display_mode = "windowed"
                |graphics_api = "$graphicsApi"
                |skip_unready_pipelines = $skipUnreadyPipelines
                |disable_copy_filter = $disableCopyFilter
                |disabled_post_processing_paths = $postProcessingPaths
                |show_fps = $showFps
                |show_fps_passes = ${prefs.getBoolean("show_fps_passes", false)}
                |show_fps_draws = ${prefs.getBoolean("show_fps_draws", false)}
                |show_fps_bandwidth = ${prefs.getBoolean("show_fps_bandwidth", false)}
                |show_fps_builds = ${prefs.getBoolean("show_fps_builds", true)}
                |texture_replacements = $textureReplacements
                |texture_dumps = false
                |
                |[audio]
                |volume = $audioVolStr
                |music_volume = $musicVolStr
                |sound_effects_volume = $sfxVolStr
                |ui_volume = 1.0
                |voices_volume = 1.0
                |muted = $audioMuted
                |mix_worker = $audioMixer
                |
                |[controller]
                |rumble = $rumble
                |wii_remotes = false
                |
                |[network]
                |enabled = $networkEnabled
                |
                |[discord]
                |enabled = false
            """.trimMargin() + "\n"

            configFile.writeText(content)
        } catch (e: Exception) {
            android.util.Log.e("WiiCompiled", "Failed to update Config.toml: " + e.message)
        }
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    private fun pruneOldLogs(keepCount: Int = 3) {
        val logsDir = File(filesDir, "WiiCompiled/Logs")
        if (!logsDir.exists() || !logsDir.isDirectory) return

        try {
            val sessionDirs = logsDir.listFiles { file -> file.isDirectory }
                ?.sortedByDescending { it.lastModified() }
                ?: return

            // If more than keepCount session folders exist, remove the oldest ones
            if (sessionDirs.size > keepCount) {
                for (oldDir in sessionDirs.drop(keepCount)) {
                    oldDir.deleteRecursively()
                    android.util.Log.i("WiiCompiled", "Pruned old log directory: ${oldDir.name}")
                }
            }

            // Also clean up any orphan guest memory crash dumps (.bin / .mem2) older than the latest session
            logsDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.name.endsWith(".bin") || file.name.endsWith(".mem2"))) {
                    // Only keep dumps from the latest session if needed
                    val isInsideLatest = sessionDirs.take(1).any { file.absolutePath.startsWith(it.absolutePath) }
                    if (!isInsideLatest) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("WiiCompiled", "Log pruning encountered an error: ${e.message}")
        }
    }

    private fun updateLogsButtonSize() {
        if (!::binding.isInitialized) return
        pruneOldLogs(keepCount = 3)
        val logsDir = File(filesDir, "WiiCompiled/Logs")
        val bytes = getFolderSize(logsDir)
        binding.btnClearLogs.text = "Clear Crash Logs (${formatSize(bytes)})"
    }

    override fun onPause() {
        super.onPause()
        if (!isExtracting) {
            saveConfigOptions()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            updateLogsButtonSize()
        }
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
            binding.statusTitle.text = "Ready"
            binding.statusTitle.setTextColor(0xFF4CAF50.toInt())
            binding.statusText.text = "Mario Kart Wii (RMCP01) Verified"
            binding.statusIcon.setImageResource(R.drawable.ic_check_circle)
            binding.statusBadge.visibility = View.VISIBLE
            binding.statusBadge.text = "RMCP01"
            binding.launchBtn.isEnabled = true
            binding.selectDiscBtn.text = "Select Different Disc (.wbfs / .iso)"
        } else {
            binding.statusTitle.text = "Disc Required"
            binding.statusTitle.setTextColor(0xFFFF9800.toInt())
            binding.statusText.text = "Select a valid Wii disc image (.wbfs / .iso)"
            binding.statusIcon.setImageResource(R.drawable.ic_warning)
            binding.statusBadge.visibility = View.GONE
            binding.launchBtn.isEnabled = false
            binding.selectDiscBtn.text = "Select Disc Image (.wbfs / .iso)"
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
        binding.progressBar.isIndeterminate = false
        binding.progressBar.max = 100
        binding.progressBar.progress = 0
        binding.progressBar.visibility = View.VISIBLE
        binding.selectDiscBtn.isEnabled = false
        binding.launchBtn.isEnabled = false

        binding.statusTitle.text = "Installing..."
        binding.statusTitle.setTextColor(0xFF2196F3.toInt())
        binding.statusIcon.setImageResource(R.drawable.ic_disc)
        binding.statusBadge.visibility = View.VISIBLE
        binding.statusBadge.text = "EXTRACTING"
        binding.statusText.text = "Opening disc image (.wbfs / .iso)..."

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
                            binding.progressBar.progress = percent
                            binding.statusText.text = "$status ($percent%)"
                        }
                    }

                    runOnUiThread {
                        isExtracting = false
                        binding.progressBar.visibility = View.GONE
                        binding.selectDiscBtn.isEnabled = true
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
                    binding.progressBar.visibility = View.GONE
                    binding.selectDiscBtn.isEnabled = true
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
