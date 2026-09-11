package com.wiicompiled.mkw

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RemapperActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var remapperContainer: LinearLayout
    private lateinit var btnResetDefaults: Button
    private lateinit var btnSaveRemapper: Button
    private lateinit var tvControllerName: TextView
    private lateinit var tvControllerHint: TextView
    private lateinit var ivControllerIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remapper)

        toolbar = findViewById(R.id.toolbar)
        remapperContainer = findViewById(R.id.remapperContainer)
        btnResetDefaults = findViewById(R.id.btnResetDefaults)
        btnSaveRemapper = findViewById(R.id.btnSaveRemapper)
        tvControllerName = findViewById(R.id.tvControllerName)
        tvControllerHint = findViewById(R.id.tvControllerHint)
        ivControllerIcon = findViewById(R.id.ivControllerIcon)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        detectControllers()
        buildRemapperSections()

        btnResetDefaults.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Controller Mapping")
                .setMessage("Reset all buttons to standard Xbox / universal defaults?")
                .setPositiveButton("Reset") { _, _ ->
                    ControllerConfig.resetToDefaults(this)
                    buildRemapperSections()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnSaveRemapper.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        detectControllers()
    }

    private fun detectControllers() {
        val deviceIds = InputDevice.getDeviceIds()
        var controllerFound = false
        var deviceName = ""

        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                controllerFound = true
                deviceName = device.name
                break
            }
        }

        if (controllerFound) {
            tvControllerName.text = deviceName
            tvControllerHint.text = "Connected & active. Ready for button mapping."
            ivControllerIcon.setColorFilter(ContextCompat.getColor(this, R.color.status_ready))
        } else {
            tvControllerName.text = "No Gamepad Detected"
            tvControllerHint.text = "Connect a Bluetooth / USB controller or map standard keys."
            ivControllerIcon.setColorFilter(ContextCompat.getColor(this, R.color.md_theme_dark_primary))
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun buildRemapperSections() {
        remapperContainer.removeAllViews()

        val groupedActions = ControllerConfig.ACTIONS.groupBy { it.category }

        for ((category, actions) in groupedActions) {
            // Category header
            val headerText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dpToPx(6), dpToPx(14), dpToPx(6), dpToPx(6))
                }
                text = category.title.uppercase()
                setTextColor(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_primary))
                textSize = 12f
                paint.isFakeBoldText = true
                letterSpacing = 0.08f
            }
            remapperContainer.addView(headerText)

            // Category card
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(14)
                }
                radius = dpToPx(20).toFloat()
                cardElevation = 0f
                strokeWidth = dpToPx(1)
                setStrokeColor(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_outlineVariant))
                setCardBackgroundColor(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_surfaceContainer))
            }

            val cardInner = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6))
            }

            actions.forEachIndexed { index, action ->
                val row = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dpToPx(10), 0, dpToPx(10))
                }

                // Left text block: Name + Description
                val textContainer = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, dpToPx(10), 0)
                }

                val titleTv = TextView(this).apply {
                    text = action.name
                    setTextColor(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_onSurface))
                    textSize = 15f
                    paint.isFakeBoldText = true
                }

                val descTv = TextView(this).apply {
                    text = action.description
                    setTextColor(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_onSurfaceVariant))
                    textSize = 12f
                    setPadding(0, dpToPx(2), 0, 0)
                }

                textContainer.addView(titleTv)
                textContainer.addView(descTv)

                // Right button badge
                val currentKey = ControllerConfig.getActionKeyCode(this, action.actionId)
                val isUnassigned = currentKey == 0

                val btnAssign = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dpToPx(42)
                    )
                    text = ControllerConfig.getKeyName(currentKey)
                    textSize = 12.5f
                    cornerRadius = dpToPx(14)
                    insetTop = 0
                    insetBottom = 0

                    if (isUnassigned) {
                        setTextColor(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_outline))
                        strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_outlineVariant))
                        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    } else {
                        setTextColor(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_onPrimaryContainer))
                        strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_primary))
                        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_primaryContainer))
                    }

                    setOnClickListener {
                        promptKeyListening(action)
                    }
                }

                row.addView(textContainer)
                row.addView(btnAssign)
                cardInner.addView(row)

                if (index < actions.size - 1) {
                    val divider = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dpToPx(1)
                        )
                        setBackgroundColor(ContextCompat.getColor(this@RemapperActivity, R.color.md_theme_dark_outlineVariant))
                    }
                    cardInner.addView(divider)
                }
            }

            card.addView(cardInner)
            remapperContainer.addView(card)
        }
    }

    private fun promptKeyListening(action: ControllerConfig.ActionDef) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogView = layoutInflater.inflate(R.layout.dialog_listen_key, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.88).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvDialogActionName: TextView = dialogView.findViewById(R.id.tvDialogActionName)
        val btnDialogCancel: MaterialButton = dialogView.findViewById(R.id.btnDialogCancel)
        val btnDialogUnassign: MaterialButton = dialogView.findViewById(R.id.btnDialogUnassign)

        tvDialogActionName.text = action.name

        btnDialogCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDialogUnassign.setOnClickListener {
            ControllerConfig.setActionKeyCode(this@RemapperActivity, action.actionId, 0)
            dialog.dismiss()
            buildRemapperSections()
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss()
                    return@setOnKeyListener true
                }
                ControllerConfig.setActionKeyCode(this@RemapperActivity, action.actionId, keyCode)
                dialog.dismiss()
                buildRemapperSections()
                return@setOnKeyListener true
            }
            false
        }

        dialog.setCancelable(true)
        dialog.show()
    }
}
