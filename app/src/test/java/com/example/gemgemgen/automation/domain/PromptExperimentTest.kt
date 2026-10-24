// 역할: 프롬프트 실험(연금술) 도메인 정책(기본 프리셋 프롬프트 항목, 위치 태그 검증, 백그라운드 문구 치환, 다중 줄 붙여넣기 판별), 저장소 및 실행 인가 정책을 검증합니다.
package com.example.gemgemgen.automation.domain

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.gemgemgen.automation.android.PromptExperimentStore
import com.example.gemgemgen.environment.domain.EnvironmentStatus
import com.example.gemgemgen.remote.domain.AutomationMode
import com.example.gemgemgen.remote.domain.RemoteAutomationStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PromptExperimentTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var testScope: CoroutineScope
    private lateinit var store: PromptExperimentStore
    private lateinit var testFile: File

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testFile = temporaryFolder.newFile("test_prompt_experiments.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        store = PromptExperimentStore(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun validateExperimentReady_blocksWhenTagMissingOrItemNotSelected() {
        val item = PromptExperimentItem(id = "item-1", content = "야구장")

        // 실험 모드 OFF일 때는 태그나 선택 항목이 없어도 통과
        assertNull(
            PromptExperimentPolicy.validateExperimentReady(
                isExperimentMode = false,
                promptTemplate = "스마트폰 스냅샷",
                selectedItem = null
            )
        )

        // 실험 모드 ON + 본문에 {{실험}} 태그 없음 -> 차단
        assertNotNull(
            PromptExperimentPolicy.validateExperimentReady(
                isExperimentMode = true,
                promptTemplate = "스마트폰 스냅샷",
                selectedItem = item
            )
        )

        // 실험 모드 ON + {{실험}} 태그 있음 + 번호 미선택 -> 차단
        assertNotNull(
            PromptExperimentPolicy.validateExperimentReady(
                isExperimentMode = true,
                promptTemplate = "스마트폰 스냅샷\n{{실험}}\n브이포즈",
                selectedItem = null
            )
        )

        // 실험 모드 ON + {{실험}} 태그 있음 + 번호 선택됨 -> 통과
        assertNull(
            PromptExperimentPolicy.validateExperimentReady(
                isExperimentMode = true,
                promptTemplate = "스마트폰 스냅샷\n{{실험}}\n브이포즈",
                selectedItem = item
            )
        )
    }

    @Test
    fun resolvePromptTemplate_replacesTagWithoutMutatingOriginalWhenExperimentModeOn() {
        val template = "스마트폰 스냅샷\n\n{{실험}}\n\n여성이 브이포즈를 하며 앉아 있다.\n3:4 비율"
        val item1 = PromptExperimentItem(id = "1", content = "야구장")
        val item2 = PromptExperimentItem(id = "2", content = "축구장")

        val resolved1 = PromptExperimentPolicy.resolvePromptTemplate(
            isExperimentMode = true,
            promptTemplate = template,
            selectedItem = item1
        )
        assertEquals("스마트폰 스냅샷\n\n야구장\n\n여성이 브이포즈를 하며 앉아 있다.\n3:4 비율", resolved1)

        val resolved2 = PromptExperimentPolicy.resolvePromptTemplate(
            isExperimentMode = true,
            promptTemplate = template,
            selectedItem = item2
        )
        assertEquals("스마트폰 스냅샷\n\n축구장\n\n여성이 브이포즈를 하며 앉아 있다.\n3:4 비율", resolved2)

        // 실험 모드 OFF 시 원본 그대로 반환
        val resolvedOff = PromptExperimentPolicy.resolvePromptTemplate(
            isExperimentMode = false,
            promptTemplate = template,
            selectedItem = item1
        )
        assertEquals(template, resolvedOff)
    }

    @Test
    fun isMultilinePasteCandidate_detectsSingleVsMultiLineCorrectly() {
        assertFalse(PromptExperimentPolicy.isMultilinePasteCandidate("야구장"))
        assertFalse(PromptExperimentPolicy.isMultilinePasteCandidate("  야구장  \n  "))

        val multiText = "야구장\n축구장\n테니스장"
        assertTrue(PromptExperimentPolicy.isMultilinePasteCandidate(multiText))
        assertEquals(listOf("야구장", "축구장", "테니스장"), PromptExperimentPolicy.extractNonBlankLines(multiText))
    }

    @Test
    fun canRun_inExperimentMode_requiresTagAndSelectedItem() {
        val readyEnv = EnvironmentStatus(
            isGeminiInstalled = true,
            isAccessibilityServiceEnabled = true,
            hasWriteSecureSettingsPermission = true,
            isWildcardDirectoryAccessible = true
        )
        val item = PromptExperimentItem(id = "1", content = "야구장")

        // 실험 모드 ON인데 번호가 선택되지 않으면 canRun == false
        assertFalse(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnv,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "스냅샷 {{실험}}",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus(),
                isExperimentMode = true,
                selectedExperimentItem = null
            )
        )

        // 실험 모드 ON이고 태그 및 번호가 모두 선택되면 canRun == true
        assertTrue(
            AutomationExecutionPolicy.canRun(
                mode = AutomationMode.NORMAL,
                environmentStatus = readyEnv,
                targetApp = AutomationTargetApp.GEMINI,
                promptTemplate = "스냅샷 {{실험}}",
                isRunning = false,
                remoteAutomationStatus = RemoteAutomationStatus(),
                isExperimentMode = true,
                selectedExperimentItem = item
            )
        )
    }

    @Test
    fun store_savesAndRestoresMultiplePresetsAndActivePresetId() {
        val initial = store.loadPresets()
        assertEquals(1, initial.size)
        assertEquals(PromptExperimentPolicy.DEFAULT_PRESET_ID, initial.first().id)
        assertEquals(4, initial.first().items.size)
        assertEquals(PromptExperimentPolicy.DEFAULT_EXPERIMENT_PROMPTS, initial.first().items.map { it.content })

        val presets = listOf(
            PromptExperimentPreset(
                id = "preset-place",
                name = "장소",
                items = listOf(
                    PromptExperimentItem(id = "p1", content = "야구장"),
                    PromptExperimentItem(id = "p2", content = "축구장"),
                    PromptExperimentItem(id = "p3", content = "테니스장")
                )
            ),
            PromptExperimentPreset(
                id = "preset-light",
                name = "조명",
                items = listOf(
                    PromptExperimentItem(id = "l1", content = "자연광"),
                    PromptExperimentItem(id = "l2", content = "네온사인")
                )
            )
        )

        store.savePresets(presets)
        store.saveActivePresetId("preset-light")

        val loaded = store.loadPresets()
        assertEquals(2, loaded.size)
        assertEquals("장소", loaded[0].name)
        assertEquals(listOf("야구장", "축구장", "테니스장"), loaded[0].items.map { it.content })
        assertEquals("조명", loaded[1].name)
        assertEquals("preset-light", store.loadActivePresetId())
    }

    @Test
    fun moveElement_reordersItemsAndPresetsSafely() {
        val list = listOf("야구장", "축구장", "테니스장", "농구장")

        // 2번째(index 1: 축구장)를 위로(-1) 이동
        val movedUp = PromptExperimentPolicy.moveElement(list, 1, -1)
        assertEquals(listOf("축구장", "야구장", "테니스장", "농구장"), movedUp)

        // 첫 번째(index 0)를 위로(-1) 이동 시도 시 변화 없음
        assertEquals(list, PromptExperimentPolicy.moveElement(list, 0, -1))

        // 마지막(index 3)을 아래로(+1) 이동 시도 시 변화 없음
        assertEquals(list, PromptExperimentPolicy.moveElement(list, 3, 1))
    }

    @Test
    fun moveElementTo_reordersItemsToArbitraryIndicesSafely() {
        val list = listOf("A", "B", "C", "D")

        // 0번(A)을 2번 위치로 드래그 이동 -> [B, C, A, D]
        assertEquals(listOf("B", "C", "A", "D"), PromptExperimentPolicy.moveElementTo(list, 0, 2))

        // 3번(D)을 0번 위치로 드래그 이동 -> [D, A, B, C]
        assertEquals(listOf("D", "A", "B", "C"), PromptExperimentPolicy.moveElementTo(list, 3, 0))

        // 같은 위치로 이동 시 원본 유지
        assertEquals(list, PromptExperimentPolicy.moveElementTo(list, 1, 1))

        // 유효 범위를 벗어난 인덱스 시 원본 유지
        assertEquals(list, PromptExperimentPolicy.moveElementTo(list, -1, 2))
        assertEquals(list, PromptExperimentPolicy.moveElementTo(list, 0, 5))
    }
}
