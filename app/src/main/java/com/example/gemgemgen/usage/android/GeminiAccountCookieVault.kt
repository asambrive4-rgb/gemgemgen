// 역할: 다중 제미나이 계정 간 WebView 세션 쿠키가 섞이지 않도록 계정별로 쿠키를 백업·복원·격리하는 볼트입니다.
package com.example.gemgemgen.usage.android

import android.content.SharedPreferences
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
internal class GeminiAccountCookieVault(
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
