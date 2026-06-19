package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.net.Uri
import com.example.gemgemgen.wildcard.usecase.FolderSelectionResult
import com.example.gemgemgen.wildcard.usecase.WildcardFolderRepository

class AndroidWildcardFolderRepository(
    private val context: Context
) : WildcardFolderRepository {
    override fun save(folderUri: String): FolderSelectionResult {
        return try {
            WildcardFolderStore.saveFolderUri(context, Uri.parse(folderUri))
            FolderSelectionResult(message = "wildcard 폴더를 선택했습니다.")
        } catch (error: SecurityException) {
            FolderSelectionResult(
                error = "폴더 권한 저장 실패: ${error.message ?: "다시 선택해주세요."}"
            )
        }
    }
}
