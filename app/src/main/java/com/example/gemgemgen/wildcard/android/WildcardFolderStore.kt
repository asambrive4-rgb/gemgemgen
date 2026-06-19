package com.example.gemgemgen.wildcard.android

import android.content.Context
import android.net.Uri

object WildcardFolderStore {
    private const val PREFS_NAME = "gemgemgen_settings"
    private const val KEY_WILDCARD_FOLDER_URI = "wildcard_folder_uri"

    fun getFolderUri(context: Context): Uri? {
        val value = prefs(context).getString(KEY_WILDCARD_FOLDER_URI, null)
        return value?.let(Uri::parse)
    }

    fun saveFolderUri(context: Context, uri: Uri) {
        prefs(context)
            .edit()
            .putString(KEY_WILDCARD_FOLDER_URI, uri.toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

