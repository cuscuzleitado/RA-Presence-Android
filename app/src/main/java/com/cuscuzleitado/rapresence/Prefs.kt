package com.cuscuzleitado.rapresence

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("ra_presence", Context.MODE_PRIVATE)

    var raUser: String
        get() = sp.getString("ra_user", "") ?: ""
        set(v) { sp.edit().putString("ra_user", v.trim()).apply() }

    var raApiKey: String
        get() = sp.getString("ra_api_key", "") ?: ""
        set(v) { sp.edit().putString("ra_api_key", v.trim()).apply() }

    var discordToken: String
        get() = sp.getString("discord_token", "") ?: ""
        set(v) { sp.edit().putString("discord_token", v.trim().trim('"')).apply() }

    var showButton: Boolean
        get() = sp.getBoolean("show_button", true)
        set(v) { sp.edit().putBoolean("show_button", v).apply() }
}
