// Adapted from silverpoetry/HyperConnectToolkit v1.6.0 (Apache-2.0).
// RELEGION: namespace, API 102 integration and activation evidence.
package com.alyssum.relegion.hyperconnect.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import android.os.UserManager
import com.alyssum.relegion.DiagnosticLog
import com.alyssum.relegion.hyperconnect.config.FeatureSettingsStore

/** Receives activation evidence from a genuinely hooked target process. */
class HookStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HookStatusContract.ACTION_HOOKS_ACTIVE) return
        if (Build.VERSION.SDK_INT < 34) return

        val senderUid = sentFromUid
        if (senderUid < 0) {
            DiagnosticLog.write(Log.WARN, TAG, "Rejected Hook report without sender identity")
            return
        }
        val senderPackages = context.packageManager
            .getPackagesForUid(senderUid)
            .orEmpty()
            .asList()
        val report = HookStatusPolicy.validate(
            senderPackages = senderPackages,
            sourcePackage = intent.getStringExtra(HookStatusContract.EXTRA_SOURCE_PACKAGE),
            sourceProcess = intent.getStringExtra(HookStatusContract.EXTRA_SOURCE_PROCESS),
            moduleVersion = intent.getIntExtra(HookStatusContract.EXTRA_MODULE_VERSION, 0),
            reportedFeatureMask = intent.getIntExtra(
                HookStatusContract.EXTRA_ACTIVE_FEATURES,
                0,
            ),
        )
        if (report == null) {
            DiagnosticLog.write(Log.WARN, TAG, "Rejected invalid Hook report from uid=$senderUid")
            return
        }
        val snapshot = intent.getIntExtra(HookStatusContract.EXTRA_CONFIG_SNAPSHOT, -1)
        if (snapshot !in 0..7) return
        val required = when (report.featureMask) {
            HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY,
            HookStatusContract.COMPONENT_NOTIFICATION_FLOW -> 1
            HookStatusContract.FEATURE_PORTRAIT_STREAMING -> 2
            HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL -> 4
            else -> return
        }
        if (snapshot and required != required) return
        // Ignore a late report from a process still using the old configuration.
        // Before first unlock, only device-protected status storage is available.
        if (context.getSystemService(UserManager::class.java).isUserUnlocked) {
            FeatureSettingsStore.initialize(context)
            if (snapshot != FeatureSettingsStore.configuration.value.settings.snapshot) return
        }
        HookRuntimeStore.recordReportedCapabilities(context, report.featureMask, snapshot)
        DiagnosticLog.write(
            Log.INFO,
            TAG,
            "Recorded Hook capabilities=${report.featureMask} from ${report.target.processName}",
        )
    }

    companion object {
        private const val TAG = "HyperConnectToolkit"
    }
}
