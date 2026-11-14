package com.morvehtv.envoltorio

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Envoltorio de morvehtv.com para televisores (Android TV / Google TV).
 * Carga el sitio en un WebView a pantalla completa. El mando se maneja con
 * el foco que ya proporciona el WebView; el botón Atrás vuelve en el historial
 * del sitio y solo sale de la app cuando no queda nada atrás.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var errorView: LinearLayout
    private lateinit var retryButton: Button

    // Vídeo a pantalla completa: se muestra dentro de la propia ventana,
    // no con requestFullscreen (eso abre una ventana aparte que come el Atrás).
    private var videoContainer: FrameLayout? = null
    private var videoCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = false
        }
        root.addView(webView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        errorView = buildErrorView()
        root.addView(errorView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        errorView.visibility = View.GONE

        configureWebView()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(BuildConfig.SITE_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return false
                // Se queda dentro de la app todo lo del sitio; lo demás se abre fuera.
                return if (host == "morvehtv.com" || host.endsWith(".morvehtv.com")) {
                    false
                } else {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {
                    }
                    true
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError()
            }

            // Un 429 o 5xx del sitio no es un error de red para el WebView:
            // hay que detectarlo aquí para no enseñar la página de error del servidor.
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) showError()
            }

        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (videoContainer != null) {
                    callback.onCustomViewHidden()
                    return
                }
                videoContainer = FrameLayout(this@MainActivity).apply {
                    setBackgroundColor(Color.BLACK)
                    addView(view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                videoCallback = callback
                root.addView(videoContainer, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                hideVideo()
            }
        }
    }

    private fun hideVideo() {
        val container = videoContainer ?: return
        root.removeView(container)
        videoContainer = null
        videoCallback?.onCustomViewHidden()
        videoCallback = null
        webView.visibility = View.VISIBLE
        webView.requestFocus()
    }

    private fun buildErrorView(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(64), dp(48), dp(64), dp(48))
        }
        val title = TextView(this).apply {
            text = getString(R.string.error_titulo)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
        }
        val retry = Button(this).apply {
            text = getString(R.string.error_reintentar)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                errorView.visibility = View.GONE
                webView.isFocusable = true
                webView.loadUrl(BuildConfig.SITE_URL)
            }
        }
        retryButton = retry
        layout.addView(title)
        layout.addView(retry, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(32)
        })
        return layout
    }

    private fun showError() {
        errorView.visibility = View.VISIBLE
        // El WebView oculto no debe conservar el foco: si no, el mando no llega al botón.
        webView.isFocusable = false
        retryButton.requestFocus()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (videoContainer != null) {
                hideVideo()
                return true
            }
            cerrarSuperposicionOAtras()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Atrás primero cierra lo que el sitio tiene abierto (menú lateral, buscador,
    // ventana modal). Solo si no hay nada abierto vuelve en el historial o sale.
    private fun cerrarSuperposicionOAtras() {
        webView.evaluateJavascript(CERRAR_SUPERPOSICION_JS) { resultado ->
            if (resultado == "\"cerrado\"") return@evaluateJavascript
            if (webView.canGoBack()) webView.goBack() else finish()
        }
    }

    companion object {
        // Devuelve "cerrado" si ha cerrado algo, o "nada" si no había nada abierto.
        private const val CERRAR_SUPERPOSICION_JS = """
            (function () {
                var menu = document.querySelector('.hello-movieengine-header__mobile-toggle[aria-expanded="true"]');
                if (menu) { menu.click(); return 'cerrado'; }
                var buscador = document.querySelector('.hello-movieengine-header__search-close');
                if (buscador && buscador.offsetParent !== null) { buscador.click(); return 'cerrado'; }
                var modal = document.querySelector('.movie-engine-close-modal');
                if (modal && modal.offsetParent !== null) { modal.click(); return 'cerrado'; }
                return 'nada';
            })()
        """
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
