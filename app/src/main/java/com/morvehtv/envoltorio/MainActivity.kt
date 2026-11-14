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
        // Solo en compilaciones de depuración: permite inspeccionar la página con Chrome DevTools.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

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

            // Cada página completa recibe los estilos de TV. Las navegaciones
            // internas del sitio (AJAX) los conservan porque no recargan el documento.
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(INYECTAR_ESTILOS_TV_JS, null)
                view.evaluateJavascript(MENU_JS, null)
            }

        }

        webView.addJavascriptInterface(PuenteTv(this), "AndroidTV")

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
        // Ajustes visuales para televisor: márgenes seguros (los televisores recortan
        // los bordes), letra más grande para leer desde el sofá, más columnas en la
        // cuadrícula y un foco muy visible para el mando.
        private const val ESTILOS_TV = """
            .hello-movieengine-container, .movie-engine-container-fluid {
                padding-left: 7vw !important;
                padding-right: 7vw !important;
                box-sizing: border-box !important;
            }
            body { font-size: 20px !important; }
            .hello-movieengine-header__search-toggle,
            .hello-movieengine-header__search,
            .hello-movieengine-header__mobile-toggle { display: none !important; }
            .hello-movieengine-container, .movie-engine-library-container { max-width: none !important; width: 100% !important; }
            .movie-engine-card-title, .movie-engine-card-title * {
                font-size: 1.1rem !important;
                white-space: normal !important;
                overflow: visible !important;
                text-overflow: clip !important;
                -webkit-line-clamp: unset !important;
                -webkit-box-orient: horizontal !important;
                display: block !important;
            }
            .movie-engine-items-grid {
                grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)) !important;
                gap: 24px !important;
            }
            a:focus, button:focus, input:focus, [tabindex]:focus {
                outline: 4px solid #e50914 !important;
                outline-offset: 4px !important;
            }
            .movie-engine-movie-card:focus-within { transform: scale(1.04); transition: transform .15s; }
            #tv-menu-btn {
                margin-left: 28px; background: #e50914; color: #fff; border: 0; border-radius: 8px;
                padding: 10px 18px; font-size: 20px; font-weight: 700; cursor: pointer; align-self: center;
            }
            #tv-menu {
                position: fixed; top: 0; left: 0; bottom: 0; width: 360px; z-index: 9999;
                background: #0d0d0d; box-shadow: 4px 0 24px rgba(0,0,0,.6);
                overflow-y: auto; padding: 36px 24px; box-sizing: border-box;
                display: flex; flex-direction: column; gap: 6px;
            }
            #tv-menu[hidden] { display: none; }
            #tv-menu h2 { color: #fff; font-size: 22px; margin: 0 0 12px 0; letter-spacing: .08em; }
            #tv-menu a.tv-menu-link {
                display: block; color: #fff; text-decoration: none; font-size: 20px;
                padding: 12px 14px; border-radius: 6px;
            }
            #tv-menu a.tv-menu-link:focus, #tv-menu button:focus, #tv-menu-btn:focus {
                background: #222; outline: 4px solid #e50914 !important; outline-offset: 2px !important;
            }
            #masthead.tv-cabecera-fija, .tv-cabecera-fija {
                position: fixed !important; top: 0 !important; left: 0 !important; right: 0 !important;
                z-index: 1000 !important; width: 100% !important;
            }
            #tv-categorias {
                display: flex; flex-wrap: wrap; gap: 6px 22px;
                padding: 6px 7vw 12px; box-sizing: border-box;
            }
            #tv-categorias a {
                color: #d6d6d6; text-decoration: none; font-size: 18px; font-weight: 600;
                padding: 4px 8px; border-radius: 6px;
            }
            #tv-categorias a:focus { color: #fff; background: #222; outline: 4px solid #e50914 !important; outline-offset: 2px !important; }
            #tv-salir-btn {
                margin-left: 12px; background: #333; color: #fff; border: 0; border-radius: 8px;
                padding: 10px 18px; font-size: 20px; font-weight: 700; align-self: center;
            }
            #tv-salir-btn:focus { outline: 4px solid #e50914 !important; outline-offset: 4px !important; }
        """

        private val INYECTAR_ESTILOS_TV_JS = """
            (function () {
                if (document.getElementById('tv-estilos')) return;
                var s = document.createElement('style');
                s.id = 'tv-estilos';
                s.textContent = ${org.json.JSONObject.quote(ESTILOS_TV)};
                document.head.appendChild(s);
            })();
        """

        // Crea los botones Menú y Salir junto al logotipo, y el panel de categorías
        // (secciones de la biblioteca en tv.morvehtv.com). Salir llama al puente nativo
        // AndroidTV.salir() y queda fuera del panel.
        private val MENU_JS = """
            (function () {
                if (document.getElementById('tv-menu-btn')) return;
                var logo = document.querySelector('.hello-movieengine-header__logo');
                if (!logo) return;

                var categorias = [
                    ['Películas', 'https://tv.morvehtv.com/#/categoria/pelicula'],
                    ['Series', 'https://tv.morvehtv.com/#/categoria/serie'],
                    ['Películas Anime', 'https://tv.morvehtv.com/#/categoria/anime?sub=pelicula'],
                    ['Documentales', 'https://tv.morvehtv.com/#/categoria/documental'],
                    ['Series Anime', 'https://tv.morvehtv.com/#/categoria/anime?sub=serie'],
                    ['Docuseries', 'https://tv.morvehtv.com/#/categoria/documental?sub=serie'],
                    ['Programas de TV', 'https://tv.morvehtv.com/#/categoria/programa']
                ];

                var btn = document.createElement('button');
                btn.id = 'tv-menu-btn';
                btn.type = 'button';
                btn.textContent = 'Menú';
                logo.insertAdjacentElement('afterend', btn);

                var salir = document.createElement('button');
                salir.id = 'tv-salir-btn';
                salir.type = 'button';
                salir.textContent = 'Salir';
                salir.onclick = function () {
                    if (window.AndroidTV) window.AndroidTV.salir();
                };
                btn.insertAdjacentElement('afterend', salir);

                // Fila de categorías dentro de la cabecera, visible sin abrir el menú.
                var cabecera = document.getElementById('masthead') || logo.closest('header');
                var fila = document.createElement('nav');
                fila.id = 'tv-categorias';
                fila.setAttribute('aria-label', 'Categorías');
                categorias.forEach(function (c) { fila.appendChild(enlace(c[0], c[1])); });
                cabecera.appendChild(fila);

                // La cabecera se queda fija arriba; el contenido empieza justo debajo.
                cabecera.classList.add('tv-cabecera-fija');
                function ajustarAltura() {
                    document.body.style.paddingTop = cabecera.offsetHeight + 'px';
                }
                ajustarAltura();
                window.addEventListener('resize', ajustarAltura);

                var panel = document.createElement('div');
                panel.id = 'tv-menu';
                panel.hidden = true;
                document.body.appendChild(panel);

                var cargado = false;
                function enlace(texto, href) {
                    var a = document.createElement('a');
                    a.className = 'tv-menu-link';
                    a.href = href;
                    a.textContent = texto;
                    return a;
                }
                function construir() {
                    if (cargado) return;
                    cargado = true;
                    var titulo = document.createElement('h2');
                    titulo.textContent = 'CATEGORÍAS';
                    panel.appendChild(titulo);
                    categorias.forEach(function (c) { panel.appendChild(enlace(c[0], c[1])); });
                }
                function abrir() {
                    construir();
                    panel.hidden = false;
                    var primero = panel.querySelector('a, button');
                    if (primero) primero.focus();
                }
                function cerrar() {
                    panel.hidden = true;
                    btn.focus();
                }
                btn.onclick = function () { panel.hidden ? abrir() : cerrar(); };
                document.addEventListener('keydown', function (e) {
                    if (e.key === 'Escape' && !panel.hidden) cerrar();
                });
                window.tvMenuCerrar = function () {
                    if (!panel.hidden) { cerrar(); return true; }
                    return false;
                };
            })();
        """

        // Devuelve "cerrado" si ha cerrado algo, o "nada" si no había nada abierto.
        private const val CERRAR_SUPERPOSICION_JS = """
            (function () {
                if (window.tvMenuCerrar && window.tvMenuCerrar()) return 'cerrado';
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

// Puente que la página usa para pedir a la app que se cierre (botón «Salir de la app»).
class PuenteTv(private val actividad: Activity) {
    @android.webkit.JavascriptInterface
    fun salir() {
        actividad.runOnUiThread { actividad.finish() }
    }
}
