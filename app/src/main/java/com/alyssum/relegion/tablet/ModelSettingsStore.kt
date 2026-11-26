@file:android.annotation.SuppressLint("ApplySharedPref", "UseKtx")
package com.alyssum.relegion.tablet

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ModelConfiguration(val name: String = "", val remoteAvailable: Boolean = false, val pending: Boolean = false)

/** Local durable editor plus the framework's private module preference group. */
object ModelSettingsStore {
    private val lock = Any()
    private lateinit var local: SharedPreferences
    private var remote: SharedPreferences? = null
    private val mutable = MutableStateFlow(ModelConfiguration())
    val configuration = mutable.asStateFlow()

    fun initialize(context: Context) = synchronized(lock) {
        if (!::local.isInitialized) {
            local = context.getSharedPreferences(ModelNamePolicy.GROUP, Context.MODE_PRIVATE)
            mutable.value = ModelConfiguration(read(local), pending = local.getBoolean("pending", false))
        }
    }

    fun bindRemote(preferences: SharedPreferences): Boolean = synchronized(lock) {
        try {
            val localWins = local.getBoolean("pending", false) || !preferences.contains(ModelNamePolicy.KEY)
            val name = if (localWins) read(local) else read(preferences)
            val synced = !localWins || preferences.edit().putString(ModelNamePolicy.KEY, name).commit()
            check(local.edit().putString(ModelNamePolicy.KEY, name).putBoolean("pending", !synced).commit())
            remote = preferences
            mutable.value = ModelConfiguration(name, remoteAvailable = true, pending = !synced)
            true
        } catch (_: RuntimeException) {
            remote = null
            mutable.value = mutable.value.copy(remoteAvailable = false)
            false
        }
    }

    fun unbindRemote(preferences: SharedPreferences?) = synchronized(lock) {
        if (preferences == null || remote === preferences) {
            remote = null
            mutable.value = mutable.value.copy(remoteAvailable = false)
        }
    }

    fun setName(value: String): Boolean = synchronized(lock) {
        val name = ModelNamePolicy.normalize(value)
        if (!local.edit().putString(ModelNamePolicy.KEY, name).putBoolean("pending", true).commit()) return false
        val synchronized = remote?.let {
            runCatching { it.edit().putString(ModelNamePolicy.KEY, name).commit() }.getOrDefault(false)
        } ?: false
        val complete = synchronized && local.edit().putBoolean("pending", false).commit()
        mutable.value = ModelConfiguration(name, remote != null, pending = !complete)
        true
    }

    private fun read(preferences: SharedPreferences) =
        ModelNamePolicy.normalize(preferences.getString(ModelNamePolicy.KEY, ""))
}
