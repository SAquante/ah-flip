package com.bbb.flip

import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BootyBayBroker is behind a Cloudflare managed challenge and checks the TLS fingerprint, so a
 * plain HTTP client gets 403 even with the clearance cookie. Requests are therefore made as
 * same-origin fetch() calls inside a real WebView that has solved the challenge.
 *
 * The WebView stays parked on one page — navigating it to a JSON endpoint makes WebView treat
 * the response as a download and drop the document.
 */
class SiteClient {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Pair<Int, String>>>()
    private val chunks = ConcurrentHashMap<String, StringBuilder>()
    private val gate = Mutex()
    private var webView: WebView? = null
    private var ready = false
    private var bootstrapped = false
    private val pageUrl = "${Api.BASE}/ru/forever?region=eu"
    private var pageDone = CompletableDeferred<Unit>()
    private var loaded = CompletableDeferred<Unit>()
    private var pendingUrl = ""
    /** Set after a fetch succeeds: the page's JS context must be rebuilt before the next one. */
    private var contextStale = false

    fun bind(view: WebView) {
        if (webView === view) return
        webView = view
        ready = false
        bootstrapped = false
        pageDone = CompletableDeferred()
        pending.clear()
        chunks.clear()

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        // The site's SPA registers a service worker; a stale one answers fetch() with its HTML
        // shell or hangs it. Wipe stored page data so none survives an app upgrade.
        WebStorage.getInstance().deleteAllData()
        view.clearCache(true)

        view.addJavascriptInterface(Bridge(), "BBB")
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "page finished: $url")
                if (url != null && url.startsWith(pageUrl)) pageDone.complete(Unit)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                val host = url.host ?: return null
                // Keep the document and the Cloudflare challenge; drop ads, trackers, fonts,
                // images and SPA bundles so the hidden page stays light and never registers
                // a service worker.
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
        if (contextStale) {
            contextStale = false
            reload()
        }
        var last = -1 to ""
        repeat(6) { attempt ->
            val result = runCatching { fetchJson(path, 12_000) }.getOrNull()
            if (result != null && result.first in 200..299) {
                Log.d(TAG, "GET $path -> ${result.first} ${result.second.length}b")
                // An error envelope still parses as JSON, so surface it rather than empty data.
                val err = runCatching { apiJson.decodeFromString<ErrorEnvelope>(result.second) }
                    .getOrNull()?.takeIf { it.status >= 400 }
                if (err != null) throw IOException("${err.status} ${err.detail ?: err.title ?: ""}".trim())
                // The page's JS context survives only one request before going stale, so the
                // next call starts from a fresh document (cookies keep the Cloudflare pass).
                contextStale = true
                return result.second
            }
            last = result ?: (-1 to "timeout")
            Log.w(TAG, "GET $path attempt ${attempt + 1} failed: ${last.first}")
            if (last.first == 429) {
                // The site rate-limits per IP; retrying only extends the block.
                val minutes = Regex("\"retry_after_seconds\"\\s*:\\s*(\\d+)")
                    .find(last.second)?.groupValues?.get(1)?.toIntOrNull()?.let { (it + 59) / 60 }
                throw IOException(
                    "Аукцион временно ограничил запросы с этого IP" +
                        (minutes?.let { ". Повторите через ~$it мин" } ?: ". Повторите позже"),
                )
            }
            reload()
        }
        throw IOException("Аукцион не ответил (HTTP ${last.first}). Проверьте интернет и повторите.")
    }

    private suspend fun ensureReady() {
        val view = webView ?: throw IOException("Соединение не инициализировано")
        withContext(Dispatchers.Main.immediate) {
            if (!bootstrapped) {
                bootstrapped = true
                pageDone = CompletableDeferred()
                view.loadUrl(pageUrl)
            }
        }
        // fetch() from a document that is still loading queues behind its resources, so wait.
        withTimeoutOrNull(20_000) { pageDone.await() }
        var code = 0
        repeat(15) { attempt ->
            delay(if (attempt == 0) 600 else 1_000)
            code = runCatching { fetchJson("/api/config", 8_000).first }.getOrDefault(0)
            if (code == 200) {
                ready = true
                Log.d(TAG, "ready after ${attempt + 1} probe(s)")
                return
            }
            if (attempt == 7) reload()
        }
        throw IOException("Cloudflare не пропустил запросы (HTTP $code). Проверьте интернет и повторите.")
    }

    /** Reload the parked page so a fresh JS context (and challenge pass) is established. */
    private suspend fun reload() {
        val view = webView ?: throw IOException("Соединение не инициализировано")
        pageDone = CompletableDeferred()
        withContext(Dispatchers.Main.immediate) { view.reload() }
        withTimeoutOrNull(12_000) { pageDone.await() }
        delay(400)
    }

    private suspend fun navigate(target: String, timeoutMs: Long) {
        val view = webView ?: throw IOException("Соединение не инициализировано")
        val done = CompletableDeferred<Unit>()
        loaded = done
        pendingUrl = target
        withContext(Dispatchers.Main.immediate) { view.loadUrl(target) }
        withTimeoutOrNull(timeoutMs) { done.await() }
    }

    private class Document(val url: String, val complete: Boolean, val text: String)

    /** Reads the rendered document in slices: one big evaluateJavascript call truncates. */
    private suspend fun readDocument(): Document? = withContext(Dispatchers.Main.immediate) {
        val view = webView ?: return@withContext null
        val head = evalString(
            view,
            "(document.readyState==='complete'?'1':'0') + '|' + location.href + '|' + " +
                "(document.contentType.indexOf('json')<0 ? 'HTML:' : '')",
        )
        val parts = head.split("|", limit = 3)
        if (parts.size < 3) return@withContext null
        val chunk = 40_000
        val sb = StringBuilder(parts[2])
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
        Document(parts[1], parts[0] == "1", sb.toString())
    }

    private suspend fun evalString(view: WebView, js: String): String {
        val done = CompletableDeferred<String>()
        view.evaluateJavascript(js) { raw -> done.complete(decodeJsString(raw ?: "")) }
        return withTimeout(15_000) { done.await() }
    }

    /** evaluateJavascript returns a JS string literal; JSON-escaped. */
    private fun decodeJsString(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("\"")) return trimmed
        return runCatching {
            (apiJson.parseToJsonElement(trimmed) as kotlinx.serialization.json.JsonPrimitive).content
        }.getOrDefault(trimmed)
    }

    private suspend fun fetchJson(path: String, timeoutMs: Long): Pair<Int, String> =
        withContext(Dispatchers.Main.immediate) {
            val view = webView ?: throw IOException("Соединение не инициализировано")
            // Pace requests: back-to-back fetches on the same page context fail far more often.
            delay(250)
            val id = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<Pair<Int, String>>()
            val parts = StringBuilder()
            pending[id] = deferred
            chunks[id] = parts
            val url = if (path.startsWith("http")) path else Api.BASE + path
            // Large bodies cannot cross the JS bridge in one call (190 KB silently dies), so
            // the page hands the text over in slices and then signals completion.
            val js = "(function(){try{fetch('${esc(url)}',{credentials:'include'})" +
                ".then(function(r){return r.text().then(function(t){" +
                "for(var i=0;i<t.length;i+=8192){BBB.onChunk('$id',t.substr(i,8192))}" +
                "BBB.onResult('$id',r.status)})})" +
                ".catch(function(e){BBB.onError('$id',String(e))})}catch(e){BBB.onError('$id',String(e))}})()"
            view.evaluateJavascript(js, null)
            try {
                withTimeout(timeoutMs) { deferred.await() }
            } finally {
                pending.remove(id)
                chunks.remove(id)
            }
        }

    private fun esc(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

    inner class Bridge {
        @JavascriptInterface
        fun onChunk(id: String, part: String) {
            chunks[id]?.append(part)
        }

        @JavascriptInterface
        fun onResult(id: String, status: Int) {
            val body = chunks.remove(id)?.toString().orEmpty()
            pending.remove(id)?.complete(status to body)
        }

        @JavascriptInterface
        fun onError(id: String, message: String) {
            pending.remove(id)?.complete(-1 to message)
        }
    }

    companion object {
        private const val TAG = "BBB"
    }
}
