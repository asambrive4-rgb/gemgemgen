package com.example.gemgemgen.analysis.domain

/** 모델이 고른 문구를 실제 원문의 위치로 변환한다. 원문 자체는 정규화하지 않는다. */
object AnalysisSourceLocator {
    private val whitespace = Regex("[\\s\\p{Z}]+")

    fun resolve(source: String, requested: AnalysisSourceRange): AnalysisSourceRange {
        val text = requested.exactText
        // 삽입은 코드가 아는 위치에서만 허용한다. 모델에는 기존 문구를 포함한 편집을 요청한다.
        if (text.isEmpty()) {
            require(requested.startIndex in 0..source.length && requested.endIndex == requested.startIndex) {
                "추가할 위치를 찾지 못했습니다. 앞뒤 원문 문구를 포함해 다시 생성해 주세요."
            }
            return requested
        }
        var matches = exactMatches(source, text)
        if (matches.isEmpty()) {
            val words = whitespace.split(text).filter { it.isNotEmpty() }
            if (words.isNotEmpty()) {
                val pattern = words.joinToString("[\\s\\p{Z}]+") { Regex.escape(it) }
                matches = Regex(pattern, RegexOption.IGNORE_CASE).findAll(source).map {
                    AnalysisSourceRange(it.range.first, it.range.last + 1, it.value)
                }.toList()
            }
        }
        require(matches.isNotEmpty()) { "수정할 문구를 원문에서 찾지 못했습니다. 원문 표현을 유지해 다시 생성해 주세요." }
        val occurrence = requested.occurrence
        val found = if (occurrence != null && matches.size > 1) {
            matches.getOrNull(occurrence - 1)
        } else {
            matches.singleOrNull() ?: matches.singleOrNull { it.startIndex == requested.startIndex }
        }
        require(found != null) { "같은 문구가 원문에 여러 번 있습니다. 앞뒤 문맥을 포함해 수정 대상을 구분해 주세요." }
        return found
    }

    private fun exactMatches(source: String, text: String): List<AnalysisSourceRange> {
        val matches = mutableListOf<AnalysisSourceRange>()
        var start = source.indexOf(text)
        while (start >= 0) {
            matches += AnalysisSourceRange(start, start + text.length, text)
            start = source.indexOf(text, start + 1)
        }
        return matches
    }
}
