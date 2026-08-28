package com.rk.agent

import com.rk.exec.ubuntuProcess
import com.rk.file.sandboxHomeDir
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AgentDownloads {
    data class DownloadJob(
        val id: String,
        val url: String,
        val format: String,
        var status: String,
        var progress: Int,
        var line: String,
        var outputFile: String?,
        var error: String?,
        val createdAt: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, DownloadJob>()
    private val processes = ConcurrentHashMap<String, Process>()
    private val progressRegex = Regex("\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%")
    private val destinationRegex = Regex("\\[download\\]\\s+Destination:\\s+(.+)")

    fun start(url: String, format: String): String {
        require(url.length < 2000) { "url too long" }
        require(url.contains("://")) { "url must include a scheme" }
        val safeUrl = url.replace("'", "'\\''")
        val fmt = if (format == "audio") "audio" else "video"
        val id = UUID.randomUUID().toString()
        val job =
            DownloadJob(
                id = id,
                url = url,
                format = fmt,
                status = "running",
                progress = -1,
                line = "",
                outputFile = null,
                error = null,
                createdAt = System.currentTimeMillis(),
            )
        jobs[id] = job
        val formatArgs =
            if (fmt == "audio") {
                "-x --audio-format mp3 -f 'bestaudio/best' "
            } else {
                "-f 'best[ext=mp4]/best' "
            }
        val command = "mkdir -p ~/Downloads && cd ~/Downloads && yt-dlp --newline --no-playlist $formatArgs'$safeUrl'"
        scope.launch {
            val process =
                try {
                    ubuntuProcess(command = listOf("bash", "-lc", command))
                } catch (e: Exception) {
                    val failed = jobs[id] ?: return@launch
                    failed.status = "error"
                    failed.error = e.message ?: "failed to start download"
                    return@launch
                }
            processes[id] = process
            val errorBuilder = StringBuilder()
            val outputThread =
                Thread {
                    runCatching {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            val current = jobs[id] ?: return@forEachLine
                            current.line = line
                            progressRegex.find(line)?.let { match ->
                                current.progress =
                                    match.groupValues[1].toFloatOrNull()?.toInt()?.coerceIn(0, 100) ?: current.progress
                            }
                            destinationRegex.find(line)?.let { match ->
                                val name = match.groupValues[1].trim().substringAfterLast('/')
                                if (name.isNotBlank()) {
                                    current.outputFile = File(File(sandboxHomeDir(), "Downloads"), name).absolutePath
                                }
                            }
                        }
                    }
                }
            val errorThread =
                Thread {
                    runCatching { process.errorStream.bufferedReader().forEachLine { errorBuilder.appendLine(it) } }
                }
            outputThread.start()
            errorThread.start()
            val exitCode =
                try {
                    process.waitFor()
                } catch (e: Exception) {
                    -1
                }
            outputThread.join()
            errorThread.join()
            processes.remove(id)
            val current = jobs[id] ?: return@launch
            if (current.status == "cancelled") return@launch
            if (exitCode == 0) {
                current.status = "done"
                current.progress = 100
                if (current.outputFile == null) {
                    val newest =
                        File(sandboxHomeDir(), "Downloads").listFiles { f -> f.isFile }?.maxByOrNull { it.lastModified() }
                    current.outputFile = newest?.absolutePath
                }
            } else {
                current.status = "error"
                current.error = errorBuilder.toString().trim().ifEmpty { "download failed with exit code $exitCode" }
            }
        }
        return id
    }

    fun get(id: String): DownloadJob? = jobs[id]

    fun all(): List<DownloadJob> = jobs.values.sortedBy { it.createdAt }

    fun cancel(id: String) {
        val job = jobs[id] ?: return
        val process = processes[id]
        if (process != null) {
            runCatching { process.destroyForcibly() }
        }
        if (job.status == "running") {
            job.status = "cancelled"
        }
    }
}