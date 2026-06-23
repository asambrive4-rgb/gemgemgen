package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.gemgemgen.wildcard.usecase.FolderSelectionResult
import com.example.gemgemgen.wildcard.usecase.WildcardFolderRepository

class AndroidWildcardFolderRepository(
    private val context: Context
) : WildcardFolderRepository {
    override fun save(folderUri: String): FolderSelectionResult {
        return try {
            val uri = Uri.parse(folderUri)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            WildcardFolderStore.saveFolderUri(context, uri)
            FolderSelectionResult.Success
        } catch (error: SecurityException) {
            FolderSelectionResult.Failure(error.message)
        }
    }
}
