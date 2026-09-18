// 역할: 프롬프트 상단 및 하단 인스트럭션 설정의 로드 및 저장을 관리하는 유스케이스.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptInstructionConfig

class ManagePromptInstructionUseCase(
    private val repository: PromptInstructionRepository = object : PromptInstructionRepository {
        private var current = PromptInstructionConfig.DEFAULT
        override fun load(): PromptInstructionConfig = current
        override fun save(config: PromptInstructionConfig) { current = config }
    }
) {
    fun load(): PromptInstructionConfig = repository.load()

    fun save(config: PromptInstructionConfig) {
        repository.save(config)
    }
}
