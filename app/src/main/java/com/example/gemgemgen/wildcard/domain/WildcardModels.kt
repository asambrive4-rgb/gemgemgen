// 역할: 와일드카드 파일, 단어 목록, 와일드카드 세트 데이터 모델 및 폴더 접근 정책을 정의합니다.
package com.example.gemgemgen.wildcard.domain

data class WildcardSet(
    val token: String,
    val fileName: String,
    val items: List<String>
)

data class WildcardTextFile(
    val id: String,
    val fileName: String
)

object WildcardFileName {
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        return if (trimmed.endsWith(".txt", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed.txt"
        }
    }
}

class WildcardFileException(message: String) : RuntimeException(message)

enum class WildcardFolderAction {
    OpenDirectFolder,
    OpenStorageSettings,
    LaunchSafPicker
}

object WildcardFolderAccessPolicy {
    fun decideAction(
        hasAllFilesAccess: Boolean,
        isWildcardDirectoryAccessible: Boolean
    ): WildcardFolderAction {
        return when {
            hasAllFilesAccess -> WildcardFolderAction.OpenDirectFolder
            !isWildcardDirectoryAccessible -> WildcardFolderAction.OpenStorageSettings
            else -> WildcardFolderAction.LaunchSafPicker
        }
    }
}
