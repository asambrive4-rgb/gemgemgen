package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewReadinessTest {
    @Test
    fun check_requiresPromptAndWildcardFolderOnly() {
        val ready = PreviewReadiness.check(
            promptTemplate = "portrait with __hair__",
            isWildcardDirectoryAccessible = true
        )

        assertTrue(ready.canPreview)
        assertEquals("미리보기를 생성할 수 있습니다.", ready.reason)
    }

    @Test
    fun check_isNotReadyWhenPromptIsBlank() {
        val readiness = PreviewReadiness.check(
            promptTemplate = "",
            isWildcardDirectoryAccessible = true
        )

        assertFalse(readiness.canPreview)
        assertEquals("프롬프트 템플릿을 입력해주세요.", readiness.reason)
    }

    @Test
    fun check_isNotReadyWhenWildcardFolderIsMissing() {
        val readiness = PreviewReadiness.check(
            promptTemplate = "portrait",
            isWildcardDirectoryAccessible = false
        )

        assertFalse(readiness.canPreview)
        assertEquals("wildcard 폴더를 먼저 선택해주세요.", readiness.reason)
    }
}
