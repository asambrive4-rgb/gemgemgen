// 역할: 기기에 등록된 접근성 서비스 중 우리 앱의 서비스 활성화 여부를 대조 판정합니다.
package com.example.gemgemgen.environment.android

object AccessibilityServiceMatcher {
    fun containsService(
        enabledServices: String?,
        expectedPackageName: String,
        expectedClassName: String
    ): Boolean {
        if (enabledServices.isNullOrBlank()) return false

        return enabledServices
            .split(':')
            .any { value ->
                val parts = value.split('/', limit = 2)
                if (parts.size != 2) return@any false

                val packageName = parts[0]
                val className = normalizeClassName(packageName, parts[1])

                packageName == expectedPackageName && className == expectedClassName
            }
    }

    private fun normalizeClassName(packageName: String, className: String): String {
        return if (className.startsWith(".")) {
            packageName + className
        } else {
            className
        }
    }
}

