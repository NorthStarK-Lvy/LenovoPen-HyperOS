package com.alyssum.relegion

import android.content.Context
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** One bounded wait, one serial worker, and no work scheduled while the page is closed. */
internal object PenStateBridge {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "RELEGION-PenClient").apply { isDaemon = true }
    }
    suspend fun refresh(context: Context) {
        val state = withTimeoutOrNull(6_000) {
            suspendCancellableCoroutine<Bundle?> { continuation ->
                val future = worker.submit {
                    val reply = try {
                        context.contentResolver.acquireUnstableContentProviderClient(
                            Uri.parse("content://${Protocol.PEN_AUTHORITY}")
                        ).use { it?.call(Protocol.PEN_CALL, null, Bundle.EMPTY) }
                    } catch (_: Exception) {
                        null
                    }
                    if (continuation.isActive) continuation.resume(reply)
                }
                continuation.invokeOnCancellation { future.cancel(true) }
            }
        }
        // A failed bridge must not replace an independently verified local connection.
        if (state?.getInt("bridgeVersion") == 2) {
            context.contentResolver.call(Uri.parse("content://${Protocol.AUTHORITY}"), "publish", null, state)
        }
    }
}
