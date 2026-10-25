// 역할: 기본 추천 방향 칩과 사용자가 만든 칩을 합쳐 가나다순으로 정렬하고, 새 사용자 칩의 입력값을 검증합니다.
package com.example.gemgemgen.analysis.domain

object AnalysisDirectionCatalog {
    const val USER_ID_PREFIX = "user-"
    const val MAX_USER_DIRECTIONS = 20
    const val MAX_TITLE_LENGTH = 12
    const val MAX_INSTRUCTION_LENGTH = 100

    /** 기본 칩과 사용자 칩을 제목 가나다순으로 정렬해 돌려줍니다. */
    fun merged(userDirections: List<AnalysisDirection>): List<AnalysisDirection> {
        return (AnalysisDefaultDirections.values + userDirections).sortedBy { it.title }
    }

    fun isUserDirection(id: String): Boolean = id.startsWith(USER_ID_PREFIX)

    /**
     * 사용자 칩을 만듭니다. 이름이나 지시문이 비었거나, 기존 칩과 이름이 겹치거나,
     * 개수 한도를 넘으면 null을 돌려줍니다.
     */
    fun createUserDirection(
        title: String,
        instruction: String,
        existing: List<AnalysisDirection>,
        newId: String
    ): AnalysisDirection? {
        val cleanTitle = title.trim().take(MAX_TITLE_LENGTH)
        val cleanInstruction = instruction.trim().take(MAX_INSTRUCTION_LENGTH)
        if (cleanTitle.isEmpty() || cleanInstruction.isEmpty()) return null
        if (existing.count { isUserDirection(it.id) } >= MAX_USER_DIRECTIONS) return null
        if (merged(existing).any { it.title == cleanTitle }) return null
        return AnalysisDirection(
            id = USER_ID_PREFIX + newId,
            title = cleanTitle,
            hint = cleanInstruction
        )
    }
}
