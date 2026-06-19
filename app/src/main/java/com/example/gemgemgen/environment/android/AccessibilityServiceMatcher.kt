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

