// 역할: 사용자가 만든 추천 방향 칩을 영구 저장소에서 불러오고 저장하는 저장소 인터페이스와 메모리 구현입니다.
package com.example.gemgemgen.analysis.usecase

import com.example.gemgemgen.analysis.domain.AnalysisDirection

interface AnalysisCustomDirectionRepository {
    fun load(): List<AnalysisDirection>
    fun save(directions: List<AnalysisDirection>)
}

class InMemoryAnalysisCustomDirectionRepository(
    private var stored: List<AnalysisDirection> = emptyList()
) : AnalysisCustomDirectionRepository {
    override fun load(): List<AnalysisDirection> = stored

    override fun save(directions: List<AnalysisDirection>) {
        stored = directions
    }
}
