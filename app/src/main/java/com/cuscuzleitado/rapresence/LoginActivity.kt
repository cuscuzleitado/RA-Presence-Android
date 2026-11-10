package com.cuscuzleitado.rapresence

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONTokener

/** Opens the Discord login page and picks up the account token once logged in. */
class LoginActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            tryExtract()
            handler.postDelayed(this, 2000)
        }
    }

    private val js = """
        (function(){
          try {
            var i = document.createElement('iframe');
            document.body.appendChild(i);
            var t = i.contentWindow.localStorage.getItem('token');
            i.remove();
            if (t) { return t.replace(/^"|"$/g, ''); }
          } catch (e) {}
          try {
            var m = [];
            webpackChunkdiscord_app.push([[Symbol()], {}, function(r){ for (var c in r.c) m.push(r.c[c]); }]);
            var x = m.find(function(y){ return y && y.exports && y.exports.default && y.exports.default.getToken !== undefined; });
            if (x) { return x.exports.default.getToken() || ''; }
          } catch (e) {}
          return '';
        })()
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.loadUrl("https://discord.com/login")
        handler.postDelayed(poll, 3000)
    }

    private fun tryExtract() {
        web.evaluateJavascript(js) { raw ->
            if (done || raw == null) return@evaluateJavascript
            val token = try {
                JSONTokener(raw).nextValue() as? String
            } catch (e: Exception) {
                null
            }
            if (!token.isNullOrBlank() && token.length > 30) {
                done = true
                Prefs(this).discordToken = token
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        web.destroy()
        super.onDestroy()
    }
}
