// 역할: 화면 크기별 반응형 플로팅 바 너비 및 기본 위치 계산과 위치 저장·복원 동작을 검증합니다.
package com.example.gemgemgen.automation.android

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FloatingBarPositionStoreTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var testScope: CoroutineScope
    private lateinit var testFile: File

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testFile = temporaryFolder.newFile("test_floating_bar.preferences_pb")
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    private class FakeFloatingBarMetricsProvider(
        private val widthPx: Int,
        private val heightPx: Int,
        private val screenDensity: Float
    ) : FloatingBarMetricsProvider {
        override fun screenWidthPx(): Int = widthPx
        override fun screenHeightPx(): Int = heightPx
        override fun density(): Float = screenDensity
    }

    @Test
    fun smallScreen_limitsBarWidthToScreenWidth_andCentersAtZero() {
        // 너비 360dp, density 3.0 (1080px), 높이 800dp (2400px)인 소형 스마트폰
        val density = 3.0f
        val screenWidthPx = (360 * density).roundToInt() // 1080px
        val screenHeightPx = (800 * density).roundToInt() // 2400px
        val metrics = FakeFloatingBarMetricsProvider(screenWidthPx, screenHeightPx, density)

        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        val store = FloatingBarPositionStore(dataStore, metrics)

        // 470dp * 3.0 = 1410px 이지만 화면 너비가 1080px이므로 1080px로 제한되어야 함
        assertEquals(1080, store.barWidthPx())

        val defaultPos = store.defaultPosition()
        // 화면 너비와 바 너비가 같으므로 (1080 - 1080) / 2 = 0
        assertEquals(0, defaultPos.x)

        val minVisibleHeightPx = (FloatingBarPositionStore.MIN_VISIBLE_HEIGHT_DP * density).roundToInt()
        val edgeMarginPx = (FloatingBarPositionStore.EDGE_MARGIN_DP * density).roundToInt()
        val expectedY = screenHeightPx - minVisibleHeightPx - edgeMarginPx
        assertEquals(expectedY, defaultPos.y)
    }

    @Test
    fun largeScreen_capsBarWidthAt470dp_andCentersCorrectly() {
        // 너비 800dp, density 2.0 (1600px), 높이 1200dp (2400px)인 대화면 태블릿
        val density = 2.0f
        val screenWidthPx = (800 * density).roundToInt() // 1600px
        val screenHeightPx = (1200 * density).roundToInt() // 2400px
        val metrics = FakeFloatingBarMetricsProvider(screenWidthPx, screenHeightPx, density)

        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        val store = FloatingBarPositionStore(dataStore, metrics)

        // 470dp * 2.0 = 940px (화면 너비 1600px보다 작으므로 940px 유지)
        assertEquals(940, store.barWidthPx())

        val defaultPos = store.defaultPosition()
        // (1600 - 940) / 2 = 330px로 가로 중앙 정렬
        assertEquals(330, defaultPos.x)
    }

    @Test
    fun saveAndLoadPosition_persistsCorrectly() = runBlocking {
        val density = 3.0f
        val metrics = FakeFloatingBarMetricsProvider(1080, 2400, density)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        val store = FloatingBarPositionStore(dataStore, metrics)

        assertNull(store.load())

        val positionToSave = FloatingBarPosition(x = 120, y = 450)
        store.save(positionToSave)

        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(120, loaded?.x)
        assertEquals(450, loaded?.y)
    }
}
