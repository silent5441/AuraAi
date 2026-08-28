package com.rk.agent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.rk.exec.ShellUtils
import com.rk.file.sandboxHomeDir
import com.rk.resources.drawables
import com.rk.settings.Settings
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.File
import java.net.URLConnection
import kotlin.math.absoluteValue
import kotlinx.coroutines.runBlocking

object AgentBridge {
    private val lock = Any()
    private var server: AgentHttpServer? = null

    val isRunning: Boolean
        get() = server?.isAlive == true

    fun port(): Int = Settings.agent_bridge_port

    fun start(context: Context): Boolean {
        synchronized(lock) {
            if (server?.isAlive == true) return true
            val newServer = AgentHttpServer(port(), context.applicationContext)
            return try {
                newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = newServer
                Log.i("AgentBridge", "started on 127.0.0.1:${newServer.port}")
                newServer.isAlive
            } catch (e: Exception) {
                Log.e("AgentBridge", "failed to start on port ${port()}", e)
                server = null
                false
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            server?.stop()
            server = null
        }
    }

    fun restart(context: Context) {
        stop()
        start(context)
    }
}

class AgentHttpServer(val port: Int, val context: Context) : NanoHTTPD("127.0.0.1", port) {
    private val gson = Gson()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaKeyCodes =
        mapOf(
            "play" to KeyEvent.KEYCODE_MEDIA_PLAY,
            "pause" to KeyEvent.KEYCODE_MEDIA_PAUSE,
            "toggle" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            "next" to KeyEvent.KEYCODE_MEDIA_NEXT,
            "previous" to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            "stop" to KeyEvent.KEYCODE_MEDIA_STOP,
        )

    override fun useGzipWhenAccepted(r: Response?): Boolean = false

    override fun serve(session: IHTTPSession?): Response {
        return runBlocking {
            val s = session ?: return@runBlocking fail(Status.BAD_REQUEST, "null session")
            try {
                dispatch(s)
            } catch (e: JsonSyntaxException) {
                fail(Status.BAD_REQUEST, "invalid json body: ${e.message}")
            } catch (e: IllegalArgumentException) {
                fail(Status.BAD_REQUEST, e.message ?: "bad request")
            } catch (e: Exception) {
                fail(Status.INTERNAL_ERROR, e.message ?: "internal error")
            }
        }
    }

    private suspend fun dispatch(s: IHTTPSession): Response {
        val method = s.method
        val path = s.uri.trimEnd('/').ifEmpty { "/" }
        return when {
            path == "/health" && method == Method.GET -> health()
            path == "/volume" && method == Method.GET -> volumeResponse()
            path == "/volume" && method == Method.POST -> applyVolume(postBody(s))
            path == "/media" && method == Method.POST -> mediaAction(postBody(s))
            path == "/notify" && method == Method.POST -> notify(postBody(s))
            path == "/clipboard" && method == Method.GET -> clipboardGet()
            path == "/clipboard" && method == Method.POST -> clipboardSet(postBody(s))
            path == "/device" && method == Method.GET -> device()
            path == "/open" && method == Method.POST -> open(postBody(s))
            path == "/shell" && method == Method.POST -> shell(postBody(s))
            path == "/files/list" && method == Method.GET -> filesList(s.parms["path"])
            path == "/files/read" && method == Method.GET -> filesRead(s.parms["path"], s.parms["limit"])
            path == "/files/to-shared" && method == Method.POST -> filesToShared(postBody(s))
            path == "/download" && method == Method.POST -> download(postBody(s))
            path == "/download" && method == Method.GET -> downloadStatus(s.parms["id"])
            path == "/downloads" && method == Method.GET -> downloadsList()
            path == "/download/cancel" && method == Method.POST -> downloadCancel(postBody(s))
            else -> fail(Status.NOT_FOUND, "not found: ${method.name} $path")
        }
    }

    private fun health(): Response = ok("port" to port, "version" to "3.3.1")

    private fun volumeResponse(): Response =
        ok(
            "stream" to "music",
            "level" to audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            "max" to audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            "muted" to audioManager.isStreamMute(AudioManager.STREAM_MUSIC),
        )

