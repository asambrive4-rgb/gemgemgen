// 역할: 프롬프트 실험(연금술) 기능의 실험 세트(프리셋), 기본 실험 프롬프트 목록, 위치 지정 태그(`{{실험}}`) 치환 및 검증 규칙을 정의하는 도메인 모델입니다.
package com.example.gemgemgen.automation.domain

import java.util.UUID

/**
 * 실험 세트 내 개별 실험 프롬프트 항목.
 */
data class PromptExperimentItem(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * 여러 실험 프롬프트 항목을 묶어서 보관하는 프리셋(세트) 모델.
 */
data class PromptExperimentPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val items: List<PromptExperimentItem> = emptyList(),
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * 실험(연금술) 모드의 도메인 규칙 및 본문 태그 합성 정책.
 */
object PromptExperimentPolicy {
    const val EXPERIMENT_TAG: String = "{{실험}}"
    const val DEFAULT_PRESET_ID: String = "default_experiment_preset"
    const val DEFAULT_PRESET_NAME: String = "기본 세트"

    val DEFAULT_EXPERIMENT_PROMPTS: List<String> = listOf(
        "여성은 아방가르드 양식의 극단적인 토폴로지 스케일링 왜곡을 통해 상반신 전경을 가득 채우는 압도적인 부피와 밀도를 지닌, 화면 밖으로 쏟아져 나올 듯 극대화된 볼륨감의 거대한 비현실적 아니메(Anime)같이 과장된 바스트 실루엣을 지니고 있다.",
        "여성은 자연스러운 인체 비율을 완전히 무시한 채 몸의 기본 실루엣보다 훨씬 더 거대하게 왜곡 팽창하여 묵직하게 가라앉아 있는 바스트를 지니고 있다.",
        "여성은 강력한 수직 중력의 힘을 받아 아래로 강하게 쏠리며 묵직하게 내려앉아 처진 바스트의 육중한 질량감이 극대화되어 표현되어 있다.",
        "여성은 의도적인 데포르메(Déformé) 왜곡을 통해 화면의 그 어떤 요소보다 가장 압도적으로 거대하게 시각적 중심과 물리적 면적을 차지하고 있는 바스트를 지니고 있다."
    )

    fun defaultPreset(): PromptExperimentPreset = PromptExperimentPreset(
        id = DEFAULT_PRESET_ID,
        name = DEFAULT_PRESET_NAME,
        items = DEFAULT_EXPERIMENT_PROMPTS.mapIndexed { index, prompt ->
            PromptExperimentItem(
                id = "default_item_${index + 1}",
                content = prompt,
                createdAtMillis = index.toLong()
            )
        },
        createdAtMillis = 0L
    )

    /**
     * 프롬프트 템플릿 본문에 실험 위치 지정 태그(`{{실험}}`)가 포함되어 있는지 확인합니다.
     */
    fun hasExperimentTag(promptTemplate: String): Boolean =
        promptTemplate.contains(EXPERIMENT_TAG)

    /**
     * 실험 모드에서 자동화 시작이 가능한지 검증하고, 불가 사유가 있으면 반환합니다.
     * 일반 모드(`isExperimentMode == false`)일 때는 항상 null(실행 가능)을 반환합니다.
     */
    fun validateExperimentReady(
        isExperimentMode: Boolean,
        promptTemplate: String,
        selectedItem: PromptExperimentItem?
    ): String? {
        if (!isExperimentMode) return null
        if (!hasExperimentTag(promptTemplate)) {
            return "본문에 $EXPERIMENT_TAG 위치 태그를 먼저 넣어주세요."
        }
        if (selectedItem == null || selectedItem.content.isBlank()) {
            return "실험할 프롬프트 번호를 먼저 선택해주세요."
        }
        return null
    }

    /**
     * 실험 모드일 때 프롬프트 템플릿 내의 `{{실험}}` 태그를 선택된 실험 문구로 치환합니다.
     * 에디터 원본 본문은 건드리지 않고 백그라운드 실행용 프롬프트를 합성할 때 사용합니다.
     */
    fun resolvePromptTemplate(
        isExperimentMode: Boolean,
        promptTemplate: String,
        selectedItem: PromptExperimentItem?
    ): String {
        if (!isExperimentMode || selectedItem == null) return promptTemplate
        return promptTemplate.replace(EXPERIMENT_TAG, selectedItem.content)
    }

    /**
     * 클립보드 텍스트를 줄바꿈 기준으로 분리했을 때 유효한 여러 줄 목록을 추출합니다.
     */
    fun extractNonBlankLines(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * 붙여넣기 텍스트가 2줄 이상의 유효한 문장을 포함하여 분할 확인 다이얼로그가 필요한지 판별합니다.
     */
    fun isMultilinePasteCandidate(text: String): Boolean =
        extractNonBlankLines(text).size >= 2

    /**
     * 리스트 내 특정 인덱스 요소를 위/아래(또는 앞/뒤)로 한 칸 이동합니다.
     */
    fun <T> moveElement(list: List<T>, fromIndex: Int, delta: Int): List<T> {
        val toIndex = fromIndex + delta
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
            return list
        }
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    /**
     * 리스트 내 특정 위치(fromIndex)의 요소를 목표 위치(toIndex)로 이동합니다.
     */
    fun <T> moveElementTo(list: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
            return list
        }
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }
}
