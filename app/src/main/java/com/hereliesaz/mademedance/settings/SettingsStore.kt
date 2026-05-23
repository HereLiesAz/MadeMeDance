package com.hereliesaz.mademedance.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for user settings.
 *
 * Movement sensitivity (non-sensitive) lives in plain prefs; per the "one-way
 * conservative" rule, automatic adjustments only ever *lower* it. The ACRCloud
 * credentials are sensitive, so they live in an encrypted prefs file.
 */
object SettingsStore {

    private const val PREFS = "mmd_settings"
    private const val SECURE_PREFS = "mmd_secure_settings"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val KEY_ACR_HOST = "acr_host"
    private const val KEY_ACR_KEY = "acr_key"
    private const val KEY_ACR_SECRET = "acr_secret"
    private const val DEFAULT_SENSITIVITY = 0.5f

    // How far one rejected clip pulls sensitivity down.
    private const val REJECT_STEP = 0.08f

    private var prefs: SharedPreferences? = null
    private var securePrefs: SharedPreferences? = null

    private val _sensitivity = MutableStateFlow(DEFAULT_SENSITIVITY)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _acrHost = MutableStateFlow("")
    val acrHost: StateFlow<String> = _acrHost.asStateFlow()

    private val _acrKey = MutableStateFlow("")
    val acrKey: StateFlow<String> = _acrKey.asStateFlow()

    private val _acrSecret = MutableStateFlow("")
    val acrSecret: StateFlow<String> = _acrSecret.asStateFlow()

    private val _acrConfigured = MutableStateFlow(false)
    val acrConfigured: StateFlow<Boolean> = _acrConfigured.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also {
            _sensitivity.value = it.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        }
        securePrefs = openSecurePrefs(app)
        securePrefs?.let {
            _acrHost.value = it.getString(KEY_ACR_HOST, "") ?: ""
            _acrKey.value = it.getString(KEY_ACR_KEY, "") ?: ""
            _acrSecret.value = it.getString(KEY_ACR_SECRET, "") ?: ""
        }
        recomputeAcrConfigured()
    }

    private fun openSecurePrefs(app: Context): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Keystore unavailable/corrupt — credentials simply won't persist.
        null
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

    fun setAcrCredentials(host: String, key: String, secret: String) {
        _acrHost.value = host.trim()
        _acrKey.value = key.trim()
        _acrSecret.value = secret.trim()
        securePrefs?.edit()
            ?.putString(KEY_ACR_HOST, _acrHost.value)
            ?.putString(KEY_ACR_KEY, _acrKey.value)
            ?.putString(KEY_ACR_SECRET, _acrSecret.value)
            ?.apply()
        recomputeAcrConfigured()
    }

    private fun recomputeAcrConfigured() {
        _acrConfigured.value =
            _acrHost.value.isNotBlank() && _acrKey.value.isNotBlank() && _acrSecret.value.isNotBlank()
    }
}
