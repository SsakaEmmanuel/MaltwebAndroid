package com.maltweb.android

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
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
 * MALTWEB Android WebView app.
 * Restores the native branded header and bottom navigation, with a friendly
 * optional update arrow that appears when a newer APK release is available.
 */
class MainActivity : ComponentActivity() {
    private val websiteUrl = "https://ssakaemmanuel.github.io/maltweb/"
    private val latestReleaseApi =
        "https://api.github.com/repos/SsakaEmmanuel/MaltwebAndroid/releases/latest"
    private val green = Color.rgb(18, 107, 80)
    private val deepGreen = Color.rgb(5, 67, 53)
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var browser: WebView
    private lateinit var progress: ProgressBar
    private lateinit var updateButton: Button
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button
    private lateinit var loadingOverlay: LinearLayout
    private var latestReleaseUrl: String? = null
    private var latestTag: String? = null
    private var latestNotes: String? = null
    private var updateCheckStarted = false

    // Holds the website's pending <input type="file"> callback.
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 7421

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildAppScreen()
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
        checkForAvailableUpdate()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildAppScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Native wordmark: does not depend on a missing drawable resource.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(6), dp(6))
            setBackgroundColor(green)
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
        }
        brand.addView(TextView(this).apply {
            text = "MALTWEB"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            contentDescription = "MALTWEB logo"
        })
        brand.addView(TextView(this).apply {
            text = "Your community, connected."
            textSize = 11f
            setTextColor(Color.rgb(222, 242, 234))
        })
        header.addView(brand, LinearLayout.LayoutParams(0, dp(52), 1f))

        updateButton = makeHeaderButton("↓", "Download update") { showUpdateDetails() }
        updateButton.visibility = View.GONE
        header.addView(updateButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        val refreshTop = makeHeaderButton("↻", "Refresh page") { browser.reload() }
        header.addView(refreshTop, LinearLayout.LayoutParams(dp(44), dp(48)))
        root.addView(header)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(80, 210, 151))
        }
        root.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
        ))

        val frame = android.widget.FrameLayout(this)
        root.addView(frame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

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
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    return uri.scheme != "https" && uri.scheme != "http"
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    showLoading()
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    updateNavigationButtons()
                    hideLoading()
                }
                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        hideLoading()
                        Toast.makeText(
                            this@MainActivity,
                            "MALTWEB couldn't load. Check your connection and tap refresh.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    // Cancel any previous request before replacing its callback.
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    val chooserIntent = try {
                        fileChooserParams?.createIntent()
                    } catch (_: Exception) {
                        null
                    }

                    if (chooserIntent == null) {
                        this@MainActivity.filePathCallback?.onReceiveValue(null)
                        this@MainActivity.filePathCallback = null
                        Toast.makeText(
                            this@MainActivity,
                            "Unable to open the image picker.",
                            Toast.LENGTH_LONG
                        ).show()
                        return false
                    }

                    return try {
                        startActivityForResult(chooserIntent, fileChooserRequestCode)
                        true
                    } catch (_: Exception) {
                        this@MainActivity.filePathCallback?.onReceiveValue(null)
                        this@MainActivity.filePathCallback = null
                        Toast.makeText(
                            this@MainActivity,
                            "No file picker is available on this phone.",
                            Toast.LENGTH_LONG
                        ).show()
                        false
                    }
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@MainActivity.progress.progress = newProgress
                    this@MainActivity.progress.visibility =
                        if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }
        }
        frame.addView(browser, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
        }
        loadingOverlay.addView(TextView(this).apply {
            text = "MALTWEB"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(deepGreen)
        })
        loadingOverlay.addView(TextView(this).apply {
            text = "Connecting your community…"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(8), 0, dp(12))
        })
        loadingOverlay.addView(ProgressBar(this))
        frame.addView(loadingOverlay, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Restore the native navigation row: Home / Back / Forward / Refresh.
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(4), dp(3), dp(4), dp(3))
            elevation = dp(5).toFloat()
        }
        val home = makeNavButton("⌂\nHome") {
            if (browser.url != websiteUrl) browser.loadUrl(websiteUrl)
            else browser.scrollTo(0, 0)
        }
        backButton = makeNavButton("‹\nBack") { if (browser.canGoBack()) browser.goBack() }
        forwardButton = makeNavButton("›\nForward") { if (browser.canGoForward()) browser.goForward() }
        val refresh = makeNavButton("↻\nRefresh") { browser.reload() }
        listOf(home, backButton, forwardButton, refresh).forEach { button ->
            nav.addView(button, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        root.addView(nav)
        setContentView(root)
        updateNavigationButtons()
    }

    @Deprecated("Used to receive the Android WebView file picker result.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileChooserRequestCode) return

        val callback = filePathCallback ?: return
        val selectedUris = if (resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                ?: data?.data?.let { arrayOf(it) }
        } else {
            null
        }
        callback.onReceiveValue(selectedUris)
        filePathCallback = null
    }

    private fun showLoading() {
        if (!::loadingOverlay.isInitialized) return
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        if (!::loadingOverlay.isInitialized) return
        loadingOverlay.visibility = View.GONE
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
                } finally { connection.disconnect() }

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
                    throw IllegalStateException("No published APK is available")
                }
                val newer = compareVersions(tag.removePrefix("v"), BuildConfig.VERSION_NAME) > 0
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    if (newer) {
                        latestTag = tag
                        latestReleaseUrl = releaseUrl
                        latestNotes = notes
                        updateButton.visibility = View.VISIBLE
                        Toast.makeText(this, "MALTWEB update available — tap ↓", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing) {
                        val message = if (errorCode != null) "Update check failed ($errorCode)" else "Update check failed"
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
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
            append("\n\nOpen the release page to download the update?")
        }
        AlertDialog.Builder(this)
            .setTitle("✨ MALTWEB update available")
            .setMessage(message)
            .setPositiveButton("Download update") { _, _ -> openRelease(url) }
            .setNegativeButton("Maybe later", null)
            .show()
    }

    private fun openRelease(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) {
            Toast.makeText(this, "Couldn't open the download page.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }
        val right = b.substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrElse(i) { 0 }
            val y = right.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun makeHeaderButton(label: String, description: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 22f
            contentDescription = description
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
            minWidth = 0
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action() }
        }

    private fun makeNavButton(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            minWidth = 0
            setPadding(dp(2), 0, dp(2), 0)
            setTextColor(Color.rgb(23, 41, 35))
            setBackgroundColor(Color.WHITE)
            setOnClickListener { action() }
        }

    private fun updateNavigationButtons() {
        if (!::browser.isInitialized) return
        if (::backButton.isInitialized) backButton.isEnabled = browser.canGoBack()
        if (::forwardButton.isInitialized) forwardButton.isEnabled = browser.canGoForward()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        if (::browser.isInitialized) browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
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
