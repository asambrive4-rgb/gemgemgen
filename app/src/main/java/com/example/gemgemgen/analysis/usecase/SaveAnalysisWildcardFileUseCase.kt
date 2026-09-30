// 역할: AI 분석 결과를 와일드카드 파일로 저장하고 원문 치환을 준비하는 유스케이스입니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisTargetSegment
import com.example.gemgemgen.analysis.domain.AnalysisTargetSegmentPolicy
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.core.ClipboardGateway
import com.example.gemgemgen.wildcard.domain.WildcardFileName
import com.example.gemgemgen.wildcard.usecase.WildcardFileRepository
import kotlinx.coroutines.withContext

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
    private val clipboardGateway: ClipboardGateway,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    /**
     * Saves candidates to a wildcard file, replaces the target span in the source
     * with a `__token__`, and copies the replaced source to the clipboard.
     * Clipboard failure does not undo a successful file write.
     */
    suspend fun saveAndPrepareReplacedSource(
        fileNameInput: String,
        candidates: List<String>,
        overwrite: Boolean,
        sourcePrompt: String = "",
        targetSegment: AnalysisTargetSegment? = null
    ): AnalysisSaveAndReplaceResult = withContext(dispatchers.io) {
        require(candidates.none { '\n' in it || '\r' in it }) {
            "여러 줄의 원문을 보존한 후보는 한 줄 단위 와일드카드 파일로 저장할 수 없습니다. '생성' 카드에서 복사하거나 적용해 주세요."
        }
        val fileName = WildcardFileName.normalize(fileNameInput)
            ?: return@withContext AnalysisSaveAndReplaceResult.InvalidFileName
        val existingFile = repository.listFiles()
            .firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }

        if (existingFile != null && !overwrite) {
            return@withContext AnalysisSaveAndReplaceResult.FileExists(fileName)
        }

        val targetFile = existingFile ?: repository.createFile(fileName)
        repository.writeFile(targetFile, candidates.joinToString(separator = "\n"))

        val replacedSource = AnalysisTargetSegmentPolicy.replaceSegmentWithWildcardToken(
            source = sourcePrompt,
            segment = targetSegment,
            savedFileName = fileName
        )
        try {
            clipboardGateway.writeText(replacedSource)
            AnalysisSaveAndReplaceResult.Success(
                fileName = fileName,
                replacedSource = replacedSource,
                clipboardCopied = true
            )
        } catch (error: RuntimeException) {
            AnalysisSaveAndReplaceResult.Success(
                fileName = fileName,
                replacedSource = replacedSource,
                clipboardCopied = false,
                clipboardError = error.message
            )
        }
    }
}

