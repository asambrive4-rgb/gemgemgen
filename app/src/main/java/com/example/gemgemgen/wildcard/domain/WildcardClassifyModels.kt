package com.example.gemgemgen.wildcard.domain

data class WildcardClassifyGroup(
    val name: String,
    val items: List<String>
)

data class WildcardClassifyResult(
    val criteria: String,
    val sourceLines: List<String>,
    val groups: List<WildcardClassifyGroup>,
    /** 어느 그룹에도 안 들어가 저장하지 않는 줄 (미리보기 표시용) */
    val droppedLines: List<String> = emptyList()
) {
    val droppedLineCount: Int
        get() = droppedLines.size

    val savableGroups: List<WildcardClassifyGroup>
        get() = groups.filter { group ->
            group.items.isNotEmpty() &&
                group.name.trim() != WildcardClassifyFileName.UNCLASSIFIED_GROUP_NAME
        }
}

/** 미리보기에서 파일명을 수정한 뒤 저장에 쓰는 항목 */
data class WildcardClassifySaveEntry(
    val groupName: String,
    val items: List<String>,
    /** 사용자가 보는/고치는 파일명 입력 (확장자 없어도 됨) */
    val fileNameInput: String,
    val isEditingFileName: Boolean = false
)

object WildcardClassifyFileName {
    const val UNCLASSIFIED_GROUP_NAME = "미분류"

    /**
     * 그룹명 → 기본 파일명 입력값 (확장자 없는 stem 권장 UI용).
     */
    fun suggestedInputFromGroupName(name: String): String {
        val fileName = fromGroupName(name) ?: return name.trim().filterNot { it.isWhitespace() }
        return fileName.removeSuffix(".txt")
    }

    /**
     * 그룹명 또는 사용자 입력 → `이름.txt`.
     * 공백 제거, 경로 위험 문자 치환. 비면 null.
     */
    fun fromGroupName(name: String): String? = normalizeUserInput(name)

    fun normalizeUserInput(input: String): String? {
        val withoutExt = input.trim().removeSuffix(".txt").removeSuffix(".TXT")
        val cleaned = withoutExt
            .map { ch ->
                when {
                    ch.isWhitespace() -> ""
                    ch in "\\/:*?\"<>|" -> "_"
                    else -> ch.toString()
                }
            }
            .joinToString("")
            .take(80)
        if (cleaned.isBlank()) return null
        return WildcardFileName.normalize(cleaned)
    }

    fun buildSaveEntries(groups: List<WildcardClassifyGroup>): List<WildcardClassifySaveEntry> {
        val used = linkedSetOf<String>()
        return groups.mapNotNull { group ->
            if (group.items.isEmpty()) return@mapNotNull null
            if (group.name.trim() == UNCLASSIFIED_GROUP_NAME) return@mapNotNull null

            var stem = suggestedInputFromGroupName(group.name).ifBlank { "group" }
            var candidate = stem
            var suffix = 2
            while (!used.add(candidate.lowercase())) {
                candidate = "${stem}_$suffix"
                suffix++
            }
            WildcardClassifySaveEntry(
                groupName = group.name,
                items = group.items,
                fileNameInput = candidate
            )
        }
    }
}

/**
 * 모델 응답 그룹을 원본 줄 집합에 맞춘다.
 * - 원본에 없는 문장은 버림
 * - 한 줄은 첫 그룹에만 배정
 * - 미배정 줄은 저장하지 않음 (droppedLines 로 미리보기만)
 */
object WildcardClassifyResultPolicy {
    fun reconcile(
        sourceLines: List<String>,
        rawGroups: List<WildcardClassifyGroup>
    ): Pair<List<WildcardClassifyGroup>, List<String>> {
        if (sourceLines.isEmpty()) {
            return emptyList<WildcardClassifyGroup>() to emptyList()
        }

        val remaining = sourceLines
            .groupingBy { it }
            .eachCount()
            .toMutableMap()

        val reconciled = ArrayList<WildcardClassifyGroup>()
        for (group in rawGroups) {
            val name = group.name.trim()
            if (name.isEmpty()) continue
            if (name == WildcardClassifyFileName.UNCLASSIFIED_GROUP_NAME) continue

            val kept = ArrayList<String>()
            for (item in group.items) {
                val line = item.trim()
                if (line.isEmpty()) continue
                val count = remaining[line] ?: 0
                if (count <= 0) continue
                kept.add(line)
                remaining[line] = count - 1
            }
            if (kept.isNotEmpty()) {
                reconciled.add(WildcardClassifyGroup(name = name, items = kept))
            }
        }

        val dropped = ArrayList<String>()
        for (line in sourceLines) {
            val count = remaining[line] ?: 0
            if (count > 0) {
                repeat(count) { dropped.add(line) }
                remaining[line] = 0
            }
        }
        return reconciled to dropped
    }
}
