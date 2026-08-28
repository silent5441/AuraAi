package com.rk.agent

import android.app.Activity
import com.google.gson.JsonParser
import com.rk.exec.ShellUtils
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.settings.Settings

data class ChatSession(
    val id: String,
    val directory: String,
    val title: String,
    val modelId: String,
    val modelProvider: String,
    val cost: Double,
    val tokensInput: Long,
    val tokensOutput: Long,
    val createdMs: Long,
    val updatedMs: Long,
)

data class ProjectMemory(
    val directory: String,
    val sessions: List<ChatSession>,
) {
    val lastUpdatedMs: Long
        get() = sessions.maxOfOrNull { it.updatedMs } ?: 0L
}

/**
 * Read/write access to the opencode chat history ("chat memory") stored per project inside the
 * Ubuntu sandbox at ~/.local/share/opencode/opencode.db.
 *
 * Sessions are keyed by their working directory, so every project keeps its own memory. Because
 * history lives in this database (not in the model), switching models mid-project still continues
 * with full context via `opencode --continue`.
 */
object ChatMemory {

    private const val DB = "\$HOME/.local/share/opencode/opencode.db"

    /** True when an opencode database exists in the sandbox. */
    suspend fun hasDatabase(): Boolean =
        ShellUtils.runUbuntu(
            workingDir = null,
            *bash("test -f $DB"),
            timeoutSeconds = 15L,
        ).exitCode == 0

    private fun bash(command: String): Array<out String> = arrayOf("bash", "-lc", command)

    /** True when an interactive opencode process is currently running (deletion is unsafe then). */
    suspend fun isOpencodeRunning(): Boolean =
        ShellUtils.runUbuntu(
            workingDir = null,
            *bash("pgrep -f '[o]pencode' >/dev/null 2>&1"),
            timeoutSeconds = 15L,
        ).exitCode == 0

    private suspend fun ensureSqlite3(): Boolean =
        ShellUtils.runUbuntu(
            workingDir = null,
            *bash("command -v sqlite3 >/dev/null || { apt-get update -qq && apt-get install -y sqlite3; }"),
            timeoutSeconds = 180L,
        ).exitCode == 0

