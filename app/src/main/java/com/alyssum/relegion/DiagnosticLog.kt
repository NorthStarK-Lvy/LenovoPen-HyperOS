package com.alyssum.relegion

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object DiagnosticLog {
    private val selected = MutableStateFlow(LogLevel.WARN)
    val level = selected.asStateFlow()
    private var local: SharedPreferences? = null
    private var remote: SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        local = context.createDeviceProtectedStorageContext()
            .getSharedPreferences(LogLevel.GROUP, Context.MODE_PRIVATE)
        selected.value = LogLevel.read(local)
    }

    @Synchronized
    fun bindRemote(preferences: SharedPreferences?) {
        remote = preferences
        sync()
    }

    @Synchronized
    fun select(level: LogLevel): Boolean {
        selected.value = level
        local?.edit()?.putString(LogLevel.KEY, level.name)?.apply()
        return sync()
    }

    private fun sync(): Boolean {
        val preferences = remote ?: return false
        return runCatching {
            preferences.getString(LogLevel.KEY, null) == selected.value.name ||
                preferences.edit().putString(LogLevel.KEY, selected.value.name).commit()
        }.getOrDefault(false)
    }

    fun write(priority: Int, tag: String, message: String, error: Throwable? = null) {
        if (!selected.value.allows(priority)) return
        Log.println(priority, tag, message)
        if (error != null) Log.println(priority, tag, Log.getStackTraceString(error))
    }
}
