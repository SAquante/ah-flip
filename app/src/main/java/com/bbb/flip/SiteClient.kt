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
 * read back. Navigation gives every request its own document, which avoids the two things that
 * made same-document `fetch()` unusable here — a JS context that dies after one call, and the
 * site's SPA scripts reloading the page mid-request.
 *
 * Rules learned the hard way, all encoded below:
 *  - WebView treats JSON -> JSON as a download (the document never commits), so `about:blank` is
 *    loaded in between, and it must be given time to actually finish;
 *  - `evaluateJavascript` blocks while a document is loading and can swallow the next navigation,
 *    so nothing is read until a fixed settle delay has passed;
 *  - `onPageFinished` is not reliable for JSON documents, so the document itself is inspected.
 */
class SiteClient {

    private val gate = Mutex()
    private var webView: WebView? = null
    private var ready = false
    private var bootstrapped = false

    /** URL of the most recent finished load: the only reliable "document is ready" signal. */
    @Volatile
    private var lastFinishedUrl = ""

    fun bind(view: WebView) {
        if (webView === view) return
        webView = view
        ready = false
        bootstrapped = false

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // Prices must be live; a cached /api/ response once showed an hour-old scan.
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
                if (url != null) lastFinishedUrl = url
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                val host = url.host ?: return null
                // Keep the document and the Cloudflare challenge; drop ads, trackers, fonts,
                // images and the SPA bundles (so no service worker, and a light page).
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
        try {
            fetchWithRetries(path)
        } catch (e: IOException) {
            // The Cloudflare pass can expire mid-session; solve it once more before giving up.
            if (e.message?.contains("ограничил") == true) throw e
            Log.w(TAG, "GET $path failed (${e.message}), re-checking the challenge")
            ready = false
            bootstrapped = false
            ensureReady()
            fetchWithRetries(path)
        }
    }

    private suspend fun fetchWithRetries(path: String): String {
        val url = if (path.startsWith("http")) path else Api.BASE + path
        var lastError = "нет ответа"
        repeat(3) { attempt ->
            val body = runCatching { loadJsonDocument(url, attempt) }.getOrElse {
                lastError = it.message ?: "ошибка"
                null
            }
            if (body != null) {
                Log.d(TAG, "GET $path -> 200 ${body.length}b")
                // An error envelope is still JSON, so surface it instead of empty data.
                val err = runCatching { apiJson.decodeFromString<ErrorEnvelope>(body) }
                    .getOrNull()?.takeIf { it.status >= 400 }
                if (err != null) {
                    if (err.status == 429) throw IOException(rateLimitMessage(body))
                    throw IOException("${err.status} ${err.detail ?: err.title ?: ""}".trim())
                }
                return body
            }
            Log.w(TAG, "GET $path attempt ${attempt + 1} failed: $lastError")
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
        delay(6_000)
        repeat(12) { attempt ->
            val probe = runCatching { loadJsonDocument("${Api.BASE}/api/config", 0) }.getOrNull()
            if (probe != null) {
                ready = true
                Log.d(TAG, "ready after ${attempt + 1} probe(s)")
                return
            }
            delay(700)
        }
        throw IOException("Cloudflare не пропустил запросы. Проверьте интернет и повторите.")
    }

    /**
     * Navigates to [url] and returns the document text. [attempt] widens the settle delays, because
     * a slow first document is the usual reason a round comes back empty.
     */
    private suspend fun loadJsonDocument(url: String, attempt: Int): String {
        // JSON -> JSON is treated as a download, so reset to a blank document first.
        loadUrl("about:blank")
        awaitFinish("about:blank", 8_000)
        loadUrl(url)
        // onPageFinished is the only signal that the JSON document has committed. Reading or
        // navigating earlier kills it — the reads below only start once the page reports done.
        if (!awaitFinish(url, 25_000)) {
            Log.d(TAG, "no load event for $url (attempt ${attempt + 1})")
            throw IOException("Аукцион вернул не данные")
        }
        // onPageFinished fires before the document is fully parsed, so read until the text stops
        // growing: a partial read yields truncated JSON.
        var previous = ""
        repeat(12) {
            val doc = readDocument()
            if (doc != null && doc.url.startsWith(url) && !doc.html) {
                val text = doc.text
                if (text.length > 1 && text == previous) {
                    if (runCatching { apiJson.parseToJsonElement(text) }.isSuccess) return text
                }
                previous = text
            }
            delay(400)
        }
        val doc = readDocument()
        Log.d(
            TAG,
            "attempt ${attempt + 1} for $url: " +
                (doc?.let { "url=${it.url.take(70)} html=${it.html} len=${it.text.length}" } ?: "no document"),
        )
        throw IOException("Аукцион вернул не данные")
    }

    /** Waits until a load of [target] has finished; false on timeout. */
    private suspend fun awaitFinish(target: String, timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs) {
            while (!lastFinishedUrl.startsWith(target)) delay(100)
            true
        } ?: false

    private suspend fun loadUrl(target: String) {
        val view = webView ?: throw IOException("Соединение не инициализировано")
        withContext(Dispatchers.Main.immediate) { view.loadUrl(target) }
    }

    private class Document(val url: String, val html: Boolean, val text: String)

    /** Reads the rendered document in slices: one big evaluateJavascript call truncates. */
    private suspend fun readDocument(): Document? = withContext(Dispatchers.Main.immediate) {
        val view = webView ?: return@withContext null
        val head = evalString(
            view,
            "location.href + '|' + (document.contentType.indexOf('json')<0 ? 'HTML' : 'JSON')",
        )
        if (head.isEmpty()) return@withContext null
        val parts = head.split("|", limit = 2)
        if (parts.size < 2) return@withContext null
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
        Document(parts[0], parts[1] == "HTML", sb.toString())
    }

    private suspend fun evalString(view: WebView, js: String): String {
        val done = CompletableDeferred<String>()
        view.evaluateJavascript(js) { raw -> done.complete(decodeJsString(raw ?: "")) }
        return withTimeout(10_000) { done.await() }
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
