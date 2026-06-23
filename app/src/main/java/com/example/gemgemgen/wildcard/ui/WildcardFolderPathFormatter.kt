package com.example.gemgemgen.wildcard.ui

import java.net.URLDecoder

object WildcardFolderPathFormatter {
    fun summaryPath(uriString: String): String {
        if (uriString.isBlank()) return "선택된 폴더 없음"

        val encodedTreePath = uriString.substringAfter("/tree/", missingDelimiterValue = "")
        if (encodedTreePath.isNotBlank()) {
            val treePath = decode(encodedTreePath)
            return treePath.substringAfter(':', treePath)
        }

        val encodedLastSegment = uriString.substringAfterLast('/', uriString)
        val lastSegment = decode(encodedLastSegment)
        if (lastSegment.isNotBlank()) {
            return lastSegment.substringAfter(':', lastSegment)
        }

        return uriString
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
