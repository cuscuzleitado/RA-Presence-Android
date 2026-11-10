package com.cuscuzleitado.rapresence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class PresenceService : Service() {

    companion object {
        const val ACTION_STOP = "com.cuscuzleitado.rapresence.STOP"
        private const val CHANNEL_ID = "presence"
        private const val NOTIF_ID = 1

        val running = MutableStateFlow(false)
        val discordStatus = MutableStateFlow("desconectado")
        val raStatus = MutableStateFlow("parado")

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PresenceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PresenceService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val wsHttp = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var gateway: DiscordGateway? = null
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground("Iniciando…")
        if (pollJob == null) begin()
        return START_STICKY
    }

    override fun onDestroy() {
        running.value = false
        discordStatus.value = "desconectado"
        raStatus.value = "parado"
        gateway?.close()
        gateway = null
        scope.cancel()
        super.onDestroy()
    }

    private fun begin() {
        val prefs = Prefs(this)
        val user = prefs.raUser
        val key = prefs.raApiKey
        val token = prefs.discordToken
        if (user.isBlank() || key.isBlank() || token.isBlank()) {
            raStatus.value = "faltam dados (usuário, chave da API ou login do Discord)"
            stopSelf()
            return
        }

        running.value = true
        raStatus.value = "aguardando…"

        val gw = DiscordGateway(
            client = wsHttp,
            token = token,
            scope = scope,
            onStatus = { discordStatus.value = it },
            onFatal = { stopSelf() },
        )
        gateway = gw
        gw.connect()

        val ra = RaApi(http, user, key)
        val discordApi = DiscordApi(http, token)

        pollJob = scope.launch {
            val gameCache = mutableMapOf<Long, RaGame>()
            val imageCache = mutableMapOf<Long, String>()
            var currentGameId = 0L
            var startedAt = 0L

            while (isActive) {
                try {
                    val s = ra.summary()
                    val playing = s.lastGameId != 0L &&
                        !s.richPresence.isNullOrBlank() &&
                        isRecent(s.richPresenceDate)

                    if (!playing) {
                        currentGameId = 0L
                        gw.setActivity(null)
                        raStatus.value = "nenhum jogo ativo no RA"
                        updateNotification("Aguardando um jogo no RetroAchievements")
                    } else {
                        val game = gameCache.getOrPut(s.lastGameId) { ra.game(s.lastGameId) }
                        if (currentGameId != s.lastGameId) {
                            currentGameId = s.lastGameId
                            startedAt = System.currentTimeMillis()
                        }
                        val image = imageCache[s.lastGameId]
                            ?: discordApi
                                .resolveExternalImage("https://media.retroachievements.org" + game.imageIcon)
                                ?.also { imageCache[s.lastGameId] = it }

                        gw.setActivity(
                            buildActivity(game, s.richPresence!!, image, startedAt, user, Prefs(this@PresenceService).showButton),
                        )
                        raStatus.value = "jogando ${game.title} (${game.consoleName})"
                        updateNotification("Jogando: ${game.title}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    raStatus.value = "erro: ${e.message}"
                }
                delay(Config.POLL_SECONDS * 1000)
            }
        }
    }

    private fun isRecent(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = try {
            fmt.parse(raw)
        } catch (e: Exception) {
            null
        } ?: return false
        return System.currentTimeMillis() - date.time < Config.ACTIVE_WINDOW_MINUTES * 60_000
    }

    /** Removes the game title the RA text repeats at the end (or the whole text if it is just the title). */
    private fun cleanRichPresence(raw: String, title: String): String {
        val t = title.trim()
        var s = raw.trim()
        if (t.isEmpty()) return s
        val q = Regex.escape(t)
        val only = Regex("^(?:Playing\\s+)?$q$", RegexOption.IGNORE_CASE)
        if (only.containsMatchIn(s)) return ""
        val tail = Regex("\\s*\\|\\s*(?:Playing\\s+)?$q\\s*$", RegexOption.IGNORE_CASE)
        s = tail.replace(s, "").trim().trimEnd('|').trim()
        return s
    }

    private fun buildActivity(
        game: RaGame,
        state: String,
        image: String?,
        startedAt: Long,
        user: String,
        showButton: Boolean,
    ): JSONObject {
        val assets = JSONObject().put("large_text", "${game.title} (${game.consoleName})")
        if (image != null) assets.put("large_image", image)

        val a = JSONObject()
            .put("type", 0)
            .put("name", game.title)
            .put("application_id", Config.DISCORD_CLIENT_ID)
            .put("timestamps", JSONObject().put("start", startedAt))
            .put("assets", assets)

        val details = cleanRichPresence(state, game.title)
        if (details.isNotBlank()) a.put("details", details.take(128))
        if (game.consoleName.isNotBlank()) a.put("state", "(${game.consoleName})")

        if (showButton) {
            a.put("buttons", JSONArray().put("Perfil no RetroAchievements"))
            a.put(
                "metadata",
                JSONObject().put(
                    "button_urls",
                    JSONArray().put("https://retroachievements.org/user/$user"),
                ),
            )
        }
        return a
    }

    private fun goForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Presença no Discord", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val n = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            raStatus.value = "não foi possível iniciar em segundo plano: ${e.message}"
            stopSelf()
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, PresenceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("RA Presence")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Parar", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
