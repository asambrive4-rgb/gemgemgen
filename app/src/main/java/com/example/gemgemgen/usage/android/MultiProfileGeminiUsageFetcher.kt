// 역할: 계정 ID별 독립 WebView 프로필 및 격리 쿠키 저장소를 사용하여 5개 이상의 Google 계정 세션을 섞이지 않게 유지하고 사용량 DOM을 동시에 병렬 스크래핑합니다.
package com.example.gemgemgen.usage.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
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
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.usecase.GeminiUsageFetchResult
import com.example.gemgemgen.usage.usecase.MultiProfileGeminiUsageRemoteDataSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 계정별 WebView 프로필의 쿠키 스냅샷을 도메인별로 격리 보관하는 저장소.
 *
 * - 최소 5개 이상의 구글 계정이 서로 로그인 쿠키(SID, HSID, SSID, __Secure-1PSID 등)를 덮어쓰지 않도록
 *   계정 프로필(`webViewProfileName`) 단위로 쿠키 맵을 분리 저장 및 복원합니다.
 * - 복원 시 `.google.com` 도메인 쿠키와 호스트 전용 쿠키(`LSID`, `__Host-GAPS`, `COMPASS` 등)를
 *   이중으로 덮어쓰지 않도록 분류·중복 제거하여 Google의 `CookieMismatch` 오류를 방지합니다.
 */
