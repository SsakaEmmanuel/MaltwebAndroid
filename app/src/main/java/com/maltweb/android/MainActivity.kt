package com.maltweb.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * MALTWEB user APK.
 * Loads the existing website inside the app, adds native branded navigation,
 * and shows an animated MALTWEB loading overlay while pages load.
 *
 * Authentication must be enforced by the website and Supabase RLS policies.
 * Do not rely on hiding navigation links in this APK as an access-control system.
 */
class MainActivity : ComponentActivity() {

    private val websiteUrl = "https://ssakaemmanuel.github.io/maltweb/"
    private val maltGreen = Color.rgb(18, 107, 80)
    private val deepGreen = Color.rgb(5, 67, 53)
    private val white = Color.WHITE
    private val darkText = Color.rgb(23, 41, 35)
    private val muted = Color.rgb(102, 119, 111)

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var loadingLogo: ImageView
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button

    private var pageLoadCount = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 249, 247))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Native branded header with the actual MALTWEB image asset.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(10), dp(7))
            setBackgroundColor(maltGreen)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.maltweb_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "MALTWEB logo"
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }
        titleView = TextView(this).apply {
            text = "MALTWEB"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        }
        val subtitle = TextView(this).apply {
            text = "Your community, connected."
            textSize = 12f
            setTextColor(Color.rgb(222, 242, 234))
        }
        titleColumn.addView(titleView)
        titleColumn.addView(subtitle)
        header.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val refreshTop = makeHeaderButton("↻") {
            if (::webView.isInitialized) webView.reload()
        }
        header.addView(refreshTop, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(header)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(80, 210, 151))
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        ))

        val pageFrame = FrameLayout(this)
        root.addView(pageFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        webView = WebView(this).apply {
            setBackgroundColor(white)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                builtInZoomControls = false
                displayZoomControls = false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                // Keep web links in the app; don't allow unknown non-web schemes.
                return scheme != "https" && scheme != "http"
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageLoadCount++
                showLoadingAnimation()
                titleView.text = "MALTWEB"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.progress = 100
                updateNavigationButtons()
                hideLoadingAnimation()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    hideLoadingAnimation()
                    Toast.makeText(
                        this@MainActivity,
                        "MALTWEB couldn't load this page. Check your internet and tap refresh.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        pageFrame.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Animated branded overlay shown while a new website page is loading.
        loadingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(white)
            setPadding(dp(28), dp(24), dp(28), dp(24))
            visibility = View.GONE
            alpha = 1f
        }
        loadingLogo = ImageView(this).apply {
            setImageResource(R.drawable.maltweb_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "MALTWEB loading animation"
        }
        loadingOverlay.addView(loadingLogo, LinearLayout.LayoutParams(dp(112), dp(112)))
        val loadingTitle = TextView(this).apply {
            text = "MALTWEB"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(deepGreen)
            gravity = Gravity.CENTER
        }
        loadingOverlay.addView(loadingTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val loadingMessage = TextView(this).apply {
            text = "Connecting your community…"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        loadingOverlay.addView(loadingMessage, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(5) })

        val spinner = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(maltGreen)
        }
        loadingOverlay.addView(spinner, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            topMargin = dp(18)
        })

        pageFrame.addView(loadingOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(white)
            setPadding(dp(5), dp(4), dp(5), dp(4))
        }
        val homeButton = makeNavButton("⌂\nHome") {
            if (webView.url != websiteUrl) webView.loadUrl(websiteUrl)
            else webView.scrollTo(0, 0)
        }
        backButton = makeNavButton("‹\nBack") { if (webView.canGoBack()) webView.goBack() }
        forwardButton = makeNavButton("›\nForward") { if (webView.canGoForward()) webView.goForward() }
        val refreshButton = makeNavButton("↻\nRefresh") { webView.reload() }
        listOf(homeButton, backButton, forwardButton, refreshButton).forEach { button ->
            nav.addView(button, LinearLayout.LayoutParams(0, dp(55), 1f))
        }
        root.addView(nav)

        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else {
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
    }

    private fun showLoadingAnimation() {
        loadingOverlay.clearAnimation()
        loadingLogo.clearAnimation()
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.alpha = 0f
        loadingOverlay.startAnimation(AlphaAnimation(0f, 1f).apply {
            duration = 180
            fillAfter = true
        })
        loadingLogo.startAnimation(ScaleAnimation(
            0.88f, 1.06f, 0.88f, 1.06f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 650
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        })
    }

    private fun hideLoadingAnimation() {
        if (!::loadingOverlay.isInitialized || loadingOverlay.visibility != View.VISIBLE) return
        loadingLogo.clearAnimation()
        val fade = AlphaAnimation(1f, 0f).apply {
            duration = 220
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) = Unit
                override fun onAnimationRepeat(animation: Animation?) = Unit
                override fun onAnimationEnd(animation: Animation?) {
                    loadingOverlay.visibility = View.GONE
                    loadingOverlay.alpha = 1f
                }
            })
        }
        loadingOverlay.startAnimation(fade)
        progressBar.visibility = View.GONE
    }

    private fun makeHeaderButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 22f
            setTextColor(white)
            setBackgroundColor(maltGreen)
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun makeNavButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(darkText)
            setBackgroundColor(white)
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
