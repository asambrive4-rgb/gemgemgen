package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WildcardFileNameTest {
    @Test
    fun normalize_addsTxtExtensionWhenMissing() {
        assertEquals("hair.txt", WildcardFileName.normalize("hair"))
    }

    @Test
    fun normalize_keepsExistingTxtExtension() {
        assertEquals("hair.txt", WildcardFileName.normalize(" hair.txt "))
    }

    @Test
    fun normalize_rejectsBlankFileName() {
        assertNull(WildcardFileName.normalize("   "))
    }
}
