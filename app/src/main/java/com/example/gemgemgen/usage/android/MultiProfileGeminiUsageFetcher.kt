// 역할: 단일 공유 WebView와 조기 스크립트 주입·MutationObserver·CookieVault 세션 격리를 활용하여 다중 계정 제미나이 사용량을 초고속으로 수집하고 로그인 세션을 관리합니다.
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
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.usecase.GeminiUsageFetchResult
import com.example.gemgemgen.usage.usecase.MultiProfileGeminiUsageRemoteDataSource
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 다중 Google 계정(5개 이상)의 독립 쿠키 세션 격리 및 사용량 페이지 스크래핑 엔진.
 *
 * - 계정별로 WebView를 여러 개 생성하지 않고 **단 1개의 공유 WebView**만 사용하여 네이티브 RAM 점유를 최소화합니다.
 * - 새로고침 시마다 [GeminiAccountCookieVault]를 통해 해당 계정의 쿠키 스냅샷으로 안전하게 스위칭하고,
 *   모든 새로고침이 끝나면 공유 WebView를 즉시 `destroy()`하여 유휴 시 RAM 점유를 0으로 만듭니다.
 * - `shouldInterceptRequest`를 통해 폰트·이미지·미디어·광고/분석 요청을 차단하여 로딩 속도를 극대화합니다.
 */
