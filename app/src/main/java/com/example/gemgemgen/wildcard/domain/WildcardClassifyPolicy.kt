// 역할: AI 와일드카드 분류 요청, 실행, 결과 저장의 비즈니스 인가 규칙과 도메인 불변식을 정의합니다.
package com.example.gemgemgen.wildcard.domain

/**
 * 와일드카드 AI 단어 분류 기능의 실행, 저장 및 화면 진입 인가를 결정하는 순수 도메인 정책 객체입니다.
 */
object WildcardClassifyPolicy {

    /**
     * AI 분류 요청(다이얼로그 진입)이 가능한지 검증합니다.
     * 파일이 선택되어 있고, 분류할 텍스트 줄이 존재하며, 파일 수정 권한이 있고,
     * 파일 작업이나 줄 선택 모드 및 이전 분류 작업이 진행 중이지 않아야 합니다.
     */
    fun canRequestClassify(
        canModifyFiles: Boolean,
        hasSelectedFile: Boolean,
        hasSelectableLines: Boolean,
        isFileOperationInProgress: Boolean,
        isLineSelectionMode: Boolean,
        isClassifyBusy: Boolean
    ): Boolean {
        return canModifyFiles &&
            hasSelectedFile &&
            hasSelectableLines &&
            !isFileOperationInProgress &&
            !isLineSelectionMode &&
            !isClassifyBusy
    }

    /**
     * AI 분류 작업을 실제로 실행(또는 재실행)할 수 있는지 검증합니다.
     * 분류 기준이 공백이 아니어야 하고, 현재 분류나 파일 작업이 진행 중이지 않아야 하며,
     * 기준 입력 창이 열려있거나 기존 미리보기가 존재해야 합니다.
     */
    fun canRunClassify(
        criteria: String,
        isClassifying: Boolean,
        isFileOperationInProgress: Boolean,
        showCriteriaDialog: Boolean,
        hasPreview: Boolean
    ): Boolean {
        return criteria.isNotBlank() &&
            !isClassifying &&
            !isFileOperationInProgress &&
            (showCriteriaDialog || hasPreview)
    }

    /**
     * AI 분류 결과 파일들을 저장할 수 있는지 검증합니다.
     * 미리보기가 존재하고, 저장할 항목이 1개 이상이며, 모든 파일명이 유효해야 하고,
     * 저장 권한이 있으며, 파일 작업/분류 중이 아니고, 덮어쓰기 충돌이 없어야 합니다.
     */
    fun canSaveClassifyResult(
        hasPreview: Boolean,
        saveEntries: List<WildcardClassifySaveEntry>,
        canModifyFiles: Boolean,
        isClassifying: Boolean,
        isFileOperationInProgress: Boolean,
        hasOverwriteConflicts: Boolean
    ): Boolean {
        return hasPreview &&
            saveEntries.isNotEmpty() &&
            saveEntries.all {
                WildcardClassifyFileName.normalizeUserInput(it.fileNameInput) != null
            } &&
            canModifyFiles &&
            !isClassifying &&
            !isFileOperationInProgress &&
            !hasOverwriteConflicts
    }
}
