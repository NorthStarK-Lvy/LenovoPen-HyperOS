package com.alyssum.relegion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Explicit, user-triggered root bridge for the pen side key and the existing event-driven pen guard. */
class RootPenBridge(private val context: Context) {
    data class Result(val success: Boolean, val output: String)

    /** Starts the side-key listener only. It never touches refresh-rate settings. */
    suspend fun startKeys(): Result = withContext(Dispatchers.IO) {
        prepareFiles()
        writeConfig()
        val dir = bridgeDir()
        val bridge = q(dir.resolve("pen-bridge"))
        runRoot("chmod 700 ${q(dir.resolve("pen-bridge"))} ${q(dir.resolve("launch-note.sh"))}; " +
            "mkdir -p ${q(dir.resolve("run"))}; rm -f ${q(dir.resolve("run/stop"))}; " +
            "if [ -z \"\$(pidof pen-bridge)\" ]; then cd ${q(dir)}; " +
            "setsid $bridge ${q(dir)} --keys </dev/null >/dev/null 2>&1 & fi; sleep 1; pidof pen-bridge")
    }

    suspend fun stopKeys(): Result = withContext(Dispatchers.IO) {
        val stop = bridgeDir().resolve("run/stop")
        stop.parentFile?.mkdirs()
        runRoot("touch ${q(stop)}")
    }

    /** The launcher script reads this file on every click, so saving never needs a restart. */
    fun writeConfig() {
        val dir = bridgeDir().also { it.mkdirs() }
        val consumers = if (PenActionStore.consumersEnabled(context))
            PenActionStore.consumers(context).filter { PenActions.isPackageName(it) } else emptyList()
        val clickPackage = PenActionStore.openPackage(context).takeIf { PenActions.isPackageName(it) } ?: ""
        val doublePackage = PenActionStore.doublePackage(context).takeIf { PenActions.isPackageName(it) } ?: ""
        val lines = listOf(
            "click=${PenActions.normalize(PenActionStore.action(context), PenActions.CREATE_NOTE)}",
            "double=${PenActions.normalize(PenActionStore.doubleAction(context), PenActions.SCREEN_RECOGNITION)}",
            "package=$clickPackage",
            "double_package=$doublePackage",
            "consumers=${consumers.joinToString(",")}",
            "guard=${if (consumers.isEmpty()) 0 else 1}",
        )
        File(dir, "config").writeText(lines.joinToString("\n", postfix = "\n"))
    }

    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) { runRoot("pidof pen-bridge").let { it.success && it.output.isNotBlank() } }

    suspend fun start(): Result = withContext(Dispatchers.IO) {
        prepareFiles()
        val dir = bridgeDir()
        runRoot("chmod 700 ${q(dir.resolve("pen-bridge"))}; " +
            "mkdir -p ${q(dir.resolve("run"))}; " +
            "rm -f ${q(dir.resolve("disable"))} ${q(dir.resolve("remove"))} ${q(dir.resolve("run/stop"))}; " +
            "cd ${q(dir)}; ${q(dir.resolve("pen-bridge"))} ${q(dir)} --refresh-only >/dev/null 2>&1 &")
    }

    suspend fun stop(): Result = withContext(Dispatchers.IO) {
        val stop = bridgeDir().resolve("run/stop")
        stop.parentFile?.mkdirs()
        if (!stop.exists()) stop.createNewFile()
        Result(true, "stop requested")
    }

    suspend fun applyDisplaySize(): Result = withContext(Dispatchers.IO) {
        runRoot("/system/bin/wm size 2000x3199")
    }

    suspend fun resetDisplaySize(): Result = withContext(Dispatchers.IO) {
        runRoot("/system/bin/wm size reset")
    }

    private fun bridgeDir(): File = File(context.filesDir, "root-bridge")

    private fun prepareFiles() {
        val dir = bridgeDir()
        dir.mkdirs()
        for (name in listOf("pen-bridge", "quicknote.jar", "launch-note.sh")) {
            val target = File(dir, name)
            context.assets.open("root-bridge/$name").use { input -> target.outputStream().use(input::copyTo) }
        }
    }

    private fun runRoot(command: String): Result {
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (error: Exception) {
            return Result(false, "无法调用 root：${error.message ?: "su unavailable"}")
        }
        val output = StringBuilder()
        val reader = Thread({
            runCatching { process.inputStream.bufferedReader().use { output.append(it.readText().takeLast(4096)) } }
        }, "RELEGION-RootOutput").apply { isDaemon = true; start() }
        val completed = process.waitFor(10, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(500)
        return Result(completed && process.exitValue() == 0, synchronized(output) { output.toString().trim() })
    }

    private fun q(file: File): String = q(file.absolutePath)
    private fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
