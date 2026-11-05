// 역할: 원격 명령 실행 결과(성공 또는 에러 메시지를 포함한 실패)를 나타냅니다.
package com.example.gemgemgen.automation.domain

sealed interface RemoteActionResult {
    data object Success : RemoteActionResult
    data class Failure(val message: String) : RemoteActionResult
}
