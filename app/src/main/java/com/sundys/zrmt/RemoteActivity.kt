package com.sundys.zrmt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class RemoteActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private lateinit var flContainer: ViewGroup
    private lateinit var tvTitle: TextView
    private lateinit var link: Link

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var pendingWebPermission: PermissionRequest? = null
    private var desktopUa = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val l = readLink(intent)
        if (l == null) {
            Toast.makeText(this, R.string.link_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        link = l

        tvTitle = findViewById(R.id.tvTitle)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)
        flContainer = findViewById(R.id.flContainer)
        findViewById<TextView>(R.id.tvSubtitle).text = link.host

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        flContainer.addView(webView, 0)

        setupWeb()

        findViewById<View>(R.id.btnBack).setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
        findViewById<View>(R.id.btnReload).setOnClickListener { reload() }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMenu(it) }
        errorView.findViewById<View>(R.id.btnRetry).setOnClickListener { reload() }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(link.url)
        }
        LinkStore(this).markOpened(link.id)
    }

    private fun readLink(intent: Intent): Link? {
        val url = intent.getStringExtra(EXTRA_URL) ?: return null
        return Link(
            id = intent.getStringExtra(EXTRA_ID).orEmpty(),
            url = url,
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            version = intent.getStringExtra(EXTRA_VERSION).orEmpty(),
            host = intent.getStringExtra(EXTRA_HOST).orEmpty(),
            createdAt = 0L,
            lastOpenedAt = 0L
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setBackgroundColor(0xFF17191E.toInt())

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                errorView.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                ensureViewport(view)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val u = request.url
                return when (u.scheme?.lowercase()) {
                    "http", "https" -> false
                    else -> {
                        openExternal(u)
                        true
                    }
                }
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showError(error.description?.toString() ?: getString(R.string.load_failed))
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    showError("服务器返回 HTTP ${errorResponse.statusCode}")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                progressBar.progress = newProgress
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { handleWebPermission(request) }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                flContainer.addView(
                    view, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                customView = view
                customViewCallback = callback
            }

            override fun onHideCustomView() {
                customView?.let { flContainer.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                super.onHideCustomView()
            }
        }
    }

    private fun handleWebPermission(request: PermissionRequest) {
        val grants = ArrayList<String>()
        for (res in request.resources) {
            val appPerm = when (res) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
                else -> null
            }
            if (appPerm == null) {
                grants.add(res)
                continue
            }
            if (checkSelfPermission(appPerm) == PackageManager.PERMISSION_GRANTED) {
                grants.add(res)
            } else {
                pendingWebPermission = request
                requestPermissions(arrayOf(appPerm), REQ_PERM)
                return
            }
        }
        request.grant(grants.toTypedArray())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERM) return
        val pending = pendingWebPermission
        pendingWebPermission = null
        if (pending == null) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pending.grant(pending.resources)
        } else {
            pending.deny()
            Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_LONG).show()
        }
    }

    /** 页面缺少 viewport meta 时补一个，保证按手机屏宽自适应缩放 */
    private fun ensureViewport(view: WebView) {
        val js = "(function(){try{if(document.querySelector('meta[name=viewport]'))return;" +
            "var m=document.createElement('meta');m.setAttribute('name','viewport');" +
            "m.setAttribute('content','width=device-width, initial-scale=1');" +
            "(document.head||document.documentElement).appendChild(m);}catch(e){}})();"
        view.evaluateJavascript(js, null)
    }

    private fun showError(desc: String) {
        progressBar.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorView.findViewById<TextView>(R.id.tvError).text = desc
    }

    private fun reload() {
        errorView.visibility = View.GONE
        if (webView.url.isNullOrEmpty()) webView.loadUrl(link.url) else webView.reload()
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.menu_copy)
        popup.menu.add(0, 2, 0, if (desktopUa) R.string.menu_mobile_ua else R.string.menu_desktop_ua)
        popup.menu.add(0, 3, 0, R.string.menu_open_browser)
        popup.menu.add(0, 4, 0, R.string.menu_close)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("zcode", webView.url ?: link.url))
                    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    desktopUa = !desktopUa
                    webView.settings.userAgentString = if (desktopUa)
                        DESKTOP_UA
                    else
                        WebSettings.getDefaultUserAgent(this)
                    reload()
                }
                3 -> openExternal(Uri.parse(webView.url ?: link.url))
                4 -> finish()
            }
            true
        }
        popup.show()
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        when {
            customView != null -> (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            flContainer.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_VERSION = "version"
        const val EXTRA_HOST = "host"
        private const val REQ_PERM = 4001

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"

        fun intent(context: Context, link: Link): Intent =
            Intent(context, RemoteActivity::class.java)
                .putExtra(EXTRA_ID, link.id)
                .putExtra(EXTRA_URL, link.url)
                .putExtra(EXTRA_NAME, link.name)
                .putExtra(EXTRA_VERSION, link.version)
                .putExtra(EXTRA_HOST, link.host)
    }
}
