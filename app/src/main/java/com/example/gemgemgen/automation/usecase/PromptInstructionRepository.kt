// 역할: 프롬프트 상단 및 하단 인스트럭션 설정 데이터의 영구 저장 및 조회를 위한 저장소 인터페이스를 정의합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.PromptInstructionConfig

interface PromptInstructionRepository {
    fun load(): PromptInstructionConfig
    fun save(config: PromptInstructionConfig)
}
