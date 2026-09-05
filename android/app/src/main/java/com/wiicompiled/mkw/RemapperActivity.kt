package com.wiicompiled.mkw

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class RemapperActivity : AppCompatActivity() {

    private lateinit var remapperContainer: LinearLayout
    private lateinit var btnResetDefaults: Button
    private lateinit var btnSaveRemapper: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remapper)

        remapperContainer = findViewById(R.id.remapperContainer)
        btnResetDefaults = findViewById(R.id.btnResetDefaults)
        btnSaveRemapper = findViewById(R.id.btnSaveRemapper)

        buildRemapperRows()

        btnResetDefaults.setOnClickListener {
            AlertDialog.Builder(this)
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
                setPadding(0, 16, 0, 16)
            }

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                text = action.name
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14.0f
            }

            val currentKey = ControllerConfig.getActionKeyCode(this, action.actionId)
            val btnAssign = Button(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = ControllerConfig.getKeyName(currentKey)
                setTextColor(0xFF4CAF50.toInt())
                setBackgroundColor(0xFF263238.toInt())
                textSize = 12.0f
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
                setBackgroundColor(0xFF2A2A2A.toInt())
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
            setBackgroundColor(0xFF1E1E1E.toInt())
        }

        val title = TextView(this).apply {
            text = "Assign Button for:\n${action.name}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16.0f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val desc = TextView(this).apply {
            text = "Press any button on your connected controller now..."
            setTextColor(0xFF4CAF50.toInt())
            textSize = 14.0f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            setTextColor(0xFFECEFF1.toInt())
            setBackgroundColor(0xFF37474F.toInt())
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
