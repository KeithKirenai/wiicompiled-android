package com.wiicompiled.mkw

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.wiicompiled.mkw.databinding.ActivityInstallBinding
import com.wiicompiled.mkw.extractor.WiiDiscExtractor
import java.io.File

class InstallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstallBinding
    private var isExtracting = false

    private val selectDiscLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            startExtraction(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityInstallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectDisc.setOnClickListener {
            if (!isExtracting) {
                selectDiscLauncher.launch(arrayOf("*/*"))
            }
        }

        binding.btnDone.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        binding.btnCancel.setOnClickListener {
            if (!isExtracting) {
                finish()
            }
        }

        // If a disc URI was forwarded directly via intent
        intent.getParcelableExtra<Uri>("disc_uri")?.let { uri ->
            startExtraction(uri)
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

    private fun startExtraction(uri: Uri) {
        if (isExtracting) return
        isExtracting = true

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnSelectDisc.visibility = View.GONE
        binding.btnCancel.visibility = View.GONE
        binding.btnDone.visibility = View.GONE

        binding.installerProgressBar.isIndeterminate = false
        binding.installerProgressBar.max = 100
        binding.installerProgressBar.progress = 0
        binding.installerProgressBar.visibility = View.VISIBLE

        binding.installerStatusText.visibility = View.VISIBLE
        binding.installerStatusText.text = "Opening disc image (.wbfs / .iso)..."
        binding.installerTitle.text = "Installing Game Data..."
        binding.installerDescription.text = "Extracting Mario Kart Wii (RMCP01) filesystem. Please do not close the app."

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
                            binding.installerProgressBar.progress = percent
                            binding.installerStatusText.text = " (%)"
                        }
                    }

                    runOnUiThread {
                        isExtracting = false
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        binding.installerProgressBar.visibility = View.GONE

                        if (result.success) {
                            val prefs = getSharedPreferences("wiicompiled_settings", Context.MODE_PRIVATE)
                            prefs.edit().putString("dvd_root", targetDir.absolutePath).apply()

                            binding.installerIcon.setImageResource(R.drawable.ic_check_circle)
                            binding.installerIcon.imageTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
                            binding.installerTitle.text = "Installation Complete!"
                            binding.installerDescription.text = "Mario Kart Wii (RMCP01) unpacked successfully.\n files extracted to ."
                            binding.installerStatusText.visibility = View.GONE

                            binding.btnDone.visibility = View.VISIBLE
                        } else {
                            binding.installerIcon.setImageResource(R.drawable.ic_error)
                            binding.installerIcon.imageTintList = null
                            binding.installerTitle.text = "Installation Failed"
                            binding.installerDescription.text = result.errorMessage ?: "Failed to extract game disc image."
                            binding.installerStatusText.visibility = View.GONE

                            binding.btnSelectDisc.text = "Try Another Disc"
                            binding.btnSelectDisc.visibility = View.VISIBLE
                            binding.btnCancel.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isExtracting = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    binding.installerProgressBar.visibility = View.GONE

                    binding.installerIcon.setImageResource(R.drawable.ic_error)
                    binding.installerIcon.imageTintList = null
                    binding.installerTitle.text = "Installation Error"
                    binding.installerDescription.text = "An error occurred reading the disc file:\n"
                    binding.installerStatusText.visibility = View.GONE

                    binding.btnSelectDisc.text = "Try Again"
                    binding.btnSelectDisc.visibility = View.VISIBLE
                    binding.btnCancel.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    override fun onBackPressed() {
        if (!isExtracting) {
            super.onBackPressed()
        }
    }
}