    /**
     * Runs [sql] through sqlite3 inside the sandbox. The SQL is piped via stdin (base64 encoded)
     * so no shell quoting can break it. Returns trimmed stdout.
     */
    private suspend fun sqlite(sql: String, jsonMode: Boolean, timeoutSeconds: Long = 60L): ShellUtils.Result {
        val b64 =
            android.util.Base64.encodeToString(sql.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val flags = if (jsonMode) "-json " else ""
        val cmd = "printf %s '$b64' | base64 -d | sqlite3 ${flags}\"$DB\""
        return ShellUtils.runUbuntu(workingDir = null, *bash(cmd), timeoutSeconds = timeoutSeconds)
    }

    /**
     * Lists chat sessions, newest first. When [directory] is non-null only sessions of that
     * project (sandbox path, e.g. /home/myapp) are returned.
     */
    suspend fun listSessions(directory: String? = null): List<ChatSession> {
        if (!ensureSqlite3()) return emptyList()

        val where = if (directory != null) "WHERE directory='${esc(directory)}'" else ""
        val sql =
            "SELECT id, directory, title, model, cost, tokens_input, tokens_output, " +
                "time_created, time_updated FROM session $where " +
                "ORDER BY time_updated DESC LIMIT 500;"
        val result = sqlite(sql, jsonMode = true, timeoutSeconds = 30L)
        if (result.exitCode != 0 || result.output.isBlank()) return emptyList()

        return runCatching {
            JsonParser.parseString(result.output).asJsonArray.map { row ->
                val obj = row.asJsonObject
                val model =
                    runCatching { obj.get("model")?.asString }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
                ChatSession(
                    id = obj.get("id").asString,
                    directory = obj.get("directory")?.asString ?: "",
                    title = obj.get("title")?.asString ?: "Untitled",
                    modelId = model?.get("id")?.asString ?: "unknown",
                    modelProvider = model?.get("providerID")?.asString ?: "unknown",
                    cost = obj.get("cost")?.asDouble ?: 0.0,
                    tokensInput = obj.get("tokens_input")?.asLong ?: 0L,
                    tokensOutput = obj.get("tokens_output")?.asLong ?: 0L,
                    createdMs = obj.get("time_created")?.asLong ?: 0L,
                    updatedMs = obj.get("time_updated")?.asLong ?: 0L,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Groups sessions by project directory. */
    suspend fun listProjects(): List<ProjectMemory> =
        listSessions()
            .groupBy { it.directory.ifBlank { "/" } }
            .map { (dir, sessions) -> ProjectMemory(dir, sessions) }
            .sortedByDescending { it.lastUpdatedMs }

    private val AUX_TABLES =
        listOf("todo", "session_input", "session_share", "session_context_epoch", "session_diff")

    /**
     * Deletes sessions permanently. Fails (returns failure) when an interactive opencode process
     * is running, because writing to its live database could corrupt it.
     */
    suspend fun deleteSessions(ids: List<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        if (isOpencodeRunning()) {
            return Result.failure(IllegalStateException("opencode running"))
        }
        if (!ensureSqlite3()) {
            return Result.failure(IllegalStateException("sqlite3 unavailable"))
        }

        // Auxiliary tables vary between opencode versions; only delete the ones that exist.
        val tableList = AUX_TABLES.joinToString(",") { "'$it'" }
        val existingResult = sqlite("SELECT name FROM sqlite_master WHERE type='table' AND name IN ($tableList);", jsonMode = false)
        val existing =
            if (existingResult.exitCode == 0) {
                existingResult.output.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else emptySet()

        val sql =
            ids.joinToString("") { id ->
                val sid = esc(id)
                existing.joinToString("") { table -> "DELETE FROM $table WHERE session_id='$sid';" } +
                    "DELETE FROM part WHERE session_id='$sid';" +
                    "DELETE FROM message WHERE session_id='$sid';" +
                    "DELETE FROM session_message WHERE session_id='$sid';" +
                    "DELETE FROM session WHERE id='$sid';"
            }
        val result = sqlite(sql, jsonMode = false)
        return if (result.exitCode == 0) Result.success(Unit)
        else Result.failure(RuntimeException(result.error.ifBlank { "delete failed" }))
    }

    /** Continues [session]'s project chat (latest memory) in a new terminal. */
    fun continueSession(activity: Activity, session: ChatSession) {
        launchTerminal(
            activity,
            TerminalCommand(
                sandbox = true,
                exe = "opencode",
                args = arrayOf("--continue"),
                id = "opencode",
                workingDir = session.directory.takeIf { it.isNotBlank() && it != "/" },
                env = arrayOf("XED_BRIDGE_PORT=${Settings.agent_bridge_port}"),
            ),
        )
    }

    /** Starts opencode for [directory]; with [continueLatest] it resumes the newest session. */
    fun openProjectChat(activity: Activity, directory: String?, continueLatest: Boolean) {
        launchTerminal(
            activity,
            TerminalCommand(
                sandbox = true,
                exe = "opencode",
                args = if (continueLatest) arrayOf("--continue") else arrayOf(),
                id = "opencode",
                workingDir = directory?.takeIf { it.isNotBlank() && it != "/" },
                env = arrayOf("XED_BRIDGE_PORT=${Settings.agent_bridge_port}"),
            ),
        )
    }

    suspend fun maxSessionTime(): Long =
        runCatching {
            sqlite("SELECT COALESCE(MAX(time_updated),0) FROM session;", jsonMode = false, timeoutSeconds = 20L)
                .output.trim().toLongOrNull()
        }.getOrNull() ?: 0L

    suspend fun renameSession(id: String, newTitle: String): Result<Unit> {
        val title = newTitle.trim()
        if (title.isEmpty()) return Result.failure(IllegalArgumentException("empty title"))
        if (isOpencodeRunning()) return Result.failure(IllegalStateException("opencode running"))
        if (!ensureSqlite3()) return Result.failure(IllegalStateException("sqlite3 unavailable"))
        val sql = "UPDATE session SET title='${esc(title)}' WHERE id='${esc(id)}';"
        val result = sqlite(sql, jsonMode = false)
        return if (result.exitCode == 0) Result.success(Unit)
        else Result.failure(RuntimeException(result.error.ifBlank { "rename failed" }))
    }

    suspend fun messageCounts(): Map<String, Int> {
        if (!ensureSqlite3()) return emptyMap()
        val result =
            runCatching {
                sqlite(
                    "SELECT session_id, COUNT(*) FROM message GROUP BY session_id;",
                    jsonMode = false,
                    timeoutSeconds = 30L,
                )
            }.getOrNull() ?: return emptyMap()
        if (result.exitCode != 0) return emptyMap()
        return result.output.lines().mapNotNull { line ->
            val parts = line.trim().split("|")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()
    }

    suspend fun newestSessionSince(beforeMs: Long): ChatSession? =
        runCatching { listSessions().firstOrNull { it.createdMs > beforeMs } }.getOrNull()

    /**
     * Exports a session as a Markdown conversation transcript (user/assistant turns with text
     * parts). Reasoning/tool parts are skipped.
     */
    suspend fun exportMarkdown(session: ChatSession): Result<String> {
        if (!ensureSqlite3()) return Result.failure(IllegalStateException("sqlite3 unavailable"))
        val sid = esc(session.id)

        val msgRes =
            runCatching {
                sqlite(
                    "SELECT id,data FROM message WHERE session_id='$sid' ORDER BY time_created ASC;",
                    jsonMode = true,
                    timeoutSeconds = 60L,
                )
            }.getOrNull() ?: return Result.failure(RuntimeException("query failed"))
        val partRes =
            runCatching {
                sqlite(
                    "SELECT message_id,data FROM part WHERE session_id='$sid' ORDER BY time_created ASC;",
                    jsonMode = true,
                    timeoutSeconds = 60L,
                )
            }.getOrNull() ?: return Result.failure(RuntimeException("query failed"))
        if (msgRes.exitCode != 0 || partRes.exitCode != 0) {
            return Result.failure(RuntimeException((msgRes.error + " " + partRes.error).ifBlank { "query failed" }))
        }

        return runCatching {
            data class Msg(var role: String, val text: StringBuilder)
            val order = ArrayList<String>()
            val msgs = LinkedHashMap<String, Msg>()
            if (msgRes.output.isNotBlank()) {
                JsonParser.parseString(msgRes.output).asJsonArray.forEach { row ->
                    val obj = row.asJsonObject
                    val id = obj.get("id").asString
                    val data = runCatching { JsonParser.parseString(obj.get("data").asString ?: "{}") }.getOrNull()
                        ?: JsonParser.parseString("{}")
                    val role = runCatching { data.asJsonObject.get("role")?.asString }.getOrNull() ?: "user"
                    msgs[id] = Msg(role, StringBuilder())
                    order.add(id)
                }
            }
            if (partRes.output.isNotBlank()) {
                JsonParser.parseString(partRes.output).asJsonArray.forEach { row ->
                    val obj = row.asJsonObject
                    val mid = obj.get("message_id").asString
                    val msg = msgs[mid] ?: return@forEach
                    val data =
                        runCatching { JsonParser.parseString(obj.get("data").asString ?: "{}").asJsonObject }
                            .getOrNull() ?: return@forEach
                    val type = runCatching { data.get("type")?.asString }.getOrNull()
                    if (type == "text") {
                        val text = runCatching { data.get("text")?.asString }.getOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            if (msg.text.isNotEmpty()) msg.text.append("\n\n")
                            msg.text.append(text)
                        }
                    }
                }
            }

            val sb = StringBuilder()
            sb.append("# ").append(session.title).append('\n')
            sb.append("> project: `").append(session.directory).append("`\n")
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).let { fmt ->
                sb.append("> started: ").append(fmt.format(java.util.Date(session.createdMs))).append('\n')
            }
            sb.append('\n')
            order.forEach { id ->
                val msg = msgs[id]!!
                if (msg.text.isBlank()) return@forEach
                sb.append("## ").append(if (msg.role == "assistant") "assistant" else "user").append("\n\n")
                sb.append(msg.text).append("\n\n")
            }
            sb.toString()
        }
    }

    private fun esc(value: String): String = value.replace("'", "''")

    fun formatRelativeTime(ms: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (ms <= 0) return "?"
        val diff = ((nowMs - ms) / 1000L).coerceAtLeast(0)
        val minutes = diff / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            diff < 60 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 30 -> "${days}d ago"
            else -> "${days / 30}mo ago"
        }
    }

    fun formatTokens(tokens: Long): String =
        when {
            tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
            tokens >= 1_000 -> "%.1fk".format(tokens / 1_000.0)
            else -> tokens.toString()
        }
}
