package com.alyssum.relegion.hyperconnect.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Only explicit UI actions use these fixed commands. No background root work. */
enum class RootCommand(val shellText: String) {
    OPEN_CROSS_DEVICE_NOTIFICATION("am start -W -n com.milink.service/com.miui.circulate.world.ui.connectivitysettings.Notification.FeatureNotificationActivity"),
    STOP_MILINK("am force-stop com.milink.service"),
    STOP_MIRROR("am force-stop com.xiaomi.mirror"),
    STOP_SETTINGS("am force-stop com.android.settings"),
    STOP_SECURITY_CORE("am force-stop com.miui.securitycore"),
}

data class RootResult(val exitCode: Int, val output: String, val timedOut: Boolean = false) {
    val isSuccess: Boolean get() = !timedOut && exitCode == 0
}

class RootShell {
    suspend fun execute(command: RootCommand): RootResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder("su", "-c", command.shellText).redirectErrorStream(true).start()
        } catch (_: Exception) {
            return@withContext RootResult(-1, "无法调用 root，请给 RELEGION 授权后重试")
        }
        // Drain concurrently: even an unexpected verbose su implementation cannot
        // block on a full pipe. Keep a bounded diagnostic tail in memory only.
        val output = StringBuilder()
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().use { stream ->
                    val buffer = CharArray(512)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        synchronized(output) {
                            output.append(buffer, 0, count)
                            if (output.length > 4096) output.delete(0, output.length - 4096)
                        }
                    }
                }
            }
        }, "RELEGION-RootOutput").apply { isDaemon = true; start() }
        try {
            val completed = process.waitFor(8, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            reader.join(300)
            RootResult(if (completed) process.exitValue() else -1,
                synchronized(output) { output.toString().trim() }, !completed)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.outputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }
}
