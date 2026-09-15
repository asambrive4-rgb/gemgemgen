// 역할: 변주 생성용 프롬프트 설정의 로드 및 영구 저장을 담당하는 포트 인터페이스입니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.VariationPromptConfig

interface VariationPromptRepository {
    fun load(): VariationPromptConfig
    fun save(config: VariationPromptConfig)
}
