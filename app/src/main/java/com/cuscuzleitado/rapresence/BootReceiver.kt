package com.cuscuzleitado.rapresence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the presence service after the phone boots, if enabled and everything is configured. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = Prefs(context)
        if (p.autoStart && p.raUser.isNotBlank() && p.raApiKey.isNotBlank() && p.discordToken.isNotBlank()) {
            PresenceService.start(context)
        }
    }
}
