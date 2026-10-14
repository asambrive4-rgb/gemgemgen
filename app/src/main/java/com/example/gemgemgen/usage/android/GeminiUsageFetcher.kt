// 역할: 숨겨진 1x1 WebView와 @JavascriptInterface(UsageBridge)를 사용해 제미나이 사용량 페이지 DOM을 폴링 및 파싱합니다.
package com.example.gemgemgen.usage.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.usecase.GeminiUsageRemoteDataSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 화면에 보이지 않는 1x1 오프스크린 WebView에서 `https://gemini.google.com/usage`를 로드하고,
 * `@JavascriptInterface` ([UsageBridge])를 통해 300ms 간격으로 최대 6초간 DOM 텍스트를 폴링하여
 * 5시간 단기 한도 및 주간 누적 한도를 추출하는 스크래퍼입니다.
 */
class GeminiUsageFetcher(
    context: Context,
    private val nowProvider: () -> Long = System::currentTimeMillis
) : GeminiUsageRemoteDataSource {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var sharedWebView: WebView? = null

    override suspend fun fetchUsage(): GeminiUsageState = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            val webView = getOrCreateHeadlessWebView()

            var timeoutRunnable: Runnable? = null

            fun finishWith(result: GeminiUsageState) {
                if (!completed.compareAndSet(false, true)) return
                timeoutRunnable?.let(mainHandler::removeCallbacks)
                mainHandler.post {
                    resetWebViewToBlank(webView)
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }

            val bridge = UsageBridge(
                nowProvider = nowProvider,
                onResult = ::finishWith
            )

            timeoutRunnable = Runnable {
                val currentUrl = runCatching { webView.url }.getOrNull()
                if (GeminiUsageParser.isAuthRedirectUrl(currentUrl)) {
                    finishWith(GeminiUsageState.AuthRequired)
                } else {
                    finishWith(
                        GeminiUsageState.Error(
                            "제미나이 사용량 정보를 불러오지 못했습니다. (응답 시간 초과)"
                        )
                    )
                }
            }

            continuation.invokeOnCancellation {
                timeoutRunnable?.let(mainHandler::removeCallbacks)
                mainHandler.post {
                    resetWebViewToBlank(webView)
                }
            }

            webView.stopLoading()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.addJavascriptInterface(bridge, BRIDGE_NAME)

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString()
                    if (GeminiUsageParser.isAuthRedirectUrl(targetUrl)) {
                        finishWith(GeminiUsageState.AuthRequired)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (GeminiUsageParser.isAuthRedirectUrl(url)) {
                        finishWith(GeminiUsageState.AuthRequired)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (completed.get()) return
                    if (url.isNullOrBlank() || url == BLANK_URL) return

                    if (GeminiUsageParser.isAuthRedirectUrl(url)) {
                        finishWith(GeminiUsageState.AuthRequired)
                        return
                    }

                    CookieManager.getInstance().flush()
                    view?.evaluateJavascript(buildPollingScript(), null)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val failingUrl = request.url?.toString()
                        if (GeminiUsageParser.isAuthRedirectUrl(failingUrl)) {
                            finishWith(GeminiUsageState.AuthRequired)
                        } else {
                            val description = error?.description?.toString().orEmpty()
                            finishWith(
                                GeminiUsageState.Error(
                                    if (description.isNotBlank()) {
                                        "사용량 페이지 로드 실패: $description"
                                    } else {
                                        "사용량 페이지를 불러오지 못했습니다."
                                    }
                                )
                            )
                        }
                    }
                }
            }

            mainHandler.postDelayed(timeoutRunnable, NATIVE_SAFETY_TIMEOUT_MS)
            webView.loadUrl(GeminiUsageParser.USAGE_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateHeadlessWebView(): WebView {
        sharedWebView?.let { return it }

        return WebView(appContext).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            visibility = View.INVISIBLE
            alpha = 0f
            layout(0, 0, 1, 1)

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = false
                blockNetworkImage = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                userAgentString = sanitizeWebViewUserAgent(userAgentString)
            }
        }.also { created ->
            sharedWebView = created
        }
    }

    /**
     * 파싱 완료 또는 취소 시 메모리 누수 방지를 위해 `about:blank`를 로드하고 브릿지를 정리합니다.
     */
    private fun resetWebViewToBlank(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.loadUrl(BLANK_URL)
            webView.clearHistory()
        }
    }

    /**
     * WebView 내부 JS 폴링 스크립트와 네이티브 간 데이터를 전달하는 브릿지.
     */
    @Keep
    class UsageBridge(
        private val nowProvider: () -> Long,
        private val onResult: (GeminiUsageState) -> Unit
    ) {
        @JavascriptInterface
        fun onAuthRequired() {
            onResult(GeminiUsageState.AuthRequired)
        }

        @JavascriptInterface
        fun onUsageParsed(
            fiveHourPercent: Int,
            fiveHourResetText: String,
            weeklyPercent: Int,
            weeklyResetText: String,
            rawBodyText: String
        ) {
            val timestamp = nowProvider()
            val parsedFromBody = GeminiUsageParser.parse(
                bodyText = rawBodyText,
                updatedAt = timestamp
            )
            val success = if (parsedFromBody != null) {
                parsedFromBody.copy(
                    fiveHourResetText = fiveHourResetText.trim().ifBlank { parsedFromBody.fiveHourResetText },
                    weeklyResetText = weeklyResetText.trim().ifBlank { parsedFromBody.weeklyResetText }
                )
            } else {
                GeminiUsageParser.createSuccess(
                    fiveHourPercent = fiveHourPercent,
                    fiveHourResetText = fiveHourResetText,
                    weeklyPercent = weeklyPercent,
                    weeklyResetText = weeklyResetText,
                    updatedAt = timestamp
                )
            }
            onResult(success)
        }

        @JavascriptInterface
        fun onError(message: String) {
            onResult(
                GeminiUsageState.Error(
                    message.ifBlank { "제미나이 사용량 정보를 파싱하지 못했습니다." }
                )
            )
        }
    }

    companion object {
        const val BRIDGE_NAME = "UsageBridge"
        const val BLANK_URL = "about:blank"
        const val POLL_INTERVAL_MS = 300L
        const val MAX_POLL_DURATION_MS = 6_000L
        private const val MAX_POLL_ATTEMPTS = (MAX_POLL_DURATION_MS / POLL_INTERVAL_MS).toInt() // 20회
        private const val NATIVE_SAFETY_TIMEOUT_MS = MAX_POLL_DURATION_MS + 1_500L

        /**
         * Google 계정 로그인이 WebView 기본 `; wv` User-Agent를 차단하지 않도록 표준 모바일 Chrome 형태로 정제합니다.
         */
        fun sanitizeWebViewUserAgent(rawUserAgent: String?): String {
            if (rawUserAgent.isNullOrBlank()) {
                return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }
            return rawUserAgent
                .replace("; wv", "")
                .replace(Regex("""Version/\d+\.\d+\s+"""), "")
                .trim()
        }

        /**
         * 페이지 로드 후 300ms 간격으로 최대 6초(20회)간 `document.body.innerText`를 폴링하는 스크립트.
         * - `accounts.google.com` 리다이렉트 감지 시 즉시 `UsageBridge.onAuthRequired()` 호출
         * - `%` 수치가 2개 이상 렌더링되면 첫 번째(5시간 한도)와 두 번째(주간 한도) 퍼센트 및 리셋 문구를 추출해 `UsageBridge.onUsageParsed(...)` 호출
         */
        fun buildPollingScript(): String = """
            (function() {
                if (window.__gemUsagePollingTimer) {
                    clearInterval(window.__gemUsagePollingTimer);
                }
                var attempts = 0;
                var maxAttempts = $MAX_POLL_ATTEMPTS;

                function extractResetLine(segment, fallbackSegment, defaultLabel) {
                    var combined = [segment || '', fallbackSegment || ''];
                    var resetKeywordRegex = /(?:resets?\b[^\n\r]*|[^\n\r]*(?:초기화|재설정|리셋|갱신)[^\n\r]*)/i;
                    var timeHintRegex = /(?:[^\n\r]*\b(?:\d+\s*(?:h|hr|hrs|hour|hours|m|min|mins|minute|minutes|d|day|days|시간|분|일)\b|\d{1,2}:\d{2})[^\n\r]*)/i;
                    var noisePrefixRegex = /^(?:used|remaining|사용됨|사용중|사용|남음)\s*[-·•:|]?\s*/i;
                    var headerRegex = /^(?:5\s*[- ]?\s*hour|five\s*[- ]?\s*hour|current|daily|weekly|5\s*시간|단기|주간|일일|한도|사용량|limit|usage|quota)\b/i;

                    for (var s = 0; s < combined.length; s++) {
                        var lines = combined[s].split(/\r?\n/);
                        for (var i = 0; i < lines.length; i++) {
                            var cleaned = lines[i].trim().replace(noisePrefixRegex, '').replace(/^[-·•:|]+|[-·•:|]+$/g, '').trim();
                            if (!cleaned) continue;
                            var explicitMatch = cleaned.match(resetKeywordRegex);
                            if (explicitMatch && explicitMatch[0]) {
                                return explicitMatch[0].trim();
                            }
                        }
                        for (var j = 0; j < lines.length; j++) {
                            var candidate = lines[j].trim().replace(noisePrefixRegex, '').replace(/^[-·•:|]+|[-·•:|]+$/g, '').trim();
                            if (!candidate || headerRegex.test(candidate)) continue;
                            if (timeHintRegex.test(candidate)) {
                                return candidate;
                            }
                        }
                    }
                    return defaultLabel;
                }

                function pollDom() {
                    attempts++;
                    var href = (window.location && window.location.href ? window.location.href : '').toLowerCase();
                    if (href.indexOf('accounts.google.com') !== -1 || href.indexOf('/servicelogin') !== -1 || href.indexOf('/v3/signin') !== -1) {
                        clearInterval(window.__gemUsagePollingTimer);
                        window.__gemUsagePollingTimer = null;
                        if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.onAuthRequired) {
                            window.$BRIDGE_NAME.onAuthRequired();
                        }
                        return;
                    }

                    var bodyText = (document.body && document.body.innerText) ? document.body.innerText : '';
                    var pctRegex = /(\d{1,3})\s*%/g;
                    var matches = [];
                    var m;
                    while ((m = pctRegex.exec(bodyText)) !== null) {
                        matches.push({
                            value: parseInt(m[1], 10),
                            start: m.index,
                            end: m.index + m[0].length
                        });
                    }

                    if (matches.length >= 2) {
                        clearInterval(window.__gemUsagePollingTimer);
                        window.__gemUsagePollingTimer = null;

                        var first = matches[0];
                        var second = matches[1];
                        var betweenSegment = bodyText.substring(first.end, second.start);
                        var beforeFirst = bodyText.substring(Math.max(0, first.start - 200), first.start);
                        var afterSecond = bodyText.substring(second.end, Math.min(bodyText.length, second.end + 400));

                        var firstReset = extractResetLine(betweenSegment, beforeFirst, '5시간 주기 자동 초기화');
                        var secondReset = extractResetLine(afterSecond, betweenSegment.slice(-200), '주간 주기 자동 초기화');

                        if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.onUsageParsed) {
                            window.$BRIDGE_NAME.onUsageParsed(
                                first.value,
                                firstReset,
                                second.value,
                                secondReset,
                                bodyText
                            );
                        }
                        return;
                    }

                    if (attempts >= maxAttempts) {
                        clearInterval(window.__gemUsagePollingTimer);
                        window.__gemUsagePollingTimer = null;
                        if (href.indexOf('accounts.google.com') !== -1) {
                            if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.onAuthRequired) {
                                window.$BRIDGE_NAME.onAuthRequired();
                            }
                        } else if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.onError) {
                            window.$BRIDGE_NAME.onError('사용량 한도 수치가 렌더링되지 않았습니다. 로그인 상태를 확인해 주세요.');
                        }
                    }
                }

                pollDom();
                if (attempts < maxAttempts) {
                    window.__gemUsagePollingTimer = setInterval(pollDom, $POLL_INTERVAL_MS);
                }
            })();
        """.trimIndent()
    }
}