class GeminiAccountCookieVault(
    private val sharedPreferences: SharedPreferences? = null
) {
    private val inMemoryVault = LinkedHashMap<String, Map<String, List<String>>>()

    @Synchronized
    fun loadCookiesForProfile(profileName: String): Map<String, List<String>> {
        if (profileName.isBlank()) return emptyMap()
        inMemoryVault[profileName]?.let { return it }

        val rawJson = sharedPreferences?.getString(prefKey(profileName), null)
        val parsed = sanitizeCookiesByOrigin(parseCookieMapJson(rawJson))
        if (parsed.isNotEmpty()) {
            inMemoryVault[profileName] = parsed
        }
        return parsed
    }

    @Synchronized
    fun saveCookiesForProfile(profileName: String, cookiesByOrigin: Map<String, List<String>>) {
        if (profileName.isBlank()) return
        val sanitized = sanitizeCookiesByOrigin(cookiesByOrigin)

        inMemoryVault[profileName] = sanitized
        sharedPreferences?.edit()
            ?.putString(prefKey(profileName), serializeCookieMapJson(sanitized))
            ?.apply()
    }

    @Synchronized
    fun clearProfile(profileName: String) {
        if (profileName.isBlank()) return
        inMemoryVault.remove(profileName)
        sharedPreferences?.edit()
            ?.remove(prefKey(profileName))
            ?.apply()
    }

    private fun prefKey(profileName: String): String = "cookies_$profileName"

    companion object {
        const val PREFS_NAME = "gemini_multi_account_cookie_vault"

        val TRACKED_GOOGLE_ORIGINS = listOf(
            "https://gemini.google.com",
            "https://accounts.google.com",
            "https://www.google.com",
            "https://google.com",
            "https://myaccount.google.com"
        )

        private val AUTH_SESSION_COOKIE_NAMES = setOf(
            "SID",
            "HSID",
            "SSID",
            "APISID",
            "SAPISID",
            "__Secure-1PSID",
            "__Secure-3PSID",
            "__Secure-1PAPISID",
            "__Secure-3PAPISID"
        )

        private val ACCOUNTS_HOST_ONLY_COOKIE_NAMES = setOf(
            "LSID",
            "__Host-1PLSID",
            "__Host-3PLSID",
            "__Host-GAPS",
            "GAPS",
            "SMSV",
            "ACCOUNT_CHOOSER",
            "OTZ"
        )

        private val GEMINI_HOST_ONLY_COOKIE_NAMES = setOf(
            "COMPASS"
        )

        /**
         * `CookieManager.getCookie(url)`가 반환하는 `k1=v1; k2=v2` 문자열을 개별 `name=value` 토큰 목록으로 분리하고,
         * 동일 이름의 쿠키가 중복 존재할 경우 가장 뒤에 위치한(최신 갱신된) 토큰만 남깁니다.
         */
        fun splitCookieHeader(rawCookieHeader: String?): List<String> {
            if (rawCookieHeader.isNullOrBlank()) return emptyList()
            val rawTokens = rawCookieHeader
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains("=") }
            return deduplicateCookieTokens(rawTokens)
        }

        /**
         * 쿠키 토큰 리스트에서 쿠키 이름 기준으로 중복을 제거합니다 (뒤쪽의 최신 값 우선 유지).
         */
        fun deduplicateCookieTokens(tokens: List<String>): List<String> {
            if (tokens.isEmpty()) return emptyList()
            val byName = LinkedHashMap<String, String>()
            for (raw in tokens) {
                val baseToken = raw.substringBefore(";").trim()
                val eqIndex = baseToken.indexOf('=')
                if (eqIndex <= 0) continue
                val name = baseToken.substring(0, eqIndex).trim()
                if (name.isEmpty()) continue
                byName.remove(name)
                byName[name] = baseToken
            }
            return byName.values.toList()
        }

        /**
         * 원시 `Cookie:` 헤더 문자열에 동일 이름의 쿠키가 2개 이상 중복 포함되어 있는지 검사합니다.
         */
        fun hasDuplicateCookieNames(rawCookieHeader: String?): Boolean {
            if (rawCookieHeader.isNullOrBlank()) return false
            val seen = HashSet<String>()
            val tokens = rawCookieHeader.split(";")
            for (raw in tokens) {
                val trimmed = raw.trim()
                val eqIndex = trimmed.indexOf('=')
                if (eqIndex <= 0) continue
                val name = trimmed.substring(0, eqIndex).trim()
                if (name.isNotEmpty() && !seen.add(name)) {
                    return true
                }
            }
            return false
        }

        /**
         * 쿠키 맵에 실제 구글 로그인 인증 세션 쿠키(`SID`, `__Secure-1PSID` 등)가 포함되어 있는지 확인합니다.
         */
        fun hasAuthenticatedSessionCookies(cookiesByOrigin: Map<String, List<String>>): Boolean {
            return cookiesByOrigin.values.any { list ->
                list.any { token ->
                    val name = token.substringBefore('=').trim()
                    name in AUTH_SESSION_COOKIE_NAMES
                }
            }
        }

        /**
         * 오리진별 쿠키 목록에서 불필요한 호스트 간 오염 및 중복 토큰을 정제합니다.
         */
        fun sanitizeCookiesByOrigin(
            cookiesByOrigin: Map<String, List<String>>
        ): Map<String, List<String>> {
            if (cookiesByOrigin.isEmpty()) return emptyMap()
            val result = LinkedHashMap<String, List<String>>()
            cookiesByOrigin.forEach { (origin, rawList) ->
                val normalizedOrigin = origin.trim()
                if (normalizedOrigin.isEmpty()) return@forEach
                val deduplicated = deduplicateCookieTokens(rawList).filter { token ->
                    val name = token.substringBefore('=').trim()
                    when {
                        normalizedOrigin.contains("accounts.google.com", ignoreCase = true) ->
                            name !in GEMINI_HOST_ONLY_COOKIE_NAMES
                        normalizedOrigin.contains("gemini.google.com", ignoreCase = true) ->
                            !name.startsWith("__Host-") && name !in ACCOUNTS_HOST_ONLY_COOKIE_NAMES
                        else ->
                            !name.startsWith("__Host-") &&
                                name !in ACCOUNTS_HOST_ONLY_COOKIE_NAMES &&
                                name !in GEMINI_HOST_ONLY_COOKIE_NAMES
                    }
                }
                if (deduplicated.isNotEmpty()) {
                    result[normalizedOrigin] = deduplicated
                }
            }
            return result
        }

        /**
         * 저장된 쿠키 맵을 `CookieManager.setCookie(url, value)` 호출 목록으로 변환합니다.
         *
         * 핵심 규칙:
         * 1. `.google.com` 공통 인증 쿠키(`SID`, `HSID`, `SSID`, `__Secure-*`, `SIDCC`, `NID` 등)는
         *    반드시 `Domain=.google.com; Path=/; Secure`로 **단 한 번만** 설정합니다.
         *    (`Domain` 속성 없이 호스트 쿠키로 중복 설정하면 Google 서버에서 `CookieMismatch`가 발생합니다.)
         * 2. `accounts.google.com` 전용 쿠키(`__Host-GAPS`, `LSID`, `SMSV`, `ACCOUNT_CHOOSER`, `OTZ`)는
         *    `Domain` 속성 없이 `https://accounts.google.com`에 **호스트 전용으로 단 한 번만** 설정합니다.
         * 3. `gemini.google.com` 전용 쿠키(`COMPASS`)는
         *    `Domain` 속성 없이 `https://gemini.google.com`에 **호스트 전용으로 단 한 번만** 설정합니다.
         */
        fun buildSetCookieCommands(
            cookiesByOrigin: Map<String, List<String>>
        ): List<Pair<String, String>> {
            val sanitizedByOrigin = sanitizeCookiesByOrigin(cookiesByOrigin)
            if (sanitizedByOrigin.isEmpty()) return emptyList()

            val domainCookiesByName = LinkedHashMap<String, String>()
            val accountsHostCookiesByName = LinkedHashMap<String, String>()
            val geminiHostCookiesByName = LinkedHashMap<String, String>()

            // www.google.com -> myaccount -> accounts -> gemini 순으로 순회하여 최신 토큰이 우선되도록 병합
            val orderedOrigins = (
                listOf(
                    "https://google.com",
                    "https://www.google.com",
                    "https://myaccount.google.com",
                    "https://accounts.google.com",
                    "https://gemini.google.com"
                ) + sanitizedByOrigin.keys
                ).distinct()

            for (origin in orderedOrigins) {
                val list = sanitizedByOrigin[origin].orEmpty()
                for (rawToken in deduplicateCookieTokens(list)) {
                    val name = rawToken.substringBefore('=').trim()
                    if (name.isEmpty()) continue

                    when {
                        name.startsWith("__Host-") || name in ACCOUNTS_HOST_ONLY_COOKIE_NAMES -> {
                            accountsHostCookiesByName.remove(name)
                            accountsHostCookiesByName[name] = rawToken
                        }
                        name in GEMINI_HOST_ONLY_COOKIE_NAMES -> {
                            geminiHostCookiesByName.remove(name)
                            geminiHostCookiesByName[name] = rawToken
                        }
                        else -> {
                            domainCookiesByName.remove(name)
                            domainCookiesByName[name] = rawToken
                        }
                    }
                }
            }

            val commands = ArrayList<Pair<String, String>>(
                domainCookiesByName.size + accountsHostCookiesByName.size + geminiHostCookiesByName.size
            )
            domainCookiesByName.values.forEach { token ->
                commands.add("https://www.google.com" to "$token; Domain=.google.com; Path=/; Secure")
            }
            accountsHostCookiesByName.values.forEach { token ->
                commands.add("https://accounts.google.com" to "$token; Path=/; Secure")
            }
            geminiHostCookiesByName.values.forEach { token ->
                commands.add("https://gemini.google.com" to "$token; Path=/; Secure")
            }
            return commands
        }

        fun serializeCookieMapJson(cookiesByOrigin: Map<String, List<String>>): String {
            val sanitized = sanitizeCookiesByOrigin(cookiesByOrigin)
            val obj = buildJsonObject {
                sanitized.forEach { (origin, cookies) ->
                    if (origin.isNotBlank() && cookies.isNotEmpty()) {
                        put(
                            origin,
                            buildJsonArray {
                                cookies.forEach { cookie ->
                                    if (cookie.isNotBlank()) {
                                        add(kotlinx.serialization.json.JsonPrimitive(cookie))
                                    }
                                }
                            }
                        )
                    }
                }
            }
            return obj.toString()
        }

        fun parseCookieMapJson(jsonString: String?): Map<String, List<String>> {
            if (jsonString.isNullOrBlank()) return emptyMap()
            return runCatching {
                val root = Json.parseToJsonElement(jsonString).jsonObject
                val map = LinkedHashMap<String, List<String>>()
                root.forEach { (origin, element) ->
                    val list = element.jsonArray.mapNotNull { item ->
                        item.jsonPrimitive.content.trim().takeIf { it.isNotEmpty() }
                    }
                    if (origin.isNotBlank() && list.isNotEmpty()) {
                        map[origin] = deduplicateCookieTokens(list)
                    }
                }
                sanitizeCookiesByOrigin(map)
            }.getOrDefault(emptyMap())
        }
    }
}

