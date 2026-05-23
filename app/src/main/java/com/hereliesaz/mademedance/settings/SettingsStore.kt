package com.hereliesaz.mademedance.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the user-tunable movement sensitivity.
 *
 * The same persisted value backs the manual knob and the auto-tuner. Per the
 * "one-way conservative" rule, automatic adjustments only ever *lower*
 * sensitivity (a rejected clip means the detector was too twitchy); the user
 * is the only one who raises it back up via the knob.
 */
object SettingsStore {

    private const val PREFS = "mmd_settings"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val DEFAULT_SENSITIVITY = 0.5f

    // How far one rejected clip pulls sensitivity down.
    private const val REJECT_STEP = 0.08f

    private var prefs: SharedPreferences? = null

    private val _sensitivity = MutableStateFlow(DEFAULT_SENSITIVITY)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also {
            _sensitivity.value = it.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        }
    }

    fun setSensitivity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _sensitivity.value = clamped
        prefs?.edit()?.putFloat(KEY_SENSITIVITY, clamped)?.apply()
    }

    /** A false trigger: back the detector off. */
    fun recordReject() {
        setSensitivity(_sensitivity.value - REJECT_STEP)
    }

    /** A good catch: confirms the current setting. Never auto-raises sensitivity. */
    fun recordAccept() {
        // One-way conservative — intentionally a no-op on the threshold.
    }
}
