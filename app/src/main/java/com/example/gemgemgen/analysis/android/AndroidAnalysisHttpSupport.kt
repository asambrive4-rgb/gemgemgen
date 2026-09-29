// 역할: AI 분석 및 인증 게이트웨이의 공통 HTTP 입출력과 오류 메시지 변환을 지원합니다.
package com.example.gemgemgen.analysis.android

import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal fun writeRequestBody(connection: HttpURLConnection, body: String) {
    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
        writer.write(body)
    }
}

internal fun readResponseBody(
    connection: HttpURLConnection,
    responseCode: Int = connection.responseCode
): String {
    val stream = if (responseCode in 200..299) {
        connection.inputStream
    } else {
        connection.errorStream
    }
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

internal fun extractJsonErrorMessage(json: Json, responseText: String): String {
    return runCatching {
        val root = json.parseToJsonElement(responseText).jsonObject
        val errorDescription = (root["error_description"] as? JsonPrimitive)?.contentOrNull
        if (!errorDescription.isNullOrBlank()) return@runCatching errorDescription

        val errorElement = root["error"]
        when (errorElement) {
            is JsonPrimitive -> errorElement.contentOrNull.orEmpty()
            is JsonObject -> {
                (errorElement["message"] as? JsonPrimitive)?.contentOrNull
                    ?: (errorElement["error"] as? JsonPrimitive)?.contentOrNull
                    ?: ""
            }
            else -> (root["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }
    }.getOrDefault("")
}

internal fun formatAnalysisNetworkError(
    error: Exception,
    serviceName: String,
    serverLabel: String,
    timeoutMillis: Int,
    timeoutGuidance: String = "잠시 후 다시 시도해 주세요."
): String {
    val timeoutSec = timeoutMillis / 1000
    return when (error) {
        is SocketTimeoutException ->
            "$serviceName 응답 시간 초과(타임아웃): 모델이 제한 시간(${timeoutSec}초) 내에 응답을 마치지 못했습니다. $timeoutGuidance"
        is UnknownHostException ->
            "네트워크 연결 실패: 인터넷 연결이 끊겼거나 $serverLabel 주소를 찾을 수 없습니다. Wi-Fi 또는 모바일 데이터 상태를 확인해 주세요."
        is ConnectException ->
            "$serviceName 서버 연결 실패: ${serverLabel}에 접속하지 못했습니다. 인터넷 상태나 방화벽/VPN 설정을 확인해 주세요."
        is SSLException ->
            "보안 연결(SSL/TLS) 오류: ${serverLabel}와의 안전한 통신 연결에 실패했습니다. 네트워크 환경 또는 시스템 날짜/시간을 확인해 주세요."
        else -> {
            val msg = error.message?.trim().orEmpty()
            if (msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true)) {
                "$serviceName 응답 시간 초과(타임아웃): 모델 응답이 지연되고 있습니다 (${timeoutSec}초 초과). $timeoutGuidance"
            } else if (msg.isNotBlank()) {
                "$serviceName 통신 오류 (${error.javaClass.simpleName}): $msg"
            } else {
                "$serviceName 통신 중 알 수 없는 오류가 발생했습니다 (${error.javaClass.simpleName})."
            }
        }
    }
}
