package com.cuscuzleitado.rapresence

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class DiscordApi(private val http: OkHttpClient, private val token: String) {

    /**
     * Discord only accepts images from the application's assets or from its
     * "external assets" proxy. This registers an external URL and returns the
     * "mp:external/..." key to use as large_image, or null if it fails.
     */
    fun resolveExternalImage(url: String): String? {
        val body = JSONObject().put("urls", JSONArray().put(url)).toString()
        val req = Request.Builder()
            .url("https://discord.com/api/v9/applications/${Config.DISCORD_CLIENT_ID}/external-assets")
            .header("Authorization", token)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val arr = JSONArray(res.body!!.string())
            if (arr.length() == 0) return null
            return "mp:" + arr.getJSONObject(0).getString("external_asset_path")
        }
    }
}
