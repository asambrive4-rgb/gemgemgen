// 역할: Gemini, Google 앱, Android 시스템 설정의 접근성 View ID 및 리소스 식별자를 단일 레지스트리로 통합 관리합니다.
package com.example.gemgemgen.automation.android

internal object GeminiNodeDescriptors {

    /**
     * Gemini / Google 앱의 텍스트 입력창 관련 리소스 ID
     */
    object Input {
        const val CHAT_INPUT_TEXT = "com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_text"
        const val COLLAPSED_INPUT_HALF_SHEET = "com.google.android.googlequicksearchbox:id/assistant_robin_input_collapsed_text_half_sheet"

        val DEFAULT_INPUT_VIEW_IDS: List<String> = listOf(
            CHAT_INPUT_TEXT,
            COLLAPSED_INPUT_HALF_SHEET
        )
    }

    /**
     * Gemini 전송(Send) 버튼 관련 리소스 ID
     */
    object Send {
        const val INPUT_SEND_BUTTON = "com.google.android.googlequicksearchbox:id/assistant_robin_input_send_button"
        const val CHAT_INPUT_SEND_BUTTON = "com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_send_button"

        val DEFAULT_SEND_VIEW_IDS: List<String> = listOf(
            INPUT_SEND_BUTTON,
            CHAT_INPUT_SEND_BUTTON
        )
    }

    /**
     * Google 계정 전환 (OneGoogle Bento) 관련 리소스 ID
     */
    object AccountSwitch {
        const val TOOLBAR_COMPOSE = "com.google.android.googlequicksearchbox:id/assistant_robin_chat_compose_toolbar"
        const val TOOLBAR_HEADER_CONTAINER = "com.google.android.googlequicksearchbox:id/assistant_robin_chat_header_container"
        val DEFAULT_TOOLBAR_IDS: List<String> = listOf(
            TOOLBAR_COMPOSE,
            TOOLBAR_HEADER_CONTAINER
        )

        const val SIDEBAR_DRAWER_CONTAINER = "com.google.android.googlequicksearchbox:id/assistant_robin_side_nav_host_drawer_container"
        val DEFAULT_SIDEBAR_DRAWER_CONTAINER_IDS: List<String> = listOf(
            SIDEBAR_DRAWER_CONTAINER
        )

        const val SIDEBAR_THREAD_ITEM = "thread_item"
        const val SIDEBAR_THREAD_ITEM_QUALIFIED = "com.google.android.googlequicksearchbox:id/thread_item"
        val DEFAULT_SIDEBAR_THREAD_ITEM_IDS: List<String> = listOf(
            SIDEBAR_THREAD_ITEM,
            SIDEBAR_THREAD_ITEM_QUALIFIED
        )

        const val PROFILE_INTERNAL_IMAGE = "com.google.android.googlequicksearchbox:id/og_apd_internal_image_view"
        const val PROFILE_RING = "com.google.android.googlequicksearchbox:id/og_apd_ring_view"
        const val PROFILE_PARTICLE_DISC = "com.google.android.googlequicksearchbox:id/account_particle_disc"
        val DEFAULT_PROFILE_BUTTON_IDS: List<String> = listOf(
            PROFILE_INTERNAL_IMAGE,
            PROFILE_RING,
            PROFILE_PARTICLE_DISC
        )

        const val BENTO_MENU_CONTENT_CONTAINER = "com.google.android.googlequicksearchbox:id/og_bento_menu_content_container"
        const val BENTO_CONTAINER = "com.google.android.googlequicksearchbox:id/og_bento_container"
        val DEFAULT_BENTO_MENU_CONTAINER_IDS: List<String> = listOf(
            BENTO_MENU_CONTENT_CONTAINER,
            BENTO_CONTAINER
        )

        const val BENTO_CLOSE_BUTTON = "com.google.android.googlequicksearchbox:id/og_bento_toolbar_close_button"
        val DEFAULT_BENTO_CLOSE_BUTTON_IDS: List<String> = listOf(
            BENTO_CLOSE_BUTTON
        )

        const val ACCOUNT_MANAGEMENT_HEADER = "com.google.android.googlequicksearchbox:id/og_bento_account_management_header_container"
        const val COMPACT_HEADER = "com.google.android.googlequicksearchbox:id/og_compact_header"
        const val BENTO_HEADER_CARD_ROOT = "com.google.android.googlequicksearchbox:id/og_bento_header_card_root"
        val DEFAULT_ACCOUNT_HEADER_CONTAINER_IDS: List<String> = listOf(
            ACCOUNT_MANAGEMENT_HEADER,
            COMPACT_HEADER,
            BENTO_HEADER_CARD_ROOT
        )

