package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import com.example.gemgemgen.automation.domain.*
import com.example.gemgemgen.automation.usecase.*
import com.example.gemgemgen.core.*
import com.example.gemgemgen.environment.android.*
import com.example.gemgemgen.environment.domain.*
import com.example.gemgemgen.environment.usecase.*
import com.example.gemgemgen.ui.*
import com.example.gemgemgen.wildcard.domain.*
import com.example.gemgemgen.wildcard.usecase.*
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
