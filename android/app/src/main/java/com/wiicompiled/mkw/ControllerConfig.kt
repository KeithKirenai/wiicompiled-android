package com.wiicompiled.mkw

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

object ControllerConfig {
    private const val PREF_NAME = "wiicompiled_controller_map"

    enum class ActionCategory(val title: String) {
        DRIVING("Primary Driving Controls"),
        ITEMS_SPECIAL("Items & Secondary Actions"),
        DPAD("D-Pad / Tricks"),
        SYSTEM("System & Menus")
    }

    // Game action IDs
    val ACTIONS = listOf(
        ActionDef(GameActivity.BTN_A, "Accelerate", "Gas & Menu Confirm", KeyEvent.KEYCODE_BUTTON_A, ActionCategory.DRIVING),
        ActionDef(GameActivity.BTN_B, "Drift / Brake", "Slide, Brake & Menu Back", KeyEvent.KEYCODE_BUTTON_B, ActionCategory.DRIVING),
        ActionDef(GameActivity.BTN_L, "Use Item", "Fire item forward / drop behind", KeyEvent.KEYCODE_BUTTON_L1, ActionCategory.DRIVING),
        ActionDef(GameActivity.BTN_R, "Hop / Drift Alt", "Hop & alternative drift trigger", KeyEvent.KEYCODE_BUTTON_R1, ActionCategory.DRIVING),

        ActionDef(GameActivity.BTN_ZL, "Rear View (ZL)", "Look behind your vehicle", KeyEvent.KEYCODE_BUTTON_L2, ActionCategory.ITEMS_SPECIAL),
        ActionDef(GameActivity.BTN_ZR, "Second Gas (ZR)", "Secondary acceleration trigger", KeyEvent.KEYCODE_BUTTON_R2, ActionCategory.ITEMS_SPECIAL),
        ActionDef(GameActivity.BTN_X, "Wheelie / Action (X)", "Wheelie on bikes / special action", KeyEvent.KEYCODE_BUTTON_X, ActionCategory.ITEMS_SPECIAL),
        ActionDef(GameActivity.BTN_Y, "Action (Y)", "Auxiliary action button", KeyEvent.KEYCODE_BUTTON_Y, ActionCategory.ITEMS_SPECIAL),

        ActionDef(GameActivity.BTN_DPAD_UP, "D-Pad Up", "Upward trick / wheelie", KeyEvent.KEYCODE_DPAD_UP, ActionCategory.DPAD),
        ActionDef(GameActivity.BTN_DPAD_DOWN, "D-Pad Down", "Downward stunt / trick", KeyEvent.KEYCODE_DPAD_DOWN, ActionCategory.DPAD),
        ActionDef(GameActivity.BTN_DPAD_LEFT, "D-Pad Left", "Left stunt / trick", KeyEvent.KEYCODE_DPAD_LEFT, ActionCategory.DPAD),
        ActionDef(GameActivity.BTN_DPAD_RIGHT, "D-Pad Right", "Right stunt / trick", KeyEvent.KEYCODE_DPAD_RIGHT, ActionCategory.DPAD),

        ActionDef(GameActivity.BTN_START, "Pause (+)", "Pause gameplay & in-game menu", KeyEvent.KEYCODE_BUTTON_START, ActionCategory.SYSTEM),
        ActionDef(GameActivity.BTN_SELECT, "Map (-)", "Toggle course minimap / view", KeyEvent.KEYCODE_BUTTON_SELECT, ActionCategory.SYSTEM)
    )

    data class ActionDef(
        val actionId: Int,
        val name: String,
        val description: String,
        val defaultKeyCode: Int,
        val category: ActionCategory
    )

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
