// 역할: 와일드카드 폴더에 대한 읽기 및 쓰기 권한 접근 허용 여부를 판정합니다.
package com.example.gemgemgen.wildcard.domain

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
