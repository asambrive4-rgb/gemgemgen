// 역할: 프롬프트 인스트럭션 설정의 로드 및 저장 관리 유스케이스를 검증합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ManagePromptInstructionUseCaseTest {

    private class FakePromptInstructionRepository(
        var config: PromptInstructionConfig = PromptInstructionConfig.DEFAULT
    ) : PromptInstructionRepository {
        override fun load(): PromptInstructionConfig = config
        override fun save(config: PromptInstructionConfig) {
            this.config = config
        }
    }

    @Test
    fun load_returnsConfigFromRepository() {
        val expected = PromptInstructionConfig(
            topInstruction = "Top Header",
            bottomInstruction = "Bottom Footer"
        )
        val repository = FakePromptInstructionRepository(config = expected)
        val useCase = ManagePromptInstructionUseCase(repository)

        val actual = useCase.load()

        assertEquals(expected, actual)
    }

    @Test
    fun save_delegatesToRepository() {
        val repository = FakePromptInstructionRepository()
        val useCase = ManagePromptInstructionUseCase(repository)

        val newConfig = PromptInstructionConfig(
            topInstruction = "Updated Top",
            bottomInstruction = "Updated Bottom"
        )
        useCase.save(newConfig)

        assertEquals(newConfig, repository.config)
        assertEquals(newConfig, useCase.load())
    }

    @Test
    fun defaultRepository_loadsDefaultAndSavesSuccessfully() {
        val useCase = ManagePromptInstructionUseCase()

        assertEquals(PromptInstructionConfig.DEFAULT, useCase.load())

        val customConfig = PromptInstructionConfig(
            topInstruction = "Custom Top",
            bottomInstruction = "Custom Bottom"
        )
        useCase.save(customConfig)

        assertEquals(customConfig, useCase.load())
    }
}
