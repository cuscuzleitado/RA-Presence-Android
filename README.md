# RA Presence (Android)

Android app that shows the game you are playing on [RetroAchievements](https://retroachievements.org) as Discord Rich Presence, straight from your phone. Companion to the Node script in `RetroAchievements-Rich-Presence`, but with no PC needed.

> Not affiliated with RetroAchievements or Discord.

## How it works

1. You enter your RA username and Web API key (retroachievements.org > Settings > Keys). They stay on your device.
2. You log in to Discord inside the app.
3. A foreground service checks `API_GetUserSummary` every 30 seconds and sends the game title, icon, RA Rich Presence text and elapsed time to Discord through the Gateway.

Only the Discord Application ID is built in (`Config.kt`).

## Important

This sets your status using your **account token**, the same way Kizzy does. That is against Discord's Terms of Service and can get an account limited or banned. Use at your own risk, preferably with a secondary account. The token is stored only in the app's private storage and is sent only to Discord.

## Build

- **Android Studio:** open this folder, let it sync, then Build > Build APK(s).
- **GitHub Actions:** push to `main`; the APK shows up as an artifact of the "Build APK" workflow (`app-debug.apk`).

Requires Android 8.0+ (API 26). Install the APK by allowing installs from unknown sources.

## Tips

- Allow notifications (Android 13+) and set the app's battery usage to "Unrestricted" so the service is not killed.
- RA only updates every couple of minutes, so there is a small delay when you start or close a game.
- If the Discord login does not pick up your session, paste your token in the manual field.
