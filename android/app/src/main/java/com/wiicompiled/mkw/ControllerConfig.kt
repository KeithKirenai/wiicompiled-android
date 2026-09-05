package com.wiicompiled.mkw

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

object ControllerConfig {
    private const val PREF_NAME = "wiicompiled_controller_map"

    // Game action IDs
    val ACTIONS = listOf(
        ActionDef(GameActivity.BTN_A, "Accelerate / Confirm (A)", KeyEvent.KEYCODE_BUTTON_A),
        ActionDef(GameActivity.BTN_B, "Drift / Brake / Back (B)", KeyEvent.KEYCODE_BUTTON_B),
        ActionDef(GameActivity.BTN_L, "Use Item (L / LB)", KeyEvent.KEYCODE_BUTTON_L1),
        ActionDef(GameActivity.BTN_R, "Hop / Drift Alternative (R / RB)", KeyEvent.KEYCODE_BUTTON_R1),
        ActionDef(GameActivity.BTN_ZL, "Rear View / ZL (LT)", KeyEvent.KEYCODE_BUTTON_L2),
        ActionDef(GameActivity.BTN_ZR, "Second Gas / ZR (RT)", KeyEvent.KEYCODE_BUTTON_R2),
        ActionDef(GameActivity.BTN_X, "Wheelie / Action (X)", KeyEvent.KEYCODE_BUTTON_X),
        ActionDef(GameActivity.BTN_Y, "Action (Y)", KeyEvent.KEYCODE_BUTTON_Y),
        ActionDef(GameActivity.BTN_START, "Pause / Menu (+)", KeyEvent.KEYCODE_BUTTON_START),
        ActionDef(GameActivity.BTN_SELECT, "Map / View (-)", KeyEvent.KEYCODE_BUTTON_SELECT),
        ActionDef(GameActivity.BTN_DPAD_UP, "D-Pad Up (Trick / Wheelie)", KeyEvent.KEYCODE_DPAD_UP),
        ActionDef(GameActivity.BTN_DPAD_DOWN, "D-Pad Down (Trick)", KeyEvent.KEYCODE_DPAD_DOWN),
        ActionDef(GameActivity.BTN_DPAD_LEFT, "D-Pad Left (Trick)", KeyEvent.KEYCODE_DPAD_LEFT),
        ActionDef(GameActivity.BTN_DPAD_RIGHT, "D-Pad Right (Trick)", KeyEvent.KEYCODE_DPAD_RIGHT)
    )

    data class ActionDef(val actionId: Int, val name: String, val defaultKeyCode: Int)

    fun getMapping(context: Context): Map<Int, Int> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val map = mutableMapOf<Int, Int>() // keyCode -> actionId
        for (action in ACTIONS) {
            val assignedKey = prefs.getInt("action_${action.actionId}", action.defaultKeyCode)
            if (assignedKey > 0) {
                map[assignedKey] = action.actionId
            }
        }
        return map
    }

    fun getActionKeyCode(context: Context, actionId: Int): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val defaultKey = ACTIONS.find { it.actionId == actionId }?.defaultKeyCode ?: 0
        return prefs.getInt("action_$actionId", defaultKey)
    }

    fun setActionKeyCode(context: Context, actionId: Int, keyCode: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("action_$actionId", keyCode).apply()
    }

    fun resetToDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "Button A (Cross)"
            KeyEvent.KEYCODE_BUTTON_B -> "Button B (Circle)"
            KeyEvent.KEYCODE_BUTTON_X -> "Button X (Square)"
            KeyEvent.KEYCODE_BUTTON_Y -> "Button Y (Triangle)"
            KeyEvent.KEYCODE_BUTTON_L1 -> "LB / L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "RB / R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "LT / L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "RT / R2"
            KeyEvent.KEYCODE_BUTTON_START -> "Start / Menu"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Select / Back"
            KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "Left Stick Click"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "Right Stick Click"
            0 -> "None (Unassigned)"
            else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        }
    }
}
