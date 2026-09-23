// 역할: 프롬프트 상용구 모델 생성, 수정 및 유스케이스 후보 변환 로직을 검증합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptSnippet
import com.example.gemgemgen.automation.domain.WildcardTokenAutocomplete.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptSnippetTest {

    private class FakePromptSnippetRepository(
        private var snippets: List<PromptSnippet> = emptyList()
    ) : PromptSnippetRepository {
        override fun load(): List<PromptSnippet> = snippets
        override fun save(snippets: List<PromptSnippet>) {
            this.snippets = snippets
        }
    }

    @Test
    fun getPromptSnippetCandidatesUseCase_transformsSnippetsCorrectly() {
        val fakeRepo = FakePromptSnippetRepository(
            listOf(
                PromptSnippet(shortcut = "품질", content = "masterpiece, best quality"),
                PromptSnippet(shortcut = "구도", content = "cinematic lighting, dynamic angle")
            )
        )
        val useCase = GetPromptSnippetCandidatesUseCase(fakeRepo)

        val candidates = useCase()

        assertEquals(2, candidates.size)
        // 구도(2), 품질(2) -> 이름순
        assertTrue(candidates.all { it.type == Candidate.Type.SNIPPET })
        assertTrue(candidates.any { it.name == "품질" && it.token == "masterpiece, best quality" && it.displayText == "품질" })
        assertTrue(candidates.any { it.name == "구도" && it.token == "cinematic lighting, dynamic angle" && it.displayText == "구도" })
    }

    @Test
    fun getPromptSnippetCandidatesUseCase_handlesEmptyRepository() {
        val fakeRepo = FakePromptSnippetRepository(emptyList())
        val useCase = GetPromptSnippetCandidatesUseCase(fakeRepo)

        val candidates = useCase()

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun updatePromptSnippet_updatesShortcutAndContentCorrectly() {
        val initialSnippet = PromptSnippet(id = "snip-1", shortcut = "품질", content = "best quality")
        val fakeRepo = FakePromptSnippetRepository(listOf(initialSnippet))
        val useCase = GetPromptSnippetCandidatesUseCase(fakeRepo)

        // 수정 전 확인
        val beforeCandidates = useCase()
        assertEquals(1, beforeCandidates.size)
        assertEquals("품질", beforeCandidates[0].name)
        assertEquals("best quality", beforeCandidates[0].token)

        // 수정 시뮬레이션: ID로 찾아 단축어와 본문 변경
        val current = fakeRepo.load().toMutableList()
        val index = current.indexOfFirst { it.id == "snip-1" }
        assertTrue(index != -1)
        current[index] = current[index].copy(shortcut = "초고화질", content = "masterpiece, 8k resolution")
        fakeRepo.save(current)

        // 수정 후 확인
        val afterCandidates = useCase()
        assertEquals(1, afterCandidates.size)
        assertEquals("초고화질", afterCandidates[0].name)
        assertEquals("masterpiece, 8k resolution", afterCandidates[0].token)
    }
}

