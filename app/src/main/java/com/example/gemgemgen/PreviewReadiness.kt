package com.example.gemgemgen

data class PreviewReadiness(
    val canPreview: Boolean,
    val reason: String
) {
    companion object {
        fun check(
            promptTemplate: String,
            isWildcardDirectoryAccessible: Boolean
        ): PreviewReadiness {
            return when {
                promptTemplate.isBlank() -> PreviewReadiness(
                    canPreview = false,
                    reason = "프롬프트 템플릿을 입력해주세요."
                )

                !isWildcardDirectoryAccessible -> PreviewReadiness(
                    canPreview = false,
                    reason = "wildcard 폴더를 먼저 선택해주세요."
                )

                else -> PreviewReadiness(
                    canPreview = true,
                    reason = "미리보기를 생성할 수 있습니다."
                )
            }
        }
    }
}
