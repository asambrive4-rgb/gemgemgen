package com.example.gemgemgen.core

object AppDefaults {
    const val GEMINI_PACKAGE_NAME = "com.google.android.apps.bard"
    const val GOOGLE_QUICK_SEARCH_BOX_PACKAGE_NAME = "com.google.android.googlequicksearchbox"
    const val CHATGPT_PACKAGE_NAME = "com.openai.chatgpt"
    /** 공식 Firefox 앱. Grok OAuth 로그인 URL 열 때 우선 사용. */
    const val FIREFOX_PACKAGE_NAME = "org.mozilla.firefox"
    const val WILDCARD_DIRECTORY = "Documents/wildcard"
    val WILDCARD_DIRECTORY_CANDIDATES: List<String> = listOf(
        WILDCARD_DIRECTORY,
        "Download/wildcard"
    ).distinct()
    const val NULL_KEYBOARD_IME_ID = "com.nilac.nullkeyboard/.NullKeyboardService"
    val NULL_KEYBOARD_IME_CANDIDATES: List<String> = listOf(
        "com.nilac.nullkeyboard/.NullKeyboardService",
        "com.wparam.nullkeyboard/.NullInputMethod",
        "com.sarvesh.nullkeyboard/.NullKeyboardService"
    )
    const val DEFAULT_REPEAT_COUNT = 10
}