/**
 * 다중 Google 계정(5개 이상)의 독립 WebView 세션 격리 및 사용량 페이지 스크래핑 엔진.
 *
 * 1. AndroidX WebKit 멀티 프로필 API(`WebViewCompat.setProfile` / `ProfileStore`)를 통해 계정별 전용 프로필과 전용 `CookieManager`를 바인딩합니다.
 * 2. 동시에 모든 기기에서 완벽한 세션 격리 및 마이그레이션을 보장하기 위해 [GeminiAccountCookieVault]를 통해
 *    계정별 Google/Gemini 쿠키를 중복 없이 정제하여 백업 및 복원합니다.
 */
class MultiProfileGeminiUsageFetcher(
    context: Context,
    private val nowProvider: () -> Long = System::currentTimeMillis
) : MultiProfileGeminiUsageRemoteDataSource {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fallbackGlobalSessionMutex = Mutex()
    private val profileMutexLock = Mutex()
    private val profileSessionMutexes = LinkedHashMap<String, Mutex>()
    private val webViewsByProfile = LinkedHashMap<String, WebView>()
    private val sanitizedProfilesInSession = HashSet<String>()
    private val cookieVault = GeminiAccountCookieVault(
        appContext.getSharedPreferences(GeminiAccountCookieVault.PREFS_NAME, Context.MODE_PRIVATE)
    )

    @Volatile
    private var activeCookieProfileName: String? = null

    init {
        registerInstance(this)
    }

    private suspend fun resolveProfileSessionMutex(profileName: String): Mutex {
        if (!isMultiProfileSupported() || profileName.isBlank()) {
            return fallbackGlobalSessionMutex
        }
        return profileMutexLock.withLock {
            profileSessionMutexes.getOrPut(profileName) { Mutex() }
        }
    }

    override suspend fun fetchUsage(profile: GeminiAccountProfile): GeminiUsageFetchResult {
        val mutex = resolveProfileSessionMutex(profile.webViewProfileName)
        return mutex.withLock {
            withContext(Dispatchers.Main) {
                val webView = getOrCreateProfileWebView(profile)
                switchCookieSessionToProfileOnMain(profile.webViewProfileName, webView)

                val result = performWebViewScrapeOnMain(webView, profile.webViewProfileName)
                if (result.usageState is GeminiUsageState.Success) {
                    snapshotActiveCookiesOnMain(profile.webViewProfileName)
                }
                result
            }
        }
    }

    override suspend fun clearAccountSession(profile: GeminiAccountProfile) {
        val mutex = resolveProfileSessionMutex(profile.webViewProfileName)
        mutex.withLock {
            withContext(Dispatchers.Main) {
                val profileName = profile.webViewProfileName
                cookieVault.clearProfile(profileName)
                sanitizedProfilesInSession.remove(profileName)

                val profileCookieManager = resolveCookieManager(profileName)
                if (isMultiProfileSupported() || activeCookieProfileName == profileName) {
                    clearCookieManagerNow(profileCookieManager)
                    if (activeCookieProfileName == profileName) {
                        activeCookieProfileName = null
                    }
                }

                webViewsByProfile.remove(profileName)?.let { webView ->
                    runCatching {
                        webView.stopLoading()
                        webView.removeJavascriptInterface(BRIDGE_NAME)
                        webView.clearHistory()
                        webView.clearCache(true)
                        webView.loadUrl(BLANK_URL)
                        webView.destroy()
                    }
                }
            }
        }
    }

    /**
     * 인앱 [계정 추가] 또는 [재로그인] 시 해당 계정 전용 독립 세션으로 로그인 WebView를 초기화합니다.
     *
     * 중요: `WebViewCompat.setProfile` 및 쿠키 세션 전환이 `loadUrl()` 호출보다 반드시 먼저 실행되도록
     * 메인 스레드에서 호출된 경우 지연(post) 없이 즉시 동기 실행합니다.
     */
    fun prepareInteractiveLoginWebView(
        webView: WebView,
        profile: GeminiAccountProfile,
        clearExistingProfileCookies: Boolean = false
    ) {
        val prepareAction = {
            val profileName = profile.webViewProfileName
            tryBindWebViewProfile(webView, profileName)
            val cookieManager = resolveCookieManager(profileName)
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            if (clearExistingProfileCookies) {
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
    fun recoverFromCookieMismatchOnMain(
        profile: GeminiAccountProfile,
        webView: WebView
    ) {
        val recoverAction = {
            val profileName = profile.webViewProfileName
            cookieVault.clearProfile(profileName)
            val cookieManager = resolveCookieManager(profileName)
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
    fun commitInteractiveLoginSession(profile: GeminiAccountProfile) {
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

    private suspend fun performWebViewScrapeOnMain(
        webView: WebView,
        profileName: String
    ): GeminiUsageFetchResult =
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            var timeoutRunnable: Runnable? = null
            val profileCookieManager = resolveCookieManager(profileName)

            fun finishWith(result: GeminiUsageFetchResult) {
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

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (completed.get()) return
                    if (url.isNullOrBlank() || url == BLANK_URL) return

                    if (GeminiUsageParser.isAuthRedirectUrl(url)) {
                        finishWith(GeminiUsageFetchResult(GeminiUsageState.AuthRequired))
                        return
                    }

                    profileCookieManager.flush()
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

            mainHandler.postDelayed(timeoutRunnable, NATIVE_SAFETY_TIMEOUT_MS)
            webView.loadUrl(GeminiUsageParser.USAGE_URL)
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateProfileWebView(profile: GeminiAccountProfile): WebView {
        val profileName = profile.webViewProfileName
        webViewsByProfile[profileName]?.let { return it }

        return WebView(appContext).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            visibility = View.INVISIBLE
            alpha = 0f
            layout(0, 0, 1, 1)

            // 중요: WebViewCompat.setProfile은 WebView 생성 직후 settings나 CookieManager 접근 전에 호출되어야 함
            tryBindWebViewProfile(this, profileName)

            val cookieManager = resolveCookieManager(profileName)
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
            webViewsByProfile[profileName] = created
        }
    }

    private fun switchCookieSessionToProfileOnMain(profileName: String, webView: WebView) {
        if (profileName.isBlank()) return
        runCatching {
            val multiProfileActive = isMultiProfileSupported()
            val cookieManager = resolveCookieManager(profileName)
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            val previousProfile = activeCookieProfileName
            if (!multiProfileActive && !previousProfile.isNullOrBlank() && previousProfile != profileName) {
                snapshotActiveCookiesOnMain(previousProfile)
            }

            val savedCookies = cookieVault.loadCookiesForProfile(profileName)
            val geminiCookieHeader = cookieManager.getCookie("https://gemini.google.com")
            val accountsCookieHeader = cookieManager.getCookie("https://accounts.google.com")

            val hasDuplicatesInCookieManager =
                GeminiAccountCookieVault.hasDuplicateCookieNames(geminiCookieHeader) ||
                    GeminiAccountCookieVault.hasDuplicateCookieNames(accountsCookieHeader)

            val currentSnapshot = mapOf(
                "https://gemini.google.com" to GeminiAccountCookieVault.splitCookieHeader(geminiCookieHeader),
                "https://accounts.google.com" to GeminiAccountCookieVault.splitCookieHeader(accountsCookieHeader)
            )
            val hasNativeAuthSession = GeminiAccountCookieVault.hasAuthenticatedSessionCookies(currentSnapshot)

            val shouldRestoreFromVault = if (multiProfileActive) {
                // 멀티 프로필 지원 기기: 각 프로필이 독립된 CookieManager를 가지므로
                // 중복 오염이 감지되었거나, 네이티브 프로필이 아직 비어 있을 때만 Vault에서 정제 복원
                hasDuplicatesInCookieManager ||
                    (!hasNativeAuthSession && savedCookies.isNotEmpty()) ||
                    (profileName !in sanitizedProfilesInSession && savedCookies.isNotEmpty())
            } else {
                // 단일 프로필 기기 폴백: 프로필 전환 시마다 해당 계정의 쿠키로 교체
                previousProfile != profileName ||
                    hasDuplicatesInCookieManager ||
                    profileName !in sanitizedProfilesInSession
            }

            if (shouldRestoreFromVault) {
                clearCookieManagerNow(cookieManager)
                val sourceCookies = if (savedCookies.isNotEmpty()) savedCookies else currentSnapshot
                val commands = GeminiAccountCookieVault.buildSetCookieCommands(sourceCookies)
                for ((targetUrl, setCookieHeader) in commands) {
                    cookieManager.setCookie(targetUrl, setCookieHeader)
                }
                cookieManager.flush()
                if (sourceCookies.isNotEmpty()) {
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
            val cookieManager = resolveCookieManager(profileName)
            cookieManager.flush()
            val captured = LinkedHashMap<String, List<String>>()
            for (origin in GeminiAccountCookieVault.TRACKED_GOOGLE_ORIGINS) {
                val rawCookie = cookieManager.getCookie(origin)
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

    private fun clearCookieManagerNow(cookieManager: CookieManager) {
        runCatching {
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        }
    }

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
        const val POLL_INTERVAL_MS = 300L
        const val MAX_POLL_DURATION_MS = 6_000L
        private const val MAX_POLL_ATTEMPTS = (MAX_POLL_DURATION_MS / POLL_INTERVAL_MS).toInt()
        private const val NATIVE_SAFETY_TIMEOUT_MS = MAX_POLL_DURATION_MS + 1_500L

        @Volatile
        private var activeInstance: MultiProfileGeminiUsageFetcher? = null

        private fun registerInstance(instance: MultiProfileGeminiUsageFetcher) {
            activeInstance = instance
        }

        fun currentInstance(): MultiProfileGeminiUsageFetcher? = activeInstance

        /**
         * 현재 기기의 Android System WebView가 멀티 프로필(`WebViewFeature.MULTI_PROFILE`)을 지원하는지 확인합니다.
         */
        fun isMultiProfileSupported(): Boolean {
            return runCatching {
                WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
            }.getOrDefault(false)
        }

        /**
         * 계정 프로필명(`profileName`)에 대응하는 독립 [CookieManager]를 반환합니다.
         * 멀티 프로필 지원 기기에서는 `ProfileStore`의 프로필 전용 `CookieManager`를 반환합니다.
         */
        fun resolveCookieManager(profileName: String): CookieManager {
            if (profileName.isNotBlank()) {
                runCatching {
                    if (isMultiProfileSupported()) {
                        return ProfileStore.getInstance()
                            .getOrCreateProfile(profileName)
                            .cookieManager
                    }
                }
            }
            return CookieManager.getInstance()
        }

        /**
         * AndroidX Webkit(`WebViewCompat.setProfile`)이 지원되는 경우
         * 해당 `WebView`에 계정 전용 프로필 이름을 바인딩합니다.
         */
        fun tryBindWebViewProfile(webView: WebView, profileName: String): Boolean {
            if (profileName.isBlank()) return false
            return runCatching {
                if (!isMultiProfileSupported()) return false
                WebViewCompat.setProfile(webView, profileName)
                true
            }.getOrDefault(false)
        }

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
         * 페이지 로드 후 300ms 간격으로 최대 6초(20회)간 `document.body.innerText` 및 계정 이메일 요소를 폴링하는 스크립트.
         */
        fun buildPollingScript(): String = """
            (function() {
                if (window.__gemUsagePollingTimer) {
                    clearInterval(window.__gemUsagePollingTimer);
                }
                var attempts = 0;
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
