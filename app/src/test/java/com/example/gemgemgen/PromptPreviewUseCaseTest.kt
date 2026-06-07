package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class PromptPreviewUseCaseTest {
    @Test
    fun generate_returnsPromptPreviewResult() {
        val useCase = PromptPreviewUseCase(
            loadWildcards = {
                listOf(
                    WildcardSet(
                        token = "__hair__",
                        fileName = "hair.txt",
                        items = listOf("short black hair")
                    )
                )
            },
            promptGenerator = PromptGenerator(Random(0))
        )

        val result = useCase.generate(
            promptTemplate = "portrait with __hair__",
            repeatCountText = "2"
        )

        assertEquals("", result.error)
        assertEquals("와일드카드 파일 1개로 2개를 생성했습니다.", result.message)
        assertEquals(
            listOf("portrait with short black hair", "portrait with short black hair"),
            result.generatedPrompts.map { it.finalPrompt }
        )
    }

    @Test
    fun generate_returnsDisplayableFailureWhenWildcardLoadFails() {
        val useCase = PromptPreviewUseCase(
            loadWildcards = { error("폴더 권한 없음") }
        )

        val result = useCase.generate(
            promptTemplate = "portrait",
            repeatCountText = "1"
        )

        assertEquals(emptyList<GeneratedPrompt>(), result.generatedPrompts)
        assertEquals("", result.message)
        assertTrue(result.error.contains("생성 실패"))
    }
}
