package com.wiicompiled.mkw

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RemapperActivity : AppCompatActivity() {

    private lateinit var remapperContainer: LinearLayout
    private lateinit var btnResetDefaults: Button
    private lateinit var btnSaveRemapper: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remapper)

        remapperContainer = findViewById(R.id.remapperContainer)
        btnResetDefaults = findViewById(R.id.btnResetDefaults)
        btnSaveRemapper = findViewById(R.id.btnSaveRemapper)

        buildRemapperRows()

        btnResetDefaults.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Controller Mapping")
                .setMessage("Reset all buttons to standard Xbox / universal defaults?")
                .setPositiveButton("Reset") { _, _ ->
                    ControllerConfig.resetToDefaults(this)
                    buildRemapperRows()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnSaveRemapper.setOnClickListener {
            finish()
        }
    }

    private fun buildRemapperRows() {
        remapperContainer.removeAllViews()

        for (action in ControllerConfig.ACTIONS) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 14, 0, 14)
            }

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                text = action.name
                setTextColor(0xFFE1E3DF.toInt())
                textSize = 15.0f
            }

            val currentKey = ControllerConfig.getActionKeyCode(this, action.actionId)
            val btnAssign = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = ControllerConfig.getKeyName(currentKey)
                setTextColor(0xFFA8D5BA.toInt())
                strokeColor = android.content.res.ColorStateList.valueOf(0xFF36413A.toInt())
                cornerRadius = 20
                textSize = 13.0f
                setOnClickListener {
                    promptKeyListening(action)
                }
            }

            row.addView(label)
            row.addView(btnAssign)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                )
                setBackgroundColor(0xFF272D29.toInt())
            }

            remapperContainer.addView(row)
            remapperContainer.addView(divider)
        }
    }

    private fun promptKeyListening(action: ControllerConfig.ActionDef) {
        var listeningDialog: Dialog? = null

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF1D221F.toInt())
        }

        val title = TextView(this).apply {
            text = "Assign Button for:\n${action.name}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18.0f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        val desc = TextView(this).apply {
            text = "Press any button on your connected controller now..."
            setTextColor(0xFFA8D5BA.toInt())
            textSize = 14.0f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }

        val cancelBtn = MaterialButton(this).apply {
            text = "Cancel"
            setTextColor(0xFFE1E3DF.toInt())
            setBackgroundColor(0xFF272D29.toInt())
            cornerRadius = 24
            setOnClickListener {
                listeningDialog?.dismiss()
            }
        }

        dialogView.addView(title)
        dialogView.addView(desc)
        dialogView.addView(cancelBtn)

        listeningDialog = Dialog(this).apply {
            setContentView(dialogView)
            setCancelable(true)
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode != KeyEvent.KEYCODE_BACK) {
                        ControllerConfig.setActionKeyCode(this@RemapperActivity, action.actionId, keyCode)
                        dismiss()
                        buildRemapperRows()
                        return@setOnKeyListener true
                    }
                }
                false
            }
            show()
        }
    }
}
