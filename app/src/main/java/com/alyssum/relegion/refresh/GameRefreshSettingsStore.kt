@file:android.annotation.SuppressLint("ApplySharedPref", "UseKtx")

package com.alyssum.relegion.refresh

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps only the setting confirmed by the framework's remote preference service. */
internal object GameRefreshSettingsStore {
    private val lock = Any()
    private val confirmed = MutableStateFlow<Boolean?>(null)
    val enabled = confirmed.asStateFlow()
    private var remote: SharedPreferences? = null

    fun bindRemote(preferences: SharedPreferences?) = synchronized(lock) {
        val value = preferences?.let {
            runCatching { GameRefreshPreferences.isEnabled(it) }.getOrNull()
        }
        remote = if (value == null) null else preferences
        confirmed.value = value
    }

    fun setEnabled(enabled: Boolean): Boolean = synchronized(lock) {
        val preferences = remote ?: return false
        if (confirmed.value == enabled) return true
        val saved = runCatching {
            preferences.edit().putBoolean(GameRefreshPreferences.KEY_ENABLED, enabled).commit()
        }.getOrDefault(false)
        if (saved) confirmed.value = enabled
        saved
    }
}
