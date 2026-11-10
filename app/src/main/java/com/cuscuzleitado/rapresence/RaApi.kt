package com.cuscuzleitado.rapresence

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RaSummary(val lastGameId: Long, val richPresence: String?, val richPresenceDate: String?)
data class RaGame(val title: String, val consoleName: String, val imageIcon: String)

class RaApi(
    private val http: OkHttpClient,
    private val user: String,
    private val apiKey: String,
) {
    private fun get(endpoint: String, vararg extra: Pair<String, String>): JSONObject {
        val url = "https://retroachievements.org/API/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("z", user)
            .addQueryParameter("y", apiKey)
            .apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (res.code == 401 || res.code == 403) {
                throw IllegalStateException("usuário ou chave da API do RA inválidos")
            }
            if (!res.isSuccessful) throw IllegalStateException("API do RA respondeu ${res.code}")
            return JSONObject(res.body!!.string())
        }
    }

    fun summary(): RaSummary {
        val o = get("API_GetUserSummary.php", "u" to user, "g" to "0", "a" to "0")
        return RaSummary(
            lastGameId = o.optLong("LastGameID", 0L),
            richPresence = if (o.isNull("RichPresenceMsg")) null else o.optString("RichPresenceMsg"),
            richPresenceDate = if (o.isNull("RichPresenceMsgDate")) null else o.optString("RichPresenceMsgDate"),
        )
    }

    fun game(id: Long): RaGame {
        val o = get("API_GetGame.php", "i" to id.toString())
        return RaGame(
            title = o.optString("Title"),
            consoleName = o.optString("ConsoleName"),
            imageIcon = o.optString("ImageIcon"),
        )
    }
}
