package com.zinsch.aircursor

import android.content.Context

object SettingsStore {
    private const val PREFS = "aircursor_settings"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val KEY_SMOOTHING = "smoothing"
    private const val KEY_INFERENCE = "inference"
    private const val KEY_SCROLL = "scroll_enabled"
    private const val KEY_HAPTIC = "haptic"

    fun sensitivity(c: Context) = c.getSharedPreferences(PREFS, 0).getFloat(KEY_SENSITIVITY, 1.0f)
    fun smoothing(c: Context) = c.getSharedPreferences(PREFS, 0).getFloat(KEY_SMOOTHING, 0.28f)
    fun inference(c: Context) = c.getSharedPreferences(PREFS, 0).getInt(KEY_INFERENCE, 15)
    fun scrollEnabled(c: Context) = c.getSharedPreferences(PREFS, 0).getBoolean(KEY_SCROLL, true)
    fun haptic(c: Context) = c.getSharedPreferences(PREFS, 0).getBoolean(KEY_HAPTIC, true)

    fun save(c: Context, sensitivity: Float, smoothing: Float, inference: Int, scroll: Boolean, haptic: Boolean) {
        c.getSharedPreferences(PREFS, 0).edit()
            .putFloat(KEY_SENSITIVITY, sensitivity)
            .putFloat(KEY_SMOOTHING, smoothing)
            .putInt(KEY_INFERENCE, inference)
            .putBoolean(KEY_SCROLL, scroll)
            .putBoolean(KEY_HAPTIC, haptic)
            .apply()
    }
}
