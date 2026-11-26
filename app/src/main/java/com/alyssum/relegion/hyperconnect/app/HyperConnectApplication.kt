// Adapted from silverpoetry/HyperConnectToolkit v1.6.0 (Apache-2.0).
// RELEGION: namespace, API 102 integration and activation evidence.
package com.alyssum.relegion.hyperconnect.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.UserManager
import com.alyssum.relegion.DiagnosticLog
import com.alyssum.relegion.LogLevel
import com.alyssum.relegion.hyperconnect.config.FeaturePreferences
import com.alyssum.relegion.hyperconnect.config.FeatureSettingsStore
import com.alyssum.relegion.hyperconnect.platform.HookRuntimeStore
import com.alyssum.relegion.refresh.GameRefreshPreferences
import com.alyssum.relegion.refresh.GameRefreshSettingsStore
import com.alyssum.relegion.tablet.ModelNamePolicy
import com.alyssum.relegion.tablet.ModelSettingsStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the companion application's connection to the official libxposed service. */
class HyperConnectApplication : Application(), XposedServiceHelper.OnServiceListener {
    private val userServicesInitialized = AtomicBoolean(false)

    @Volatile
    private var service: XposedService? = null

    @Volatile
    private var remotePreferences: SharedPreferences? = null
    @Volatile private var modelPreferences: SharedPreferences? = null

    private val userUnlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_UNLOCKED) return
            unregisterReceiver(this)
            initializeUserServices()
        }
    }

    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.initialize(this)
        HookRuntimeStore.initialize(this)
        val userManager = getSystemService(UserManager::class.java)
        if (userManager.isUserUnlocked) {
            initializeUserServices()
        } else {
            registerReceiver(
                userUnlockedReceiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun initializeUserServices() {
        if (!userServicesInitialized.compareAndSet(false, true)) return
        FeatureSettingsStore.initialize(this)
        ModelSettingsStore.initialize(this)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(boundService: XposedService) {
        service = boundService
        DiagnosticLog.bindRemote(runCatching { boundService.getRemotePreferences(LogLevel.GROUP) }.getOrNull())
        GameRefreshSettingsStore.bindRemote(
            runCatching { boundService.getRemotePreferences(GameRefreshPreferences.GROUP) }.getOrNull(),
        )
        val oldModel = modelPreferences
        val model = runCatching { boundService.getRemotePreferences(ModelNamePolicy.GROUP) }.getOrNull()
        modelPreferences = model?.takeIf { ModelSettingsStore.bindRemote(it) }
        if (modelPreferences == null) ModelSettingsStore.unbindRemote(oldModel)
        val previousPreferences = remotePreferences
        val preferences = runCatching {
            boundService.getRemotePreferences(FeaturePreferences.GROUP)
        }.getOrNull()
        val synchronized = preferences != null && FeatureSettingsStore.bindRemote(preferences)
        remotePreferences = preferences.takeIf { synchronized }
        if (!synchronized) {
            FeatureSettingsStore.unbindRemote(previousPreferences)
        }
    }

    override fun onServiceDied(deadService: XposedService) {
        if (service !== deadService) return
        service = null
        DiagnosticLog.bindRemote(null)
        GameRefreshSettingsStore.bindRemote(null)
        val preferences = remotePreferences
        remotePreferences = null
        ModelSettingsStore.unbindRemote(modelPreferences)
        modelPreferences = null
        FeatureSettingsStore.unbindRemote(preferences)
    }
}
