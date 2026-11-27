package de.movy.cloudstream

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal class MovyPlayback(private val context: Context) {
    data class MediaRequest(val url: HttpUrl, val headers: Map<String, String>)

    data class Result(val stream: MediaRequest, val subtitles: List<MediaRequest>)

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(playerUrl: String): Result? = withContext(Dispatchers.Main.immediate) {
        val player = playerUrl.toHttpUrl()
        val origin = player.newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/')
        val requests = Channel<MediaRequest>(Channel.BUFFERED)
        val subtitles = ConcurrentHashMap<HttpUrl, MediaRequest>()
        val webView = WebView(context)

        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
            }
            val pageHeaders = mapOf(
                "Origin" to origin,
                "Referer" to "$origin/",
                "User-Agent" to webView.settings.userAgentString,
            )

            fun record(address: String, headers: Map<String, String> = emptyMap()) {
                val stream = manifestUrl.containsMatchIn(address)
                if (!stream && !subtitleUrl.containsMatchIn(address)) return
                val url = address.toHttpUrlOrNull() ?: return
                val request = MediaRequest(url, pageHeaders.mapValues { (key, value) ->
                    headers.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: value
                })
                if (stream) {
                    requests.trySend(request)
                } else {
                    subtitles[url] = request
                }
            }

            val width = View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY)
            val height = View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY)
            webView.measure(width, height)
            webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    if (!request.isForMainFrame && request.method == "GET") {
                        record(request.url.toString(), request.requestHeaders)
                    }
                    return null
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && request.url.host != player.host

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                    requests.close()
                }
            }
            webView.loadUrl(playerUrl)

            withTimeoutOrNull(18_000L) {
                awaitStream(webView, requests) { record(it) }
            }?.let { Result(it, subtitles.values.toList()) }
        } finally {
            requests.close()
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun awaitStream(
        webView: WebView,
        requests: Channel<MediaRequest>,
        record: (String) -> Unit,
    ): MediaRequest? = coroutineScope {
        // Some media requests are issued by the player rather than the WebView client.
        val inspectPlayer = launch {
            val seen = mutableSetOf<String>()
            while (isActive) {
                delay(750)
                webView.mediaUrls().filter(seen::add).forEach(record)
            }
        }
        try {
            val checked = mutableSetOf<MediaRequest>()
            for (request in requests) {
                if (checked.add(request) && isPlayable(request)) return@coroutineScope request
            }
            null
        } finally {
            inspectPlayer.cancelAndJoin()
        }
    }

    private suspend fun isPlayable(request: MediaRequest): Boolean {
        return try {
            val response = app.get(request.url.toString(), headers = request.headers, timeout = 5)
            if (!response.isSuccessful) return false
            val body = response.text.trimStart('\uFEFF', '\n', '\r', ' ', '\t')
            if (request.url.encodedPath.endsWith(".mpd", ignoreCase = true)) {
                body.startsWith("<") && body.contains("<MPD")
            } else {
                body.startsWith("#EXTM3U")
            }
        } catch (_: IOException) {
            false
        }
    }

    private suspend fun WebView.mediaUrls(): List<String> = suspendCancellableCoroutine { continuation ->
        evaluateJavascript(mediaUrlsScript) { value ->
            val urls = runCatching { JSONArray(value) }.getOrNull()
            continuation.resume(
                if (urls == null) emptyList() else List(urls.length()) { urls.getString(it) }
            )
        }
    }

    private companion object {
        val manifestUrl = Regex("""^https://[^?#]+\.(?:m3u8|mpd)(?:[?#]|$)""", RegexOption.IGNORE_CASE)
        val subtitleUrl = Regex("""^https://[^?#]+/subs/[^/?#]+\.(?:vtt|srt)(?:[?#]|$)""", RegexOption.IGNORE_CASE)
        val mediaUrlsScript = """
            (() => {
                const entries = performance.getEntriesByType('resource').map(entry => entry.name);
                for (const element of document.querySelectorAll('video, video source, video track')) {
                    entries.push(element.currentSrc || element.src || '');
                }
                return entries.filter(url => /\.(m3u8|mpd|vtt|srt)([?#]|$)/i.test(url));
            })()
        """.trimIndent()
    }
}
