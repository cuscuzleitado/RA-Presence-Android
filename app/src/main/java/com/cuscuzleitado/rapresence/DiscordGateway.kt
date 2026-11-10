package com.cuscuzleitado.rapresence

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Minimal Discord Gateway client: connects with a user token and keeps a
 * single activity (Rich Presence) set. Handles heartbeat, resume and reconnect.
 */
class DiscordGateway(
    private val client: OkHttpClient,
    private val token: String,
    private val scope: CoroutineScope,
    private val onStatus: (String) -> Unit,
    private val onFatal: () -> Unit,
) {
    companion object {
        private const val GATEWAY = "wss://gateway.discord.gg/?v=10&encoding=json"
        private val FATAL_CODES = setOf(4004, 4010, 4011, 4012, 4013, 4014)
    }

    private val opRe = Regex("\"op\":(\\d+)")
    private val sRe = Regex("\"s\":(\\d+)")
    private val tRe = Regex("\"t\":\"([A-Z_]+)\"")

    @Volatile private var ws: WebSocket? = null
    @Volatile private var seq: Int? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var resumeUrl: String? = null
    @Volatile private var ready = false
    @Volatile private var closedByUser = false
    @Volatile private var ackReceived = true
    @Volatile private var activity: JSONObject? = null

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    fun connect() {
        closedByUser = false
        onStatus("conectando…")
        open(GATEWAY)
    }

    fun close() {
        closedByUser = true
        ready = false
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        val socket = ws
        ws = null
        socket?.close(1000, "bye")
    }

    /** Sets (or clears, with null) the activity shown on the profile. */
    fun setActivity(a: JSONObject?) {
        val same = a?.toString() == activity?.toString()
        activity = a
        if (!same && ready) sendPresence()
    }

    private fun open(url: String) {
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== ws) return
            try {
                handle(text)
            } catch (e: Exception) {
                onStatus("erro no gateway: ${e.message}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== ws) return
            connectionDown(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== ws) return
            connectionDown(null)
        }
    }

    private fun connectionDown(code: Int?) {
        ready = false
        heartbeatJob?.cancel()
        if (closedByUser) return
        if (code != null && code in FATAL_CODES) {
            onStatus(if (code == 4004) "token do Discord inválido, entre de novo" else "conexão recusada pelo Discord ($code)")
            onFatal()
            return
        }
        onStatus("reconectando…")
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000)
            if (closedByUser) return@launch
            val resume = resumeUrl
            if (sessionId != null && !resume.isNullOrEmpty()) {
                open("$resume/?v=10&encoding=json")
            } else {
                open(GATEWAY)
            }
        }
    }

    private fun handle(text: String) {
        // Fast path: dispatch events (op 0) can be big and frequent, so read
        // only the start of the payload instead of parsing the whole JSON.
        val head = text.take(120)
        val fastOp = opRe.find(head)?.groupValues?.get(1)?.toIntOrNull()
        if (fastOp == 0) {
            sRe.find(head)?.groupValues?.get(1)?.toIntOrNull()?.let { seq = it }
            when (tRe.find(head)?.groupValues?.get(1)) {
                "READY" -> onReady(JSONObject(text).getJSONObject("d"))
                "RESUMED" -> markReady()
            }
            return
        }

        val json = JSONObject(text)
        if (!json.isNull("s")) seq = json.getInt("s")
        when (json.getInt("op")) {
            0 -> when (json.optString("t")) {
                "READY" -> onReady(json.getJSONObject("d"))
                "RESUMED" -> markReady()
            }
            1 -> sendHeartbeat()
            7 -> ws?.close(4000, "reconnect requested")
            9 -> {
                val resumable = json.optBoolean("d", false)
                if (!resumable) {
                    sessionId = null
                    seq = null
                }
                ws?.close(4000, "invalid session")
            }
            10 -> {
                val interval = json.getJSONObject("d").getLong("heartbeat_interval")
                startHeartbeat(interval)
                if (sessionId != null && seq != null) resume() else identify()
            }
            11 -> ackReceived = true
        }
    }

    private fun onReady(d: JSONObject) {
        sessionId = d.optString("session_id")
        resumeUrl = d.optString("resume_gateway_url")
        markReady()
    }

    private fun markReady() {
        ready = true
        onStatus("conectado")
        sendPresence()
    }

    private fun startHeartbeat(interval: Long) {
        heartbeatJob?.cancel()
        ackReceived = true
        heartbeatJob = scope.launch {
            delay((interval * Random.nextDouble()).toLong())
            while (isActive) {
                if (!ackReceived) {
                    // Connection went silent: drop it and let onFailure reconnect.
                    ws?.cancel()
                    return@launch
                }
                ackReceived = false
                sendHeartbeat()
                delay(interval)
            }
        }
    }

    private fun send(obj: JSONObject) {
        ws?.send(obj.toString())
    }

    private fun sendHeartbeat() {
        send(JSONObject().put("op", 1).put("d", seq ?: JSONObject.NULL))
    }

    private fun identify() {
        val d = JSONObject()
            .put("token", token)
            .put("capabilities", 16381)
            .put(
                "properties",
                JSONObject()
                    .put("os", "Android")
                    .put("browser", "Discord Android")
                    .put("device", "Android"),
            )
            .put("compress", false)
            .put("presence", presencePayload())
        send(JSONObject().put("op", 2).put("d", d))
    }

    private fun resume() {
        val s = seq ?: 0
        val d = JSONObject()
            .put("token", token)
            .put("session_id", sessionId)
            .put("seq", s)
        send(JSONObject().put("op", 6).put("d", d))
    }

    private fun sendPresence() {
        send(JSONObject().put("op", 3).put("d", presencePayload()))
    }

    private fun presencePayload(): JSONObject {
        val activities = JSONArray()
        activity?.let { activities.put(it) }
        return JSONObject()
            .put("activities", activities)
            .put("afk", true)
            .put("since", 0)
            .put("status", "online")
    }
}
