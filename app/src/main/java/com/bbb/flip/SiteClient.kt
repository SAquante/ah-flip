package com.bbb.flip

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * BootyBayBroker sits behind a Cloudflare managed challenge and checks the TLS fingerprint, so a
 * plain HTTP client gets 403 even with the clearance cookie.
 *
 * Transport: the WebView is navigated straight to the API URL and the rendered JSON document is
 * read back. Navigation gives every request its own document, which sidesteps the two things that
 * made same-document `fetch()` unreliable here — a JS context that dies after one call, and the
 * site's own SPA scripts reloading the page mid-request.
 */
class SiteClient {

    private val gate = Mutex()
    private var webView: WebView? = null
    private var ready = false
    private var bootstrapped = false
    private var loadSeq = 0

    /** Bumped by every completed page load, so navigation can be awaited reliably. */
    @Volatile
    private var loads = 0

    fun bind(view: WebView) {
        if (webView === view) return
        webView = view
        ready = false
        bootstrapped = false

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // Prices must be live; a cached /api/ response showed an hour-old scan.
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        // The SPA registers a service worker that would answer /api/ with its HTML shell.
        WebStorage.getInstance().deleteAllData()
        view.clearCache(true)

        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "page finished: $url")
                loads++
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                val host = url.host ?: return null
                // Keep the document and the Cloudflare challenge; drop ads, trackers, fonts,
                // images and the SPA bundles (so no service worker, and a light challenge page).
                if (host == "challenges.cloudflare.com") return null
                if (host.endsWith("bootybaybroker.com")) {
                    val path = url.path ?: "/"
                    if (path.startsWith("/assets/") || path.startsWith("/img/") ||
                        path.startsWith("/icon-") || path.endsWith(".woff2")
                    ) {
                        return blocked()
                    }
                    return null
                }
                return blocked()
            }
        }
    }

    private fun blocked(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    suspend fun get(path: String): String = gate.withLock {
        if (!ready) ensureReady()
        val url = if (path.startsWith("http")) path else Api.BASE + path
        var lastError = "нет ответа"
        repeat(5) { attempt ->
            val body = runCatching { loadJsonDocument(url) }.getOrElse {
                lastError = it.message ?: "ошибка"
                null
            }
            if (body != null) {
                Log.d(TAG, "GET $path -> 200 ${body.length}b")
                // An error envelope is still JSON, so surface it instead of empty data.
                val err = runCatching { apiJson.decodeFromString<ErrorEnvelope>(body) }
                    .getOrNull()?.takeIf { it.status >= 400 }
                if (err != null) {
                    val message = "${err.status} ${err.detail ?: err.title ?: ""}".trim()
                    if (err.status == 429) throw IOException(rateLimitMessage(body))
                    throw IOException(message)
                }
                return body
            }
            Log.w(TAG, "GET $path attempt ${attempt + 1} failed: $lastError")
            delay(400)
        }
        throw IOException(lastError)
    }

    private fun rateLimitMessage(body: String): String {
        val minutes = Regex("\"retry_after_seconds\"\\s*:\\s*(\\d+)")
            .find(body)?.groupValues?.get(1)?.toIntOrNull()?.let { (it + 59) / 60 }
        return "Аукцион временно ограничил запросы с этого IP" +
            (minutes?.let { ". Повторите через ~$it мин" } ?: ". Повторите позже")
    }

    private suspend fun ensureReady() {
        val view = webView ?: throw IOException("Соединение не инициализировано")
        withContext(Dispatchers.Main.immediate) {
            if (!bootstrapped) {
                bootstrapped = true
                view.loadUrl(PAGE_URL)
            }
        }
        // The real page runs the challenge and sets cf_clearance.
        waitForLoads(20_000)
        var status = 0
        repeat(15) { attempt ->
            val probe = runCatching { loadJsonDocument("${Api.BASE}/api/config") }.getOrNull()
            if (probe != null && probe.trimStart().startsWith("{")) {
                ready = true
                Log.d(TAG, "ready after ${attempt + 1} probe(s)")
                return
            }
            status = attempt + 1
            delay(700)
        }
        throw IOException("Cloudflare не пропустил запросы (попыток: $status). Проверьте интернет.")
    }

    /**
     * Navigates to [url] and returns the document text. WebView treats JSON -> JSON as a download
     * (the document never commits), so a blank document is loaded in between; the read is gated on
     * a completed load, the document reporting [url] as its own location, and the text parsing as
     * complete JSON.
     */
    private suspend fun loadJsonDocument(url: String): String {
        navigate("about:blank", 6_000)
        navigate(url, 20_000)
        // onPageFinished can arrive from the previous navigation, so poll the document itself:
        // it must be complete, report the requested URL, and parse as full JSON.
        repeat(30) {
            val doc = readDocument()
            if (doc != null && doc.url.startsWith(url) && doc.text.length > 1 && !doc.html) {
                if (runCatching { apiJson.parseToJsonElement(doc.text) }.isSuccess) return doc.text
            }
            delay(250)
        }
        throw IOException("Аукцион вернул не данные (возможно, проверка Cloudflare)")
    }

    private suspend fun navigate(target: String, timeoutMs: Long) {
        val view = webView ?: throw IOException("Соединение не инициализировано")
        withContext(Dispatchers.Main.immediate) { view.loadUrl(target) }
        waitForLoads(timeoutMs)
    }

    private suspend fun waitForLoads(timeoutMs: Long) {
        val before = loads
        withTimeoutOrNull(timeoutMs) {
            while (loads <= before) delay(80)
        }
    }

    private class Document(val url: String, val html: Boolean, val text: String)

    /** Reads the rendered document in slices: one big evaluateJavascript call truncates. */
    private suspend fun readDocument(): Document? = withContext(Dispatchers.Main.immediate) {
        val view = webView ?: return@withContext null
        val head = evalString(
            view,
            "(document.readyState==='complete'?'1':'0') + '|' + location.href + '|' + " +
                "(document.contentType.indexOf('json')<0 ? 'HTML' : 'JSON')",
        )
        val parts = head.split("|", limit = 3)
        if (parts.size < 3 || parts[0] != "1") return@withContext null
        val chunk = 40_000
        val sb = StringBuilder()
        var offset = 0
        while (true) {
            val part = evalString(
                view,
                "(function(){var t=document.documentElement?document.documentElement.textContent:'';" +
                    "return t.substring($offset, ${offset + chunk})})()",
            )
            sb.append(part)
            if (part.length < chunk) break
            offset += chunk
            if (offset > 4_000_000) break
        }
        Document(parts[1], parts[2] == "HTML", sb.toString())
    }

    private suspend fun evalString(view: WebView, js: String): String {
        val done = CompletableDeferred<String>()
        view.evaluateJavascript(js) { raw -> done.complete(decodeJsString(raw ?: "")) }
        return withTimeout(15_000) { done.await() }
    }

    /** evaluateJavascript returns a JS string literal; JSON-escaped. */
    private fun decodeJsString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return ""
        if (!trimmed.startsWith("\"")) return trimmed
        return runCatching {
            (apiJson.parseToJsonElement(trimmed) as kotlinx.serialization.json.JsonPrimitive).content
        }.getOrDefault(trimmed)
    }

    companion object {
        private const val TAG = "BBB"
        private const val PAGE_URL = "${Api.BASE}/ru/forever?region=eu"
    }
}
