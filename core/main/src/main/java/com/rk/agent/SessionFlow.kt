package com.rk.agent

import android.app.Activity
import com.rk.exec.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launches a brand-new opencode chat and watches it in the background. When the interactive
 * opencode process exits, [onFinished] is invoked on the main thread with the session that was
 * created during the chat (or null if none/no exit detected), so the caller can ask the user to
 * save (rename) or discard it.
 */
object SessionFlow {

    private var job: Job? = null

    fun watchNewChat(activity: Activity, directory: String?, onFinished: (ChatSession?) -> Unit) {
        job?.cancel()
        job =
            CoroutineScope(Dispatchers.IO).launch {
                val before = runCatching { ChatMemory.maxSessionTime() }.getOrDefault(0L)
                ChatMemory.openProjectChat(activity, directory, continueLatest = false)

                var seen = false
                var idle = 0
                val start = System.currentTimeMillis()
                val maxWatchMs = 6L * 60 * 60 * 1000
                while (System.currentTimeMillis() - start < maxWatchMs) {
                    delay(2000)
                    val running =
                        runCatching { ChatMemory.isOpencodeRunning() }.getOrDefault(false)
                    if (running) {
                        seen = true
                        idle = 0
                    } else if (seen) {
                        idle++
                        if (idle >= 2) break
                    }
                }

                val session =
                    if (!seen) null
                    else runCatching { ChatMemory.newestSessionSince(before) }.getOrNull()

                withContext(Dispatchers.Main) { onFinished(session) }
            }
    }

    suspend fun warmUpSandbox() {
        runCatching {
            ShellUtils.runUbuntu(workingDir = null, "bash", "-lc", "true", timeoutSeconds = 90L)
        }
    }
}
