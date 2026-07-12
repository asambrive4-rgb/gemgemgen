package com.example.gemgemgen.analysis.android

import com.example.gemgemgen.analysis.usecase.AnalysisException
import com.example.gemgemgen.analysis.usecase.GrokAuthGateway
import com.example.gemgemgen.analysis.usecase.GrokAuthSession
import com.example.gemgemgen.analysis.usecase.GrokDeviceLoginChallenge
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * xAI device-code OAuth (progrok / Hermes / OpenClaw 계열 public client).
 */
class AndroidGrokOAuthGateway : GrokAuthGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun startDeviceLogin(): GrokDeviceLoginChallenge {
        return try {
            val discovery = fetchDiscovery()
            val deviceEndpoint = discovery.deviceAuthorizationEndpoint
                ?: throw AnalysisException("xAI device code 로그인을 지원하지 않습니다.")

            val body = formBody(
                "client_id" to CLIENT_ID,
                "scope" to SCOPE
            )
            val response = postForm(deviceEndpoint, body)
            if (response.code !in 200..299) {
                throw AnalysisException("Grok 로그인 시작에 실패했습니다. (${response.code})")
            }
            val root = json.parseToJsonElement(response.body).jsonObject
            val deviceCode = root.string("device_code")
                ?: throw AnalysisException("Grok 로그인 응답이 올바르지 않습니다.")
            val userCode = root.string("user_code")
                ?: throw AnalysisException("Grok 로그인 응답이 올바르지 않습니다.")
            val verificationUri = root.string("verification_uri")
                ?: throw AnalysisException("Grok 로그인 URL을 받지 못했습니다.")
            GrokDeviceLoginChallenge(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUri = verificationUri,
                verificationUriComplete = root.string("verification_uri_complete"),
                expiresInSeconds = root.string("expires_in")?.toIntOrNull() ?: 900,
                intervalSeconds = root.string("interval")?.toIntOrNull() ?: 5,
                tokenEndpoint = discovery.tokenEndpoint
            )
        } catch (error: AnalysisException) {
            throw error
        } catch (error: Exception) {
            throw AnalysisException(
                "Grok 로그인 네트워크 오류: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    override suspend fun pollDeviceLogin(challenge: GrokDeviceLoginChallenge): GrokAuthSession? {
        return try {
            val body = formBody(
                "grant_type" to DEVICE_GRANT,
                "client_id" to CLIENT_ID,
                "device_code" to challenge.deviceCode
            )
            val response = postForm(challenge.tokenEndpoint, body)
            if (response.code in 200..299) {
                return parseTokenResponse(response.body, challenge.tokenEndpoint)
            }
            val error = parseOAuthError(response.body)
            when (error) {
                "authorization_pending" -> null
                "slow_down" -> throw AnalysisException("slow_down")
                "access_denied", "authorization_denied" ->
                    throw AnalysisException("로그인이 거부되었습니다.")
                "expired_token" ->
                    throw AnalysisException("로그인 코드가 만료되었습니다. 다시 시도해주세요.")
                else -> throw AnalysisException(
                    errorMessageFromOAuth(response.body)
                        .ifBlank { "Grok 로그인 확인에 실패했습니다. (${response.code})" }
                )
            }
        } catch (error: AnalysisException) {
            throw error
        } catch (error: Exception) {
            throw AnalysisException(
                "Grok 로그인 확인 네트워크 오류: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    override suspend fun refreshSession(session: GrokAuthSession): GrokAuthSession {
        return try {
            val endpoint = session.tokenEndpoint
                ?: throw AnalysisException("Grok 세션 갱신 정보가 없습니다.")
            val refreshToken = session.refreshToken
                ?: throw AnalysisException("Grok 세션 갱신 정보가 없습니다.")
            val body = formBody(
                "grant_type" to "refresh_token",
                "client_id" to CLIENT_ID,
                "refresh_token" to refreshToken
            )
            val response = postForm(endpoint, body)
            if (response.code == 403) {
                throw AnalysisException(
                    "Grok 구독 권한으로 API를 사용할 수 없습니다. (403) Gemini로 전환해 주세요."
                )
            }
            if (response.code !in 200..299) {
                throw AnalysisException(
                    errorMessageFromOAuth(response.body)
                        .ifBlank { "Grok 세션 갱신에 실패했습니다. (${response.code})" }
                )
            }
            val refreshed = parseTokenResponse(response.body, endpoint)
            refreshed.copy(
                refreshToken = refreshed.refreshToken ?: session.refreshToken,
                accountPreview = refreshed.accountPreview.ifBlank { session.accountPreview }
            )
        } catch (error: AnalysisException) {
            throw error
        } catch (error: Exception) {
            throw AnalysisException(
                "Grok 세션 갱신 네트워크 오류: ${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    private fun parseTokenResponse(body: String, tokenEndpoint: String): GrokAuthSession {
        val root = json.parseToJsonElement(body).jsonObject
        val accessToken = root.string("access_token")
            ?: throw AnalysisException("Grok 토큰 응답이 올바르지 않습니다.")
        val refreshToken = root.string("refresh_token")
        val expiresIn = root.string("expires_in")?.toLongOrNull()
        val idToken = root.string("id_token")
        val preview = emailFromIdToken(idToken)
            ?: "****${accessToken.takeLast(4)}"
        return GrokAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = expiresIn?.let { System.currentTimeMillis() + it * 1000L },
            tokenEndpoint = tokenEndpoint,
            accountPreview = preview
        )
    }

    private fun emailFromIdToken(idToken: String?): String? {
        if (idToken.isNullOrBlank()) return null
        return runCatching {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val payload = String(
                android.util.Base64.decode(
                    parts[1],
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                ),
                Charsets.UTF_8
            )
            json.parseToJsonElement(payload).jsonObject.string("email")
        }.getOrNull()
    }

    private data class Discovery(
        val tokenEndpoint: String,
        val deviceAuthorizationEndpoint: String?
    )

    private fun fetchDiscovery(): Discovery {
        val response = get(DISCOVERY_URL)
        if (response.code !in 200..299) {
            throw AnalysisException("Grok 로그인 서버 정보를 가져오지 못했습니다.")
        }
        val root = json.parseToJsonElement(response.body).jsonObject
        val tokenEndpoint = requireTrustedEndpoint(
            root.string("token_endpoint"),
            "token_endpoint"
        )
        val deviceEndpoint = root.string("device_authorization_endpoint")?.let {
            requireTrustedEndpoint(it, "device_authorization_endpoint")
        }
        return Discovery(
            tokenEndpoint = tokenEndpoint,
            deviceAuthorizationEndpoint = deviceEndpoint
        )
    }

    private fun requireTrustedEndpoint(url: String?, label: String): String {
        if (url.isNullOrBlank()) {
            throw AnalysisException("Grok OAuth $label 이(가) 없습니다.")
        }
        val parsed = runCatching { URL(url) }.getOrNull()
            ?: throw AnalysisException("Grok OAuth $label 이(가) 올바르지 않습니다.")
        if (parsed.protocol != "https") {
            throw AnalysisException("Grok OAuth $label 이(가) 안전하지 않습니다.")
        }
        val host = parsed.host.orEmpty()
        if (host != "x.ai" && !host.endsWith(".x.ai")) {
            throw AnalysisException("Grok OAuth $label 호스트가 허용되지 않습니다.")
        }
        return url
    }

    private data class HttpTextResponse(val code: Int, val body: String)

    private fun get(url: String): HttpTextResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        return readResponse(connection)
    }

    private fun postForm(url: String, body: String): HttpTextResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): HttpTextResponse {
        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return HttpTextResponse(code, body)
    }

    private fun formBody(vararg pairs: Pair<String, String>): String {
        return pairs.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8.name())}=" +
                URLEncoder.encode(value, Charsets.UTF_8.name())
        }
    }

    private fun parseOAuthError(body: String): String {
        return runCatching {
            json.parseToJsonElement(body).jsonObject.string("error").orEmpty()
        }.getOrDefault("")
    }

    private fun errorMessageFromOAuth(body: String): String {
        return runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj.string("error_description")
                ?: obj.string("error")
                ?: ""
        }.getOrDefault("")
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitive?.content
    }

    private companion object {
        // public Grok CLI client (progrok / Hermes / OpenClaw 계열, MIT)
        const val CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"
        const val SCOPE = "openid profile email offline_access grok-cli:access api:access"
        const val DISCOVERY_URL = "https://auth.x.ai/.well-known/openid-configuration"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
