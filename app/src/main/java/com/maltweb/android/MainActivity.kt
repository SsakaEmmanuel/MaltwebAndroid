package com.maltweb.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
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
 * MALTWEB Android app with a friendly, non-blocking update indicator.
 * A download-arrow button appears only when a newer APK is available.
 * A failed update check never blocks access to the website.
 */
class MainActivity : ComponentActivity() {
    private val websiteUrl = "https://ssakaemmanuel.github.io/maltweb/"
    private val latestReleaseApi =
        "https://api.github.com/repos/SsakaEmmanuel/MaltwebAndroid/releases/latest"
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var updateButton: Button
    private lateinit var statusText: TextView
    private lateinit var browser: WebView
    private lateinit var progress: ProgressBar
    private var latestReleaseUrl: String? = null
    private var latestTag: String? = null
    private var latestNotes: String? = null
    private var updateCheckStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildAppScreen()
        checkForAvailableUpdate()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (browser.canGoBack()) browser.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        if (savedInstanceState != null) browser.restoreState(savedInstanceState)
        else browser.loadUrl(websiteUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildAppScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(18, 107, 80))
        }

        val brandColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandColumn.addView(TextView(this).apply {
            text = "MALTWEB"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        brandColumn.addView(TextView(this).apply {
            text = "Your community, connected."
            textSize = 11f
            setTextColor(Color.rgb(222, 242, 234))
        })
        header.addView(brandColumn, LinearLayout.LayoutParams(0, dp(48), 1f))

        updateButton = Button(this).apply {
            text = "↓"
            textSize = 27f
            isAllCaps = false
            contentDescription = "Download MALTWEB update"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(5, 82, 61))
            visibility = View.GONE
            setOnClickListener { showUpdateDetails() }
        }
        header.addView(updateButton, LinearLayout.LayoutParams(dp(54), dp(48)))

        val refresh = Button(this).apply {
            text = "↻"
            textSize = 23f
            isAllCaps = false
            contentDescription = "Refresh MALTWEB"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(18, 107, 80))
            setOnClickListener { browser.reload() }
        }
        header.addView(refresh, LinearLayout.LayoutParams(dp(48), dp(48)))

        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(18, 107, 80))
            setPadding(dp(14), dp(4), dp(14), dp(4))
            visibility = View.GONE
        }

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }

        browser = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    return uri.scheme != "https" && uri.scheme != "http"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@MainActivity.progress.progress = newProgress
this@MainActivity.progress.visibility =
    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        root.addView(header)
        root.addView(statusText)
        root.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
        ))
        root.addView(browser, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun checkForAvailableUpdate() {
        if (updateCheckStarted) return
        updateCheckStarted = true
        io.execute {
            var errorCode: Int? = null
            try {
                val connection = URL(latestReleaseApi).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", "MALTWEB-Android")

                val response = try {
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        errorCode = code
                        throw IllegalStateException("Update check failed")
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
                var apkFound = false
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        if (asset.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                            apkFound = true
                            break
                        }
                    }
                }

                if (tag.isBlank() || releaseUrl.isBlank() || !apkFound) {
                    throw IllegalStateException("No published APK is available yet")
                }

                val current = BuildConfig.VERSION_NAME
                val isNewer = compareVersions(tag.removePrefix("v"), current) > 0
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    if (isNewer) {
                        latestTag = tag
                        latestReleaseUrl = releaseUrl
                        latestNotes = notes
                        updateButton.visibility = View.VISIBLE
                        statusText.text = "A new version is available. Tap ↓ to update."
                        statusText.visibility = View.VISIBLE
                        Toast.makeText(this, "MALTWEB update available", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                // Friendly, non-technical message. Do not block the website.
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    val msg = if (errorCode != null) {
                        "Update check failed (${errorCode})"
                    } else {
                        "Update check failed"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDetails() {
        val tag = latestTag ?: return
        val url = latestReleaseUrl ?: return
        val notes = latestNotes.orEmpty()
        val message = buildString {
            append("MALTWEB $tag is ready to install.")
            if (notes.isNotBlank()) append("\n\nWhat's new:\n").append(notes.take(1500))
            append("\n\nDownload the update from the official MALTWEB release page?")
        }
        AlertDialog.Builder(this)
            .setTitle("✨ MALTWEB update available")
            .setMessage(message)
            .setPositiveButton("Download update") { _, _ -> openRelease(url) }
            .setNegativeButton("Maybe later", null)
            .show()
    }

    private fun openRelease(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Couldn't open the download page.", Toast.LENGTH_SHORT).show()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        if (::browser.isInitialized) browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::browser.isInitialized) {
            browser.stopLoading()
            browser.loadUrl("about:blank")
            browser.removeAllViews()
            browser.destroy()
        }
        io.shutdownNow()
        super.onDestroy()
    }
}
