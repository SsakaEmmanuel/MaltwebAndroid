package com.maltweb.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * MALTWEB Android user app with a mandatory GitHub Releases update gate.
 * A release tag such as v1.1.0 must match app versionName 1.1.0.
 * Update checks use GitHub's public latest-release API.
 */
class MainActivity : ComponentActivity() {
    private val websiteUrl = "https://ssakaemmanuel.github.io/maltweb/"
    private val latestReleaseApi =
        "https://api.github.com/repos/SsakaEmmanuel/MaltwebAndroid/releases/latest"
    private val io = Executors.newSingleThreadExecutor()
    private var webView: WebView? = null
    private var updateDialog: AlertDialog? = null
    private var isChecking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showCheckingScreen()
        checkForRequiredUpdate()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView
                if (wv != null && wv.canGoBack()) wv.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showCheckingScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(247, 249, 247))
        }
        layout.addView(TextView(this).apply {
            text = "MALTWEB"
            textSize = 28f
            setTextColor(Color.rgb(18, 107, 80))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = "Checking for required updates…"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(12), 0, dp(18))
        })
        layout.addView(ProgressBar(this))
        setContentView(layout)
    }

    private fun checkForRequiredUpdate() {
        if (isChecking || isFinishing) return
        isChecking = true
        io.execute {
            try {
                val connection = (URL(latestReleaseApi).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "MALTWEB-Android")
                }
                val response = try {
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("GitHub returned HTTP ${connection.responseCode}")
                    }
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                val release = JSONObject(response)
                val tag = release.optString("tag_name", "").trim()
                val releaseUrl = release.optString("html_url", "").trim()
                val notes = release.optString("body", "").trim()
                val assets = release.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            if (name.equals("MALTWEB.apk", ignoreCase = true)) break
                        }
                    }
                }
                if (tag.isBlank() || releaseUrl.isBlank() || apkUrl.isBlank()) {
                    throw IllegalStateException("The latest GitHub Release does not contain an APK asset.")
                }
                val current = BuildConfig.VERSION_NAME
                val newer = compareVersions(tag.removePrefix("v"), current) > 0
                runOnUiThread {
                    isChecking = false
                    if (isFinishing) return@runOnUiThread
                    if (newer) showMandatoryUpdate(tag, releaseUrl, apkUrl, notes)
                    else openWebsite()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isChecking = false
                    if (!isFinishing) showCheckFailed(e.message ?: "Network or GitHub error")
                }
            }
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrElse(i) { 0 }
            val y = right.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun showMandatoryUpdate(tag: String, releaseUrl: String, apkUrl: String, notes: String) {
        val message = buildString {
            append("A newer version ($tag) is required before you can use MALTWEB.")
            if (notes.isNotBlank()) append("\n\nWhat's new:\n").append(notes.take(1800))
            append("\n\nChoose Update Now to open the official GitHub Release and download the APK.")
        }
        updateDialog = AlertDialog.Builder(this)
            .setTitle("MALTWEB update required")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Update Now") { _, _ ->
                // Open the release page; users can inspect and download the APK themselves.
                openExternal(releaseUrl.ifBlank { apkUrl })
            }
            .setNegativeButton("I've updated — Check again") { _, _ ->
                updateDialog = null
                checkForRequiredUpdate()
            }
            .create()
        updateDialog?.setCanceledOnTouchOutside(false)
        updateDialog?.show()
        updateDialog?.setCanceledOnTouchOutside(false)
    }

    private fun showCheckFailed(reason: String) {
        updateDialog = AlertDialog.Builder(this)
            .setTitle("Can't verify MALTWEB version")
            .setMessage(
                "MALTWEB must check GitHub before opening. Check your internet connection and try again.\n\nDetails: $reason"
            )
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ ->
                updateDialog = null
                checkForRequiredUpdate()
            }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .create()
        updateDialog?.show()
        updateDialog?.setCanceledOnTouchOutside(false)
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't open the GitHub Release link.", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openWebsite() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = TextView(this).apply {
            text = "MALTWEB  •  Your community, connected."
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(18, 107, 80))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        val browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    return uri.scheme != "https" && uri.scheme != "http"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }
        }
        webView = browser
        layout.addView(header)
        layout.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
        ))
        layout.addView(browser, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(layout)
        if (savedInstanceState != null) browser.restoreState(savedInstanceState)
        else browser.loadUrl(websiteUrl)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        webView?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        updateDialog?.dismiss()
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        io.shutdownNow()
        super.onDestroy()
    }
}