        const val COMPACT_HEADER_CHEVRON_BG = "com.google.android.googlequicksearchbox:id/og_compact_header_chevron_background"
        const val COLLAPSED_CHEVRON = "com.google.android.googlequicksearchbox:id/og_collapsed_chevron"
        val DEFAULT_ACCOUNT_HEADER_CHEVRON_IDS: List<String> = listOf(
            COMPACT_HEADER_CHEVRON_BG,
            COLLAPSED_CHEVRON
        )

        const val COMPACT_HEADER_PRIMARY_TEXT = "com.google.android.googlequicksearchbox:id/og_compact_header_primary_text"
        val DEFAULT_COMPACT_HEADER_PRIMARY_TEXT_IDS: List<String> = listOf(
            COMPACT_HEADER_PRIMARY_TEXT
        )

        const val COMPACT_HEADER_SECONDARY_TEXT = "com.google.android.googlequicksearchbox:id/og_compact_header_secondary_text"
        val DEFAULT_COMPACT_HEADER_SECONDARY_TEXT_IDS: List<String> = listOf(
            COMPACT_HEADER_SECONDARY_TEXT
        )

        const val ACCOUNTS_LIST = "com.google.android.googlequicksearchbox:id/accounts"
        val DEFAULT_ACCOUNTS_LIST_CONTAINER_IDS: List<String> = listOf(
            ACCOUNTS_LIST
        )

        const val BENTO_SCROLL_CONTAINER = "com.google.android.googlequicksearchbox:id/og_bento_scroll_container"
        val DEFAULT_BENTO_SCROLL_CONTAINER_IDS: List<String> = listOf(
            BENTO_SCROLL_CONTAINER
        )

        const val AVAILABLE_ACCOUNT_ROW = "com.google.android.googlequicksearchbox:id/og_bento_available_account_root"
        val DEFAULT_AVAILABLE_ACCOUNT_ROW_IDS: List<String> = listOf(
            AVAILABLE_ACCOUNT_ROW
        )

        const val PRIMARY_ACCOUNT_INFO = "com.google.android.googlequicksearchbox:id/og_primary_account_information"
        val DEFAULT_ACCOUNT_PRIMARY_INFO_IDS: List<String> = listOf(
            PRIMARY_ACCOUNT_INFO
        )

        const val SECONDARY_ACCOUNT_INFO = "com.google.android.googlequicksearchbox:id/og_secondary_account_information"
        val DEFAULT_ACCOUNT_SECONDARY_INFO_IDS: List<String> = listOf(
            SECONDARY_ACCOUNT_INFO
        )
    }

    /**
     * Android 설정 앱 강제 중지(Force Stop) 관련 리소스 ID
     */
    object ForceStop {
        const val SETTINGS_FORCE_STOP = "com.android.settings:id/forcestop_button"
        const val SETTINGS_BUTTON2_NEGATIVE = "com.android.settings:id/button2_negative"
        const val SETTINGS_FORCE_STOP_ALT = "com.android.settings:id/force_stop_button"
        const val SETTINGS_RIGHT_BUTTON = "com.android.settings:id/right_button"
        val DEFAULT_FORCE_STOP_BUTTON_IDS: List<String> = listOf(
            SETTINGS_FORCE_STOP,
            SETTINGS_BUTTON2_NEGATIVE,
            SETTINGS_FORCE_STOP_ALT,
            SETTINGS_RIGHT_BUTTON
        )

        const val ANDROID_BUTTON1 = "android:id/button1"
        const val SETTINGS_BUTTON1 = "com.android.settings:id/button1"
        val DEFAULT_CONFIRM_DIALOG_BUTTON_IDS: List<String> = listOf(
            ANDROID_BUTTON1,
            SETTINGS_BUTTON1
        )

        const val ANDROID_BUTTON2_CANCEL = "android:id/button2"
    }

    /**
     * Android 시스템 UI (최근 앱 화면 등) 관련 리소스 ID
     */
    object SystemUi {
        const val TASK_CLOSE_SUFFIX = ":id/task_close"
    }
}