class MultiProfileGeminiUsageFetcher(
    context: Context,
    private val nowProvider: () -> Long = System::currentTimeMillis
) : MultiProfileGeminiUsageRemoteDataSource, GeminiAccountLoginSessionHandler {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionMutex = Mutex()
    private var sharedWebView: WebView? = null
    private val sanitizedProfilesInSession = HashSet<String>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cookieVault = GeminiAccountCookieVault(
        appContext.getSharedPreferences(GeminiAccountCookieVault.PREFS_NAME, Context.MODE_PRIVATE)
    )

    @Volatile
    private var activeCookieProfileName: String? = null

    init {
        registerInstance(this)
    }

    override suspend fun fetchUsage(profile: GeminiAccountProfile): GeminiUsageFetchResult {
        return sessionMutex.withLock {
            // SharedPreferences 읽기 + JSON 파싱을 IO에서 먼저 끝내 메모리 캐시에 올려 둡니다.
            withContext(Dispatchers.IO) {
                cookieVault.loadCookiesForProfile(profile.webViewProfileName)
            }
            withContext(Dispatchers.Main) {
                val webView = getOrCreateSharedWebView()
                switchCookieSessionToProfileOnMain(profile.webViewProfileName, webView)

                val result = performWebViewScrapeOnMain(webView)
                if (result.usageState is GeminiUsageState.Success && !profile.isConflictingDetectedEmail(result.detectedEmail.orEmpty())) {
                    snapshotActiveCookiesOnMain(profile.webViewProfileName)
                }
                result
            }
        }
    }

    override suspend fun clearAccountSession(profile: GeminiAccountProfile) {
        sessionMutex.withLock {
            withContext(Dispatchers.Main) {
                val profileName = profile.webViewProfileName
                cookieVault.clearProfile(profileName)
                sanitizedProfilesInSession.remove(profileName)

                if (activeCookieProfileName == profileName) {
                    clearCookieManagerNow(CookieManager.getInstance())
                    activeCookieProfileName = null
                }
            }
        }
    }

    override suspend fun releaseIdleResources() {
        sessionMutex.withLock {
            withContext(Dispatchers.Main) {
                destroySharedWebViewOnMain()
            }
        }
    }

    private fun destroySharedWebViewOnMain() {
        val webView = sharedWebView ?: return
        sharedWebView = null
        runCatching {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.clearHistory()
            webView.clearCache(false)
            webView.loadUrl(BLANK_URL)
            webView.onPause()
            webView.destroy()
        }
    }

    /**
     * 인앱 [계정 추가] 또는 [재로그인] 시 해당 계정 전용 독립 세션으로 로그인 WebView를 초기화합니다.
     *
     * 중요: 쿠키 세션 전환이 `loadUrl()` 호출보다 반드시 먼저 실행되도록
     * 메인 스레드에서 호출된 경우 지연(post) 없이 즉시 동기 실행합니다.
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun prepareLoginWebView(
        webView: WebView,
        profile: GeminiAccountProfile,
        clearExistingCookies: Boolean
    ) {
        val prepareAction = {
            val profileName = profile.webViewProfileName
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = sanitizeWebViewUserAgent(userAgentString)
            }

            val previousProfile = activeCookieProfileName
            if (!previousProfile.isNullOrBlank() && previousProfile != profileName) {
                snapshotActiveCookiesOnMain(previousProfile)
            }

            if (clearExistingCookies) {
                cookieVault.clearProfile(profileName)
                clearCookieManagerNow(cookieManager)
                sanitizedProfilesInSession.add(profileName)
                activeCookieProfileName = profileName
            } else {
                switchCookieSessionToProfileOnMain(profileName, webView)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            prepareAction()
        } else {
            mainHandler.post(prepareAction)
        }
    }

    /**
     * 인앱 로그인 WebView에서 Google `CookieMismatch`(쿠키 설정 문제) 페이지 감지 시
     * 오염된 쿠키를 즉시 비우고 깨끗한 로그인 세션으로 복구합니다.
     */
    override fun recoverFromCookieMismatch(
        profile: GeminiAccountProfile,
        webView: WebView
    ) {
        val recoverAction = {
            val profileName = profile.webViewProfileName
            cookieVault.clearProfile(profileName)
            val cookieManager = CookieManager.getInstance()
            clearCookieManagerNow(cookieManager)
            sanitizedProfilesInSession.add(profileName)
            activeCookieProfileName = profileName
            runCatching {
                webView.stopLoading()
                webView.clearCache(true)
                webView.clearHistory()
                webView.loadUrl(GeminiUsageParser.LOGIN_URL)
            }
            Unit
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            recoverAction()
        } else {
            mainHandler.post(recoverAction)
        }
    }

    /**
     * 인앱 로그인 WebView에서 로그인 진행 중 또는 완료 시점의 쿠키를 해당 계정의 독립 프로필 저장소에 즉시 반영합니다.
     */
    override fun commitLoginSession(profile: GeminiAccountProfile) {
        val commitAction = {
            activeCookieProfileName = profile.webViewProfileName
            snapshotActiveCookiesOnMain(profile.webViewProfileName)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            commitAction()
        } else {
            mainHandler.post(commitAction)
        }
    }

    /**
     * 로그인 WebView를 정리합니다. 공유 CookieManager를 쓰는 새로고침과 경합하지 않도록
     * (필요 시 쿠키를 먼저 저장한 뒤) WebView를 완전히 파괴합니다.
     */
    override fun releaseLoginWebView(
        webView: WebView,
        profile: GeminiAccountProfile,
        commitCookies: Boolean
    ) {
        val releaseAction = {
            if (commitCookies) {
                commitLoginSession(profile)
            }
            runCatching {
                CookieManager.getInstance().flush()
                webView.stopLoading()
                webView.loadUrl(BLANK_URL)
                webView.clearHistory()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseAction()
        } else {
            mainHandler.post(releaseAction)
        }
    }

    /**
     * 저장하지 않고 취소한 신규 계정의 쿠키(금고 + 현재 CookieManager)를 폐기합니다.
     */
    override fun discardLoginSession(profile: GeminiAccountProfile) {
        val discardAction = {
            val profileName = profile.webViewProfileName
            cookieVault.clearProfile(profileName)
            sanitizedProfilesInSession.remove(profileName)
            if (activeCookieProfileName == profileName) {
                clearCookieManagerNow(CookieManager.getInstance())
                activeCookieProfileName = null
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            discardAction()
        } else {
            mainHandler.post(discardAction)
        }
    }

    private suspend fun performWebViewScrapeOnMain(
        webView: WebView
    ): GeminiUsageFetchResult =
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            var timeoutRunnable: Runnable? = null
            var pollTickRunnable: Runnable? = null

            fun finishWith(result: GeminiUsageFetchResult) {
                if (!completed.compareAndSet(false, true)) return
                timeoutRunnable?.let(mainHandler::removeCallbacks)
                pollTickRunnable?.let(mainHandler::removeCallbacks)
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    resetWebViewAfterScrape(webView)
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                } else {
                    mainHandler.post {
                        resetWebViewAfterScrape(webView)
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
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
                    finishWith(GeminiUsageFetchResult(GeminiUsageState.AuthRequired))
                } else {
                    finishWith(
                        GeminiUsageFetchResult(
                            GeminiUsageState.Error(
                                "제미나이 사용량 정보를 불러오지 못했습니다. (응답 시간 초과)"
                            )
                        )
                    )
                }
            }

            continuation.invokeOnCancellation {
                timeoutRunnable?.let(mainHandler::removeCallbacks)
                pollTickRunnable?.let(mainHandler::removeCallbacks)
                mainHandler.post {
                    resetWebViewAfterScrape(webView)
                }
            }

            webView.stopLoading()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.addJavascriptInterface(bridge, BRIDGE_NAME)

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val req = request ?: return null
                    if (!req.isForMainFrame && shouldBlockSubresourceRequest(req.url?.toString())) {
                        return createEmptyResourceResponse()
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString()
                    if (GeminiUsageParser.isAuthRedirectUrl(targetUrl)) {
                        finishWith(GeminiUsageFetchResult(GeminiUsageState.AuthRequired))
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (GeminiUsageParser.isAuthRedirectUrl(url)) {
                        finishWith(GeminiUsageFetchResult(GeminiUsageState.AuthRequired))
                    }
                }

                private fun injectPollingIfReady(view: WebView?, url: String?) {
                    if (completed.get()) return
                    if (url.isNullOrBlank() || url == BLANK_URL) return

                    if (GeminiUsageParser.isAuthRedirectUrl(url)) {
                        finishWith(GeminiUsageFetchResult(GeminiUsageState.AuthRequired))
                        return
                    }

                    view?.evaluateJavascript(POLLING_SCRIPT, null)
                    if (pollTickRunnable == null) {
                        val tick = object : Runnable {
                            override fun run() {
                                if (completed.get()) return
                                view?.evaluateJavascript(NATIVE_POLL_TICK_SCRIPT, null)
                                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
                            }
                        }
                        pollTickRunnable = tick
                        mainHandler.postDelayed(tick, POLL_INTERVAL_MS)
                    }
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    injectPollingIfReady(view, url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectPollingIfReady(view, url)
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
                            finishWith(GeminiUsageFetchResult(GeminiUsageState.AuthRequired))
                        } else {
                            val description = error?.description?.toString().orEmpty()
                            finishWith(
                                GeminiUsageFetchResult(
                                    GeminiUsageState.Error(
                                        if (description.isNotBlank()) {
                                            "사용량 페이지 로드 실패: $description"
                                        } else {
                                            "사용량 페이지를 불러오지 못했습니다."
                                        }
                                    )
                                )
                            )
                        }
                    }
                }
            }

            // 앱이 백그라운드일 때 스로틀링/일시정지된 WebView 타이머를 깨웁니다.
            runCatching {
                webView.onResume()
                webView.resumeTimers()
            }
            mainHandler.postDelayed(timeoutRunnable, NATIVE_SAFETY_TIMEOUT_MS)
            webView.loadUrl(GeminiUsageParser.USAGE_URL)
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateSharedWebView(): WebView {
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
                mediaPlaybackRequiresUserGesture = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = sanitizeWebViewUserAgent(userAgentString)
            }
        }.also { created ->
            sharedWebView = created
        }
    }

    private fun switchCookieSessionToProfileOnMain(profileName: String, webView: WebView) {
        if (profileName.isBlank()) return
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            val previousProfile = activeCookieProfileName
            if (!previousProfile.isNullOrBlank() && previousProfile != profileName) {
                snapshotActiveCookiesOnMain(previousProfile)
            }

            val savedCookies = cookieVault.loadCookiesForProfile(profileName)
            val geminiCookieHeader = cookieManager.getCookie("https://gemini.google.com")
            val accountsCookieHeader = cookieManager.getCookie("https://accounts.google.com")

            val hasDuplicatesInCookieManager =
                GeminiAccountCookieVault.hasDuplicateCookieNames(geminiCookieHeader) ||
                    GeminiAccountCookieVault.hasDuplicateCookieNames(accountsCookieHeader)

            // 단일 공유 WebView에서 프로필 전환 시마다 해당 계정의 쿠키로 교체하여 계정이 섞이지 않게 보장
            val shouldRestoreFromVault =
                previousProfile != profileName ||
                    hasDuplicatesInCookieManager ||
                    profileName !in sanitizedProfilesInSession

            if (shouldRestoreFromVault) {
                clearCookieManagerNow(cookieManager)
                val sourceCookies = if (savedCookies.isNotEmpty()) {
                    savedCookies
                } else if (previousProfile == profileName) {
                    mapOf(
                        "https://gemini.google.com" to GeminiAccountCookieVault.splitCookieHeader(geminiCookieHeader),
                        "https://accounts.google.com" to GeminiAccountCookieVault.splitCookieHeader(accountsCookieHeader)
                    )
                } else {
                    emptyMap()
                }
                val commands = GeminiAccountCookieVault.buildSetCookieCommands(sourceCookies)
                for ((targetUrl, setCookieHeader) in commands) {
                    cookieManager.setCookie(targetUrl, setCookieHeader)
                }
                if (savedCookies.isEmpty() && sourceCookies.isNotEmpty()) {
                    cookieVault.saveCookiesForProfile(profileName, sourceCookies)
                }
            }

            sanitizedProfilesInSession.add(profileName)
            activeCookieProfileName = profileName
        }
    }

    private fun snapshotActiveCookiesOnMain(profileName: String) {
        if (profileName.isBlank()) return
        runCatching {
            val cookieManager = CookieManager.getInstance()
            // 메인에서는 원시 쿠키 문자열만 읽고, 파싱·직렬화·디스크 저장·flush는 IO로 넘깁니다.
            val rawByOrigin = GeminiAccountCookieVault.TRACKED_GOOGLE_ORIGINS.associateWith { origin ->
                cookieManager.getCookie(origin)
            }
            ioScope.launch {
                runCatching {
                    cookieManager.flush()
                    val captured = LinkedHashMap<String, List<String>>()
                    for ((origin, rawCookie) in rawByOrigin) {
                        val tokens = GeminiAccountCookieVault.splitCookieHeader(rawCookie)
                        if (tokens.isNotEmpty()) {
                            captured[origin] = tokens
                        }
                    }
                    if (captured.isNotEmpty()) {
                        val existing = cookieVault.loadCookiesForProfile(profileName)
                        val existingHasAuth = GeminiAccountCookieVault.hasAuthenticatedSessionCookies(existing)
                        val capturedHasAuth = GeminiAccountCookieVault.hasAuthenticatedSessionCookies(captured)
                        // 이미 인증된 세션 쿠키가 저장되어 있는 계정은 비로그인/리다이렉트 상태의 임시 쿠키(NID만 있는 상태)로 덮어쓰지 않음
                        if (capturedHasAuth || !existingHasAuth) {
                            cookieVault.saveCookiesForProfile(profileName, captured)
                        }
                    }
                }
            }
        }
    }

    private fun clearCookieManagerNow(cookieManager: CookieManager) {
        runCatching {
            cookieManager.removeAllCookies(null)
        }
    }

    private fun resetWebViewAfterScrape(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.removeJavascriptInterface(BRIDGE_NAME)
        }
    }

    /**
     * WebView 내부 JS 폴링 스크립트와 네이티브 간 데이터를 전달하는 브릿지.
     */
    @Keep
    class UsageBridge(
        private val nowProvider: () -> Long,
        private val onResult: (GeminiUsageFetchResult) -> Unit
    ) {
        @JavascriptInterface
        fun onAuthRequired() {
            onResult(GeminiUsageFetchResult(GeminiUsageState.AuthRequired))
        }

        @JavascriptInterface
        fun onUsageParsed(
            fiveHourPercent: Int,
            fiveHourResetText: String,
            weeklyPercent: Int,
            weeklyResetText: String,
            detectedEmail: String,
            rawBodyText: String
        ) {
            val timestamp = nowProvider()
            val parsedFromBody = GeminiUsageParser.parse(
                bodyText = rawBodyText,
                updatedAt = timestamp
            )
            val success = parsedFromBody ?: GeminiUsageParser.createSuccess(
                fiveHourPercent = fiveHourPercent,
                fiveHourResetText = fiveHourResetText,
                weeklyPercent = weeklyPercent,
                weeklyResetText = weeklyResetText,
                updatedAt = timestamp
            )
            val resolvedEmail = GeminiUsageParser.extractAccountEmail(detectedEmail)
                ?: GeminiUsageParser.extractAccountEmail(rawBodyText)

            onResult(
                GeminiUsageFetchResult(
                    usageState = success,
                    detectedEmail = resolvedEmail
                )
            )
        }

        @JavascriptInterface
        fun onError(message: String) {
            onResult(
                GeminiUsageFetchResult(
                    GeminiUsageState.Error(
                        message.ifBlank { "제미나이 사용량 정보를 파싱하지 못했습니다." }
                    )
                )
            )
        }
    }

    companion object {
        const val BRIDGE_NAME = "UsageBridge"
        const val BLANK_URL = "about:blank"
        const val POLL_INTERVAL_MS = 80L
        const val MAX_POLL_DURATION_MS = 12_000L
        private const val MAX_POLL_ATTEMPTS = (MAX_POLL_DURATION_MS / POLL_INTERVAL_MS).toInt()
        private const val NATIVE_SAFETY_TIMEOUT_MS = 14_000L
        private const val NATIVE_POLL_TICK_SCRIPT = "window.__gemPollTick && window.__gemPollTick()"

        private val BLOCKED_SUBRESOURCE_EXTENSIONS = listOf(
            // 폰트
            ".woff2", ".woff", ".ttf", ".otf", ".eot",
            // 이미지 (svg, webp, gif, ico 포함)
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".bmp", ".tif", ".tiff", ".avif", ".heic", ".heif", ".cur",
            // 미디어
            ".mp4", ".webm", ".mp3", ".wav", ".ogg", ".m4a", ".avi", ".mov", ".flac", ".aac"
        )

        private val BLOCKED_SUBRESOURCE_HOSTS = listOf(
            // 폰트
            "fonts.gstatic.com",
            "fonts.googleapis.com",
            // 분석 / 로깅 / 텔레메트리 / 불필요 백그라운드 위젯
            "www.google-analytics.com",
            "ssl.google-analytics.com",
            "analytics.google.com",
            "www.googletagmanager.com",
            "googletagmanager.com",
            "play.google.com/log",
            "csp.withgoogle.com",
            "waa-pa.clients6.google.com",
            "ogads-pa.clients6.google.com",
            "clientservices.googleapis.com",
            // 광고 / 마케팅 / 트래커
            "googleads.g.doubleclick.net",
            "stats.g.doubleclick.net",
            "adservice.google.com",
            "pagead2.googlesyndication.com"
        )

        /**
         * 사용량 숫자(DOM 텍스트) 렌더링과 무관한 폰트, 이미지, 미디어, 광고/분석 로그 요청인지 판별합니다.
         */
        fun shouldBlockSubresourceRequest(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val lower = url.lowercase()
            if (BLOCKED_SUBRESOURCE_HOSTS.any { lower.contains(it) }) return true
            val pathBeforeQuery = lower.substringBefore('?').substringBefore('#')
            return BLOCKED_SUBRESOURCE_EXTENSIONS.any { pathBeforeQuery.endsWith(it) }
        }

        private fun createEmptyResourceResponse(): WebResourceResponse {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }

        @Volatile
        private var activeInstance: MultiProfileGeminiUsageFetcher? = null

        private fun registerInstance(instance: MultiProfileGeminiUsageFetcher) {
            activeInstance = instance
        }

        fun loginSessionHandler(): GeminiAccountLoginSessionHandler =
            checkNotNull(activeInstance) { "MultiProfileGeminiUsageFetcher가 아직 생성되지 않았습니다." }

        fun sanitizeWebViewUserAgent(rawUserAgent: String?): String {
            if (rawUserAgent.isNullOrBlank()) {
                return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }
            return rawUserAgent
                .replace("; wv", "")
                .replace(Regex("""Version/\d+\.\d+\s+"""), "")
                .trim()
        }

        val POLLING_SCRIPT: String = buildPollingScript()

        /**
         * 페이지 로드 후 1차 즉시 검사, MutationObserver(0ms 지연) 및 80ms fallback 폴링으로 사용량 DOM을 신속히 파싱하는 스크립트.
         */
        fun buildPollingScript(): String = """
            (function() {
                // 이전 주입 스크립트 정리 (중복 실행 방지)
                if (window.__gemUsagePollingTimer) {
                    clearInterval(window.__gemUsagePollingTimer);
                    window.__gemUsagePollingTimer = null;
                }
                if (window.__gemUsageObserver) {
                    try { window.__gemUsageObserver.disconnect(); } catch (e) {}
                    window.__gemUsageObserver = null;
                }
                var attempts = 0;
                var done = false;
                var lastRunAt = 0;
                var maxAttempts = $MAX_POLL_ATTEMPTS;

                function detectAccountEmail(bodyText) {
                    var emailRegex = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
                    try {
                        var nodes = document.querySelectorAll('[aria-label*="@"], [data-email], a[href*="SignOutOptions"]');
                        for (var i = 0; i < nodes.length; i++) {
                            var attr = (nodes[i].getAttribute('data-email') || nodes[i].getAttribute('aria-label') || nodes[i].textContent || '');
                            var match = attr.match(emailRegex);
                            if (match && match[0]) return match[0];
                        }
                    } catch (e) {}
                    var bodyMatch = (bodyText || '').match(emailRegex);
                    return bodyMatch && bodyMatch[0] ? bodyMatch[0] : '';
                }

                function extractResetLine(segment, fallbackSegment, defaultLabel, excludeLine) {
                    var combined = [segment || '', fallbackSegment || ''];
                    var resetKeywordRegex = /(?:resets?\b[^\n\r]*|[^\n\r]*(?:초기화|재설정|리셋|갱신)[^\n\r]*)/i;
                    var timeHintRegex = /(?:[^\n\r]*(?:\d+\s*(?:(?:h|hr|hrs|hour|hours|m|min|mins|minute|minutes|d|day|days)\b|(?:개월|주|일|시간|시|분|초))|\d{1,2}\s*월\s*\d{1,2}\s*일|\d{1,2}:\d{2}|\d{1,2}\s*\/\s*\d{1,2})[^\n\r]*)/i;
                    var noisePrefixRegex = /^(?:used|remaining|사용됨|사용중|사용|남음)\s*[-·•:|]?\s*/i;
                    var headerRegex = /^(?:5\s*[- ]?\s*hour|five\s*[- ]?\s*hour|current|daily|weekly|5\s*시간|단기|주간|일일|한도|사용량|limit|usage|quota)\b/i;

                    for (var s = 0; s < combined.length; s++) {
                        var lines = combined[s].split(/\r?\n/);
                        for (var i = 0; i < lines.length; i++) {
                            var cleaned = lines[i].trim().replace(noisePrefixRegex, '').replace(/^[-·•:|]+|[-·•:|]+$/g, '').trim();
                            if (!cleaned) continue;
                            var explicitMatch = cleaned.match(resetKeywordRegex);
                            if (explicitMatch && explicitMatch[0]) {
                                var matchedText = explicitMatch[0].trim();
                                if (excludeLine && matchedText === excludeLine) continue;
                                return matchedText;
                            }
                        }
                        for (var j = 0; j < lines.length; j++) {
                            var candidate = lines[j].trim().replace(noisePrefixRegex, '').replace(/^[-·•:|]+|[-·•:|]+$/g, '').trim();
                            if (!candidate || headerRegex.test(candidate)) continue;
                            if (excludeLine && candidate === excludeLine) continue;
                            if (timeHintRegex.test(candidate)) {
                                return candidate;
                            }
                        }
                    }
                    return defaultLabel;
                }

                function cleanup() {
                    done = true;
                    if (window.__gemUsagePollingTimer) {
                        clearInterval(window.__gemUsagePollingTimer);
                        window.__gemUsagePollingTimer = null;
                    }
                    if (window.__gemUsageObserver) {
                        try { window.__gemUsageObserver.disconnect(); } catch (e) {}
                        window.__gemUsageObserver = null;
                    }
                    window.__gemPollTick = null;
                }

                function runCheck(isTimeout) {
                    if (done) return;
                    lastRunAt = Date.now();
                    var href = (window.location && window.location.href ? window.location.href : '').toLowerCase();
                    if (href.indexOf('accounts.google.com') !== -1 || href.indexOf('/servicelogin') !== -1 || href.indexOf('/v3/signin') !== -1) {
                        cleanup();
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
                        cleanup();

                        var first = matches[0];
                        var second = matches[1];
                        var betweenSegment = bodyText.substring(first.end, second.start);
                        var beforeFirst = bodyText.substring(Math.max(0, first.start - 200), first.start);
                        var afterSecond = bodyText.substring(second.end, Math.min(bodyText.length, second.end + 400));

                        var firstReset = extractResetLine(betweenSegment, beforeFirst, '5시간 주기 자동 초기화', '');
                        var secondReset = extractResetLine(afterSecond, betweenSegment.slice(-200), '주간 주기 자동 초기화', firstReset);
                        var detectedEmail = detectAccountEmail(bodyText);

                        if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.onUsageParsed) {
                            window.$BRIDGE_NAME.onUsageParsed(
                                first.value,
                                firstReset,
                                second.value,
                                secondReset,
                                detectedEmail,
                                bodyText
                            );
                        }
                        return;
                    }

                    if (isTimeout || attempts >= maxAttempts) {
                        cleanup();
                        if (href.indexOf('accounts.google.com') !== -1) {
                            if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.onAuthRequired) {
                                window.$BRIDGE_NAME.onAuthRequired();
                            }
                        } else if (window.$BRIDGE_NAME && window.$BRIDGE_NAME.onError) {
                            window.$BRIDGE_NAME.onError('사용량 한도 수치가 렌더링되지 않았습니다. 로그인 상태를 확인해 주세요.');
                        }
                    }
                }

                // 1. 주입 즉시 1차 즉시 실행 검사 (0ms 지연)
                runCheck(false);

                // 2. 대상 DOM이 렌더링되는 순간 0ms 딜레이로 즉시 네이티브 브릿지를 호출하는 MutationObserver 등록
                if (!done && typeof MutationObserver !== 'undefined') {
                    try {
                        var targetNode = document.body || document.documentElement;
                        if (targetNode) {
                            var observer = new MutationObserver(function() {
                                if (done) return;
                                runCheck(false);
                            });
                            observer.observe(targetNode, {
                                childList: true,
                                subtree: true,
                                characterData: true
                            });
                            window.__gemUsageObserver = observer;
                        }
                    } catch (e) {}
                }

                // 3. 백그라운드 스로틀링이나 MutationObserver 미지원/누락 대비 네이티브 보조 틱
                window.__gemPollTick = function() {
                    if (done) return;
                    if (Date.now() - lastRunAt >= $POLL_INTERVAL_MS - 30) {
                        attempts++;
                        runCheck(attempts >= maxAttempts);
                    }
                };

                // 4. Fallback 주기적 폴링 타이머 (초기 인터벌 150ms)
                if (!done && attempts < maxAttempts) {
                    window.__gemUsagePollingTimer = setInterval(function() {
                        if (done) return;
                        attempts++;
                        runCheck(attempts >= maxAttempts);
                    }, $POLL_INTERVAL_MS);
                }
            })();
        """.trimIndent()
    }
}
