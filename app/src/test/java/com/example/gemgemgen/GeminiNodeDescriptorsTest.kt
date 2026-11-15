// 역할: Gemini, Google 앱, Android 설정의 접근성 노드 리소스 식별자 레지스트리의 무결성 및 기본 ID 구성을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.ForceStopNodeLabels
import com.example.gemgemgen.automation.android.GeminiAccountSwitchNodeLabels
import com.example.gemgemgen.automation.android.GeminiNodeDescriptors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiNodeDescriptorsTest {

    @Test
    fun inputDescriptors_containExpectedDefaultIds() {
        val inputIds = GeminiNodeDescriptors.Input.DEFAULT_INPUT_VIEW_IDS
        assertTrue(inputIds.contains("com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_text"))
        assertTrue(inputIds.contains("com.google.android.googlequicksearchbox:id/assistant_robin_input_collapsed_text_half_sheet"))
        assertEquals(2, inputIds.size)
    }

    @Test
    fun sendDescriptors_containExpectedDefaultIds() {
        val sendIds = GeminiNodeDescriptors.Send.DEFAULT_SEND_VIEW_IDS
        assertTrue(sendIds.contains("com.google.android.googlequicksearchbox:id/assistant_robin_input_send_button"))
        assertTrue(sendIds.contains("com.google.android.googlequicksearchbox:id/assistant_robin_chat_input_send_button"))
        assertEquals(2, sendIds.size)
    }

    @Test
    fun accountSwitchDescriptors_containExpectedDefaultIds() {
        val toolbarIds = GeminiNodeDescriptors.AccountSwitch.DEFAULT_TOOLBAR_IDS
        assertTrue(toolbarIds.contains("com.google.android.googlequicksearchbox:id/assistant_robin_chat_compose_toolbar"))
        assertTrue(toolbarIds.contains("com.google.android.googlequicksearchbox:id/assistant_robin_chat_header_container"))

        val drawerIds = GeminiNodeDescriptors.AccountSwitch.DEFAULT_SIDEBAR_DRAWER_CONTAINER_IDS
        assertTrue(drawerIds.contains("com.google.android.googlequicksearchbox:id/assistant_robin_side_nav_host_drawer_container"))

        val threadIds = GeminiNodeDescriptors.AccountSwitch.DEFAULT_SIDEBAR_THREAD_ITEM_IDS
        assertTrue(threadIds.contains("thread_item"))

        val profileIds = GeminiNodeDescriptors.AccountSwitch.DEFAULT_PROFILE_BUTTON_IDS
        assertTrue(profileIds.contains("com.google.android.googlequicksearchbox:id/og_apd_internal_image_view"))
        assertTrue(profileIds.contains("com.google.android.googlequicksearchbox:id/og_apd_ring_view"))
        assertTrue(profileIds.contains("com.google.android.googlequicksearchbox:id/account_particle_disc"))

        val bentoContainerIds = GeminiNodeDescriptors.AccountSwitch.DEFAULT_BENTO_MENU_CONTAINER_IDS
        assertTrue(bentoContainerIds.contains("com.google.android.googlequicksearchbox:id/og_bento_menu_content_container"))
        assertTrue(bentoContainerIds.contains("com.google.android.googlequicksearchbox:id/og_bento_container"))

        val headerContainerIds = GeminiNodeDescriptors.AccountSwitch.DEFAULT_ACCOUNT_HEADER_CONTAINER_IDS
        assertTrue(headerContainerIds.contains("com.google.android.googlequicksearchbox:id/og_bento_account_management_header_container"))
        assertTrue(headerContainerIds.contains("com.google.android.googlequicksearchbox:id/og_compact_header"))
        assertTrue(headerContainerIds.contains("com.google.android.googlequicksearchbox:id/og_bento_header_card_root"))

        val chevronIds = GeminiNodeDescriptors.AccountSwitch.DEFAULT_ACCOUNT_HEADER_CHEVRON_IDS
        assertTrue(chevronIds.contains("com.google.android.googlequicksearchbox:id/og_compact_header_chevron_background"))
        assertTrue(chevronIds.contains("com.google.android.googlequicksearchbox:id/og_collapsed_chevron"))

        val availableRowIds = GeminiNodeDescriptors.AccountSwitch.DEFAULT_AVAILABLE_ACCOUNT_ROW_IDS
        assertTrue(availableRowIds.contains("com.google.android.googlequicksearchbox:id/og_bento_available_account_root"))
    }

    @Test
    fun forceStopDescriptors_containExpectedDefaultIds() {
        val forceStopIds = GeminiNodeDescriptors.ForceStop.DEFAULT_FORCE_STOP_BUTTON_IDS
        assertTrue(forceStopIds.contains("com.android.settings:id/forcestop_button"))
        assertTrue(forceStopIds.contains("com.android.settings:id/button2_negative"))
        assertTrue(forceStopIds.contains("com.android.settings:id/force_stop_button"))
        assertTrue(forceStopIds.contains("com.android.settings:id/right_button"))

        val confirmIds = GeminiNodeDescriptors.ForceStop.DEFAULT_CONFIRM_DIALOG_BUTTON_IDS
        assertTrue(confirmIds.contains("android:id/button1"))
        assertTrue(confirmIds.contains("com.android.settings:id/button1"))

        assertEquals("android:id/button2", GeminiNodeDescriptors.ForceStop.ANDROID_BUTTON2_CANCEL)
    }

    @Test
    fun systemUiDescriptors_containsTaskCloseSuffix() {
        assertEquals(":id/task_close", GeminiNodeDescriptors.SystemUi.TASK_CLOSE_SUFFIX)
    }

    @Test
    fun nodeLabels_defaultValuesDelegateToGeminiNodeDescriptors() {
        val accountLabels = GeminiAccountSwitchNodeLabels()
        assertEquals(GeminiNodeDescriptors.AccountSwitch.DEFAULT_TOOLBAR_IDS, accountLabels.toolbarViewIds)
        assertEquals(GeminiNodeDescriptors.AccountSwitch.DEFAULT_PROFILE_BUTTON_IDS, accountLabels.profileButtonViewIds)
        assertEquals(GeminiNodeDescriptors.AccountSwitch.DEFAULT_BENTO_MENU_CONTAINER_IDS, accountLabels.bentoMenuContainerIds)
        assertEquals(GeminiNodeDescriptors.AccountSwitch.DEFAULT_ACCOUNT_HEADER_CONTAINER_IDS, accountLabels.accountHeaderContainerIds)

        val forceStopLabels = ForceStopNodeLabels()
        assertEquals(GeminiNodeDescriptors.ForceStop.DEFAULT_FORCE_STOP_BUTTON_IDS, forceStopLabels.forceStopButtonIds)
        assertEquals(GeminiNodeDescriptors.ForceStop.DEFAULT_CONFIRM_DIALOG_BUTTON_IDS, forceStopLabels.confirmDialogButtonIds)
    }
}
