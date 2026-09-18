// 역할: AI 분석 결과 텍스트를 클립보드에 복사하는 유스케이스입니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import kotlinx.coroutines.withContext

class CopyAnalysisResultsUseCase(
    private val clipboardGateway: ClipboardGateway,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun copy(candidates: List<String>) = withContext(dispatchers.io) {
        clipboardGateway.writeText(candidates.joinToString(separator = "\n"))
    }

    suspend fun copyText(text: String) = withContext(dispatchers.io) {
        clipboardGateway.writeText(text)
    }
}
