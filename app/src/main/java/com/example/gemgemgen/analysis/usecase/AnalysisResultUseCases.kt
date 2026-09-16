// 역할: 분석 결과 텍스트를 클립보드에 복사하거나 프롬프트 입력창으로 전달합니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardFileException
import com.example.gemgemgen.wildcard.domain.WildcardFileName
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
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

sealed class AnalysisWildcardSaveResult {
    data class Success(val fileName: String) : AnalysisWildcardSaveResult()
    data class FileExists(val fileName: String) : AnalysisWildcardSaveResult()
    data object InvalidFileName : AnalysisWildcardSaveResult()
}

sealed class AnalysisSaveAndReplaceResult {
    data object InvalidFileName : AnalysisSaveAndReplaceResult()
    data class FileExists(val fileName: String) : AnalysisSaveAndReplaceResult()
    data class Success(
        val fileName: String,
        val replacedSource: String,
        val clipboardCopied: Boolean,
        val clipboardError: String? = null
    ) : AnalysisSaveAndReplaceResult()
}

class SaveAnalysisWildcardFileUseCase(
    private val repository: WildcardFileRepository,
    private val copyResults: CopyAnalysisResultsUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend fun save(
        fileNameInput: String,
        candidates: List<String>,
        overwrite: Boolean
    ): AnalysisWildcardSaveResult = withContext(dispatchers.io) {
        require(candidates.none { '\n' in it || '\r' in it }) {
            "여러 줄의 원문을 보존한 후보는 한 줄 단위 와일드카드 파일로 저장할 수 없습니다. '생성' 카드에서 복사하거나 적용해 주세요."
        }
        val fileName = WildcardFileName.normalize(fileNameInput)
            ?: return@withContext AnalysisWildcardSaveResult.InvalidFileName
        val existingFile = repository.listFiles()
            .firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }

        if (existingFile != null && !overwrite) {
            return@withContext AnalysisWildcardSaveResult.FileExists(fileName)
        }

        val targetFile = existingFile ?: repository.createFile(fileName)
        repository.writeFile(targetFile, candidates.joinToString(separator = "\n"))
        AnalysisWildcardSaveResult.Success(fileName)
    }

    /**
     * Saves candidates to a wildcard file, replaces the target span in the source
     * with a `__token__`, and copies the replaced source to the clipboard.
     * Clipboard failure does not undo a successful file write.
     */
    suspend fun saveAndPrepareReplacedSource(
        fileNameInput: String,
        candidates: List<String>,
        overwrite: Boolean,
        sourcePrompt: String,
        targetSegment: AnalysisTargetSegment?
    ): AnalysisSaveAndReplaceResult = when (
        val saveResult = save(
            fileNameInput = fileNameInput,
            candidates = candidates,
            overwrite = overwrite
        )
    ) {
        AnalysisWildcardSaveResult.InvalidFileName ->
            AnalysisSaveAndReplaceResult.InvalidFileName
        is AnalysisWildcardSaveResult.FileExists ->
            AnalysisSaveAndReplaceResult.FileExists(saveResult.fileName)
        is AnalysisWildcardSaveResult.Success -> {
            val replacedSource = AnalysisTargetSegmentPolicy.replaceSegmentWithWildcardToken(
                source = sourcePrompt,
                segment = targetSegment,
                savedFileName = saveResult.fileName
            )
            try {
                copyResults.copyText(replacedSource)
                AnalysisSaveAndReplaceResult.Success(
                    fileName = saveResult.fileName,
                    replacedSource = replacedSource,
                    clipboardCopied = true
                )
            } catch (error: RuntimeException) {
                AnalysisSaveAndReplaceResult.Success(
                    fileName = saveResult.fileName,
                    replacedSource = replacedSource,
                    clipboardCopied = false,
                    clipboardError = error.message
                )
            }
        }
    }
}
