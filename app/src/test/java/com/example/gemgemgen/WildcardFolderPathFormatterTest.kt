package com.example.gemgemgen

import com.example.gemgemgen.ui.WildcardFolderPathFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class WildcardFolderPathFormatterTest {
    @Test
    fun summaryPath_returnsEmptySelectionTextForBlankUri() {
        assertEquals("선택된 폴더 없음", WildcardFolderPathFormatter.summaryPath(""))
    }

    @Test
    fun summaryPath_decodesTreeUriPath() {
        assertEquals(
            "Download/wildcard",
            WildcardFolderPathFormatter.summaryPath(
                "content://com.android.externalstorage.documents/tree/primary%3ADownload%2Fwildcard"
            )
        )
    }

    @Test
    fun summaryPath_decodesLastSegmentWhenTreeMarkerIsMissing() {
        assertEquals(
            "Pictures/wildcard",
            WildcardFolderPathFormatter.summaryPath("content://folder/primary%3APictures%2Fwildcard")
        )
    }
}
