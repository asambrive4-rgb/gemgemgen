// 역할: 와일드카드 파일명 유효성 검사 및 정규화 규칙을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardFileName
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