    private fun applyVolume(obj: JsonObject): Response {
        if (obj.has("mute")) {
            val mute = obj.get("mute").asBoolean
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0,
            )
        }
        if (obj.has("level")) {
            val level = obj.get("level").asInt
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, max), 0)
        }
        if (obj.has("step")) {
            val step = obj.get("step").asInt
            val direction = if (step > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            repeat(step.absoluteValue.coerceAtMost(100)) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            }
        }
        return volumeResponse()
    }

    private fun mediaAction(obj: JsonObject): Response {
        val action =
            obj.get("action")?.takeIf { !it.isJsonNull }?.asString ?: throw IllegalArgumentException("missing action")
        val keyCode = mediaKeyCodes[action] ?: throw IllegalArgumentException("unknown action: $action")
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return ok("action" to action)
    }

    private fun notify(obj: JsonObject): Response {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return fail(Status.BAD_REQUEST, "notifications not granted")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return fail(Status.BAD_REQUEST, "notifications not granted")
        }
        val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: throw IllegalArgumentException("missing title")
        val text = obj.get("text")?.takeIf { !it.isJsonNull }?.asString ?: throw IllegalArgumentException("missing text")
        val channel = NotificationChannel("agent", "Agent", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        val notification =
            NotificationCompat.Builder(context, "agent")
                .setSmallIcon(drawables.terminal)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        return ok()
    }

    private fun clipboardGet(): Response {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        return ok("text" to text)
    }

    private fun clipboardSet(obj: JsonObject): Response {
        val text = obj.get("text")?.takeIf { !it.isJsonNull }?.asString ?: throw IllegalArgumentException("missing text")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("xed-agent", text))
        return ok()
    }

    private fun device(): Response {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val battery = if (scale > 0 && level >= 0) level * 100 / scale else -1
        val plugged = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val status =
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val charging =
            plugged ||
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        val storageFree = statFs.availableBlocksLong * statFs.blockSizeLong
        val storageTotal = statFs.blockCountLong * statFs.blockSizeLong
        var wifi = false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        if (network != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (!wifi) {
            runCatching {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifi = wifiManager.isWifiEnabled
            }
        }
        return ok(
            "battery" to battery,
            "batteryCharging" to charging,
            "storageFree" to storageFree,
            "storageTotal" to storageTotal,
            "wifi" to wifi,
        )
    }

    private fun open(obj: JsonObject): Response {
        when {
            obj.has("url") -> {
                val url = obj.get("url").asString
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }.getOrElse {
                    return fail(Status.BAD_REQUEST, it.message ?: "failed to open url")
                }
                return ok("target" to "url")
            }
            obj.has("package") -> {
                val pkg = obj.get("package").asString
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent == null) {
                    return fail(Status.BAD_REQUEST, "package not found: $pkg")
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }.getOrElse {
                    return fail(Status.BAD_REQUEST, it.message ?: "failed to open package")
                }
                return ok("target" to "package")
            }
            else -> throw IllegalArgumentException("provide \"url\" or \"package\"")
        }
    }

    private suspend fun shell(obj: JsonObject): Response {
        val command =
            obj.get("command")?.takeIf { !it.isJsonNull }?.asString ?: throw IllegalArgumentException("missing command")
        val timeout = obj.get("timeout")?.takeIf { !it.isJsonNull }?.asInt?.coerceIn(1, 600) ?: 120
        val result = ShellUtils.runUbuntu("bash", "-lc", command, timeoutSeconds = timeout.toLong())
        return ok(
            "exitCode" to result.exitCode,
            "output" to result.output.take(200 * 1024),
            "error" to result.error.take(200 * 1024),
            "timedOut" to result.timedOut,
        )
    }

    private fun filesList(pathParam: String?): Response {
        val dir = resolveAllowedPath(pathParam) ?: throw IllegalArgumentException("path not allowed")
        if (!dir.isDirectory) throw IllegalArgumentException("not a directory: ${dir.absolutePath}")
        val entries =
            dir.listFiles()
                .orEmpty()
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map {
                    mapOf(
                        "name" to it.name,
                        "path" to it.absolutePath,
                        "isDirectory" to it.isDirectory,
                        "size" to it.length(),
                        "lastModified" to it.lastModified(),
                    )
                }
        return ok("path" to dir.absolutePath, "entries" to entries)
    }

    private fun filesRead(pathParam: String?, limitParam: String?): Response {
        val file = resolveAllowedPath(pathParam) ?: throw IllegalArgumentException("path not allowed")
        if (!file.isFile) throw IllegalArgumentException("not a file: ${file.absolutePath}")
        val limit = limitParam?.toIntOrNull()?.coerceIn(1, 10 * 1024 * 1024) ?: 262144
        val bytes =
            file.inputStream().use { ins ->
                val buf = ByteArray(limit)
                val n = ins.read(buf)
                if (n <= 0) ByteArray(0) else buf.copyOf(n)
            }
        return ok("path" to file.absolutePath, "content" to bytes.toString(Charsets.UTF_8))
    }

    private fun filesToShared(obj: JsonObject): Response {
        val path = obj.get("path")?.takeIf { !it.isJsonNull }?.asString ?: throw IllegalArgumentException("missing path")
        val file = resolveAllowedPath(path) ?: throw IllegalArgumentException("path not allowed")
        if (!file.isFile) throw IllegalArgumentException("not a file: ${file.absolutePath}")
        val displayName = file.name
        val resolver = context.contentResolver
        val relativeDir = Environment.DIRECTORY_DOWNLOADS + "/XedAgent"
        val mime = URLConnection.guessContentTypeFromName(displayName) ?: "application/octet-stream"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalArgumentException("failed to create media entry")
            val out = resolver.openOutputStream(uri) ?: throw IllegalArgumentException("failed to open output stream")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return ok("uri" to uri.toString(), "displayName" to displayName, "size" to file.length())
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "XedAgent")
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, displayName)
        file.inputStream().use { ins -> dest.outputStream().use { ins.copyTo(it) } }
        return ok("uri" to Uri.fromFile(dest).toString(), "displayName" to displayName, "size" to dest.length())
    }

    private fun download(obj: JsonObject): Response {
        val url = obj.get("url")?.takeIf { !it.isJsonNull }?.asString ?: throw IllegalArgumentException("missing url")
        val format = obj.get("format")?.takeIf { !it.isJsonNull }?.asString ?: "video"
        val id = AgentDownloads.start(url, format)
        return ok("id" to id)
    }

    private fun downloadsList(): Response = ok("jobs" to AgentDownloads.all())

    private fun downloadStatus(idParam: String?): Response {
        if (idParam.isNullOrEmpty()) return fail(Status.BAD_REQUEST, "missing id")
        val job = AgentDownloads.get(idParam)
        if (job == null) return fail(Status.NOT_FOUND, "job not found")
        return ok("job" to job)
    }

    private fun downloadCancel(obj: JsonObject): Response {
        val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: throw IllegalArgumentException("missing id")
        AgentDownloads.cancel(id)
        return ok()
    }

    private fun resolveAllowedPath(pathParam: String?): File? {
        val home = sandboxHomeDir()
        val sdcard = File("/sdcard")
        val raw =
            when {
                pathParam.isNullOrEmpty() || pathParam == "~" -> home
                pathParam.startsWith("~/") -> File(home, pathParam.removePrefix("~/"))
                else -> File(pathParam)
            }
        val canonical = runCatching { raw.canonicalFile }.getOrNull() ?: return null
        val homeCanonical = runCatching { home.canonicalFile }.getOrNull() ?: return null
        val sdcardCanonical = runCatching { sdcard.canonicalFile }.getOrNull() ?: return null
        fun isUnder(root: File): Boolean =
            canonical == root || canonical.absolutePath.startsWith(root.absolutePath + File.separator)
        return if (isUnder(homeCanonical) || isUnder(sdcardCanonical)) canonical else null
    }

    private fun postBody(s: IHTTPSession): JsonObject {
        val body = readBody(s)
        if (body.isBlank()) return JsonObject()
        return JsonParser.parseString(body).asJsonObject
    }

    private fun readBody(s: IHTTPSession, maxBytes: Int = 2 * 1024 * 1024): String {
        val contentLength = s.headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength > maxBytes) throw IllegalArgumentException("request body too large")
        val bytes = s.inputStream.readBytes()
        if (bytes.size > maxBytes) throw IllegalArgumentException("request body too large")
        return bytes.toString(Charsets.UTF_8)
    }

    private fun ok(vararg pairs: Pair<String, Any?>): Response {
        val map = LinkedHashMap<String, Any?>()
        map["ok"] = true
        for ((key, value) in pairs) map[key] = value
        return json(Status.OK, map)
    }

    private fun fail(status: Status, error: String): Response = json(status, mapOf("ok" to false, "error" to error))

    private fun json(status: Status, obj: Any): Response =
        newFixedLengthResponse(status, "application/json", gson.toJson(obj))
}