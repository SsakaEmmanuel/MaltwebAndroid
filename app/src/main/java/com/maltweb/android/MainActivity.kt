package com.maltweb.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.setPadding

/**
 * MALTWEB user app: a native Android shell around the existing MALTWEB website.
 *
 * The website remains the source of truth for listings, categories, registration,
 * login, and user-facing pages. Administrator access must be restricted by the
 * website/backend permissions; hiding an admin button in an APK is not security.
 */
class MainActivity : ComponentActivity() {

    private val websiteUrl = "https://ssakaemmanuel.github.io/maltweb/"

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button
    private lateinit var homeButton: Button
    private lateinit var refreshButton: Button
    private lateinit var pageContainer: FrameLayout

    private val maltGreen = Color.rgb(18, 107, 80)
    private val pageWhite = Color.WHITE
    private val softBackground = Color.rgb(247, 249, 247)
    private val darkText = Color.rgb(23, 41, 35)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(softBackground)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14))
            setBackgroundColor(maltGreen)
        }

        val logo = TextView(this).apply {
            text = "M"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pageWhite)
            gravity = Gravity.CENTER
            setPadding(dp(5))
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(44)))

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        titleView = TextView(this).apply {
            text = "MALTWEB"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pageWhite)
        }
        val subtitle = TextView(this).apply {
            text = "Your community, connected."
            textSize = 11f
            setTextColor(Color.rgb(225, 243, 235))
        }
        titleColumn.addView(titleView)
        titleColumn.addView(subtitle)
        header.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        refreshButton = makeHeaderButton("↻") { if (::webView.isInitialized) webView.reload() }
        header.addView(refreshButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(54, 190, 128))
            indeterminateTintList = android.content.res.ColorStateList.valueOf(maltGreen)
        }
        root.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        ))

        pageContainer = FrameLayout(this)
        root.addView(pageContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        webView = WebView(this)
        webView.setBackgroundColor(pageWhite)
        webView.settings.apply {
            javaScriptEnabled = true // Required by the existing website's interactive UI.
            domStorageEnabled = true // Allows normal website storage/session behavior.
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                // Keep normal website pages inside the app. Block non-web schemes rather
                // than launching unknown apps or exposing them to the WebView.
                return scheme != "https" && scheme != "http"
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 8
                titleView.text = "MALTWEB"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.progress = 100
                progressBar.visibility = View.GONE
                titleView.text = "MALTWEB"
                updateNavigationButtons()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to load MALTWEB. Check your internet and tap refresh.",
                        Toast.LENGTH_LONG
                    ).show()
                    progressBar.visibility = View.GONE
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        pageContainer.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(pageWhite)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        homeButton = makeNavButton("⌂\nHome") {
            if (webView.url != websiteUrl) webView.loadUrl(websiteUrl)
            else webView.scrollTo(0, 0)
        }
        backButton = makeNavButton("‹\nBack") {
            if (webView.canGoBack()) webView.goBack()
        }
        forwardButton = makeNavButton("›\nForward") {
            if (webView.canGoForward()) webView.goForward()
        }
        val reloadNav = makeNavButton("↻\nRefresh") { webView.reload() }

        listOf(homeButton, backButton, forwardButton, reloadNav).forEach { button ->
            nav.addView(button, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        root.addView(nav)

        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(websiteUrl)
        }
        updateNavigationButtons()
    }

    private fun makeHeaderButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 21f
            setTextColor(pageWhite)
            setBackgroundColor(maltGreen)
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun makeNavButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(darkText)
            setBackgroundColor(pageWhite)
            isAllCaps = false
            minWidth = 0
            setPadding(dp(2))
            setOnClickListener { action() }
        }

    private fun updateNavigationButtons() {
        if (!::webView.isInitialized) return
        if (::backButton.isInitialized) backButton.isEnabled = webView.canGoBack()
        if (::forwardButton.isInitialized) forwardButton.isEnabled = webView.canGoForward()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }
}
