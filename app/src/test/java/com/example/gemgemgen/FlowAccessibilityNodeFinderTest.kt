// 역할: Flow 앱 화면의 접근성 노드 탐색 상수, 키워드 매칭 세트 및 기본 설정을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.FlowAccessibilityNodeFinder
import com.example.gemgemgen.core.AppDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowAccessibilityNodeFinderTest {

    @Test
    fun sendCandidates_containExpectedFallbackKeywords() {
        val candidates = FlowAccessibilityNodeFinder.SEND_DESCRIPTIONS
        assertTrue(candidates.contains("생성"))
        assertTrue(candidates.contains("Generate"))
        assertTrue(candidates.contains("만들기"))
        assertTrue(candidates.contains("Create"))
    }

    @Test
    fun sendDescriptionsSet_containsAllLowercaseCandidates() {
        val set = FlowAccessibilityNodeFinder.SEND_DESCRIPTIONS_SET
        assertTrue(set.contains("생성"))
        assertTrue(set.contains("generate"))
        assertTrue(set.contains("만들기"))
        assertTrue(set.contains("create"))
        assertEquals(FlowAccessibilityNodeFinder.SEND_DESCRIPTIONS.size, set.size)
    }

    @Test
    fun nanoBananaProConstant_isExactModelName() {
        assertEquals("Nano Banana Pro", FlowAccessibilityNodeFinder.NANO_BANANA_PRO)
    }

    @Test
    fun modelKeywords_containNanoBanana() {
        val keywords = FlowAccessibilityNodeFinder.MODEL_KEYWORDS
        assertTrue(keywords.contains("Nano Banana"))
        assertTrue(keywords.contains("Banana"))
        assertTrue(keywords.contains("Imagen"))
    }

    @Test
    fun optionToggleKeywords_containImageCandidates() {
        val keywords = FlowAccessibilityNodeFinder.OPTION_TOGGLE_KEYWORDS
        assertTrue(keywords.contains("이미지"))
        assertTrue(keywords.contains("Image"))
    }

    @Test
    fun flowImageCountDefaults_matchSpecification() {
        assertEquals(4, AppDefaults.DEFAULT_FLOW_IMAGE_COUNT)
        assertEquals(listOf(1, 2, 3, 4), AppDefaults.FLOW_IMAGE_COUNT_OPTIONS)
    }
}
