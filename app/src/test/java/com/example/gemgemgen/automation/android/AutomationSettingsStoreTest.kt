// 역할: AutomationSettingsStore의 Preferences DataStore 기반 읽기/쓰기 및 기본값 복원 동작을 검증합니다.
package com.example.gemgemgen.automation.android

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.gemgemgen.automation.domain.PromptInstructionConfig
import com.example.gemgemgen.automation.domain.SystemInstructionPrompt
import com.example.gemgemgen.automation.domain.VariationPromptConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutomationSettingsStoreTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var testScope: CoroutineScope
    private lateinit var store: AutomationSettingsStore
    private lateinit var testFile: File

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testFile = temporaryFolder.newFile("test_automation_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        store = AutomationSettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun copyPromptOnStart_defaultsToTrue() = runBlocking {
        val enabled = store.copyPromptOnStart.first()
        assertTrue("기본값은 true여야 합니다.", enabled)
    }

    @Test
    fun setCopyPromptOnStart_updatesValue() = runBlocking {
        store.setCopyPromptOnStart(false)
        val enabled = store.copyPromptOnStart.first()
        assertFalse("설정된 false 값이 반환되어야 합니다.", enabled)

        store.setCopyPromptOnStart(true)
        val reEnabled = store.copyPromptOnStart.first()
        assertTrue("다시 true로 변경되어야 합니다.", reEnabled)
    }

    @Test
    fun promptInstructionConfig_defaultsToSystemPromptAndNullBottom() = runBlocking {
        val config = store.promptInstructionConfig.first()
        assertEquals(SystemInstructionPrompt.text, config.topInstruction)
        assertNull(config.bottomInstruction)
    }

    @Test
    fun setPromptInstructionConfig_savesAndRestores() = runBlocking {
        val customConfig = PromptInstructionConfig(
            topInstruction = "맞춤 상단 인스트럭션",
            bottomInstruction = "맞춤 하단 인스트럭션"
        )
        store.setPromptInstructionConfig(customConfig)

        val restored = store.promptInstructionConfig.first()
        assertEquals("맞춤 상단 인스트럭션", restored.topInstruction)
        assertEquals("맞춤 하단 인스트럭션", restored.bottomInstruction)

        // bottomInstruction을 null로 변경 시 정상 제거되는지 검증
        store.setPromptInstructionConfig(PromptInstructionConfig(topInstruction = "상단만", bottomInstruction = null))
        val restoredNullBottom = store.promptInstructionConfig.first()
        assertEquals("상단만", restoredNullBottom.topInstruction)
        assertNull(restoredNullBottom.bottomInstruction)
    }

    @Test
    fun variationPromptConfig_defaultsToStandardPrompt() = runBlocking {
        val config = store.variationPromptConfig.first()
        assertEquals(VariationPromptConfig.DEFAULT_VARIATION_PROMPT, config.prompt)
    }

    @Test
    fun setVariationPromptConfig_savesAndRestores() = runBlocking {
        val customPrompt = "사용자 정의 변주 프롬프트 지침"
        store.setVariationPromptConfig(VariationPromptConfig(prompt = customPrompt))

        val restored = store.variationPromptConfig.first()
        assertEquals(customPrompt, restored.prompt)
    }
}
