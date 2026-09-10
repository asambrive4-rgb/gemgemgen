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
