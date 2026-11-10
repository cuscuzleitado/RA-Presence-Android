package com.cuscuzleitado.rapresence

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var etUser: TextInputEditText
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etToken: TextInputEditText
    private lateinit var tvAccount: TextView
    private lateinit var tvDiscord: TextView
    private lateinit var tvRa: TextView
    private lateinit var btnToggle: MaterialButton

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Service runs even if the notification is denied.
            PresenceService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        etUser = findViewById(R.id.etUser)
        etApiKey = findViewById(R.id.etApiKey)
        etToken = findViewById(R.id.etToken)
        tvAccount = findViewById(R.id.tvAccount)
        tvDiscord = findViewById(R.id.tvDiscord)
        tvRa = findViewById(R.id.tvRa)
        btnToggle = findViewById(R.id.btnToggle)
        val swButton = findViewById<MaterialSwitch>(R.id.swButton)

        etUser.setText(prefs.raUser)
        etApiKey.setText(prefs.raApiKey)
        swButton.isChecked = prefs.showButton

        swButton.setOnCheckedChangeListener { _, checked -> prefs.showButton = checked }

        val swAutoStart = findViewById<MaterialSwitch>(R.id.swAutoStart)
        swAutoStart.isChecked = prefs.autoStart
        swAutoStart.setOnCheckedChangeListener { _, checked -> prefs.autoStart = checked }

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            saveFields()
            startActivity(Intent(this, LoginActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { logout() }
        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        btnToggle.setOnClickListener {
            if (PresenceService.running.value) PresenceService.stop(this) else onStartClicked()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    PresenceService.running.collect {
                        btnToggle.text = if (it) "Parar" else "Iniciar"
                    }
                }
                launch { PresenceService.discordStatus.collect { tvDiscord.text = "Discord: $it" } }
                launch { PresenceService.raStatus.collect { tvRa.text = "RetroAchievements: $it" } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccount()
    }

    private fun refreshAccount() {
        tvAccount.text = if (prefs.discordToken.isNotBlank()) {
            "Conta do Discord conectada"
        } else {
            "Conta do Discord não conectada"
        }
    }

    private fun saveFields() {
        prefs.raUser = etUser.text?.toString().orEmpty()
        prefs.raApiKey = etApiKey.text?.toString().orEmpty()
    }

    private fun logout() {
        prefs.discordToken = ""
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        PresenceService.stop(this)
        refreshAccount()
    }

    private fun onStartClicked() {
        saveFields()
        val manual = etToken.text?.toString().orEmpty().trim()
        if (manual.isNotEmpty()) {
            prefs.discordToken = manual
            etToken.setText("")
            refreshAccount()
        }
        when {
            prefs.raUser.isBlank() || prefs.raApiKey.isBlank() ->
                toast("Preencha o usuário e a chave da API do RetroAchievements.")
            prefs.discordToken.isBlank() ->
                toast("Entre no Discord primeiro.")
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED ->
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> PresenceService.start(this)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
