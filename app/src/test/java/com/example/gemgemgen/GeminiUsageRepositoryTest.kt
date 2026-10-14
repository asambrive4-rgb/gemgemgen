// 역할: GeminiUsageRepository의 로컬 DataStore 캐시 즉시 표시(Warm Start) 및 백그라운드 스크래핑 상태 전이를 검증합니다.
package com.example.gemgemgen

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.usage.android.DataStoreGeminiUsageCacheDataSource
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.usecase.GeminiUsageCacheDataSource
import com.example.gemgemgen.usage.usecase.GeminiUsageRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeminiUsageRepositoryTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var testFile: File

    @Before
    fun setUp() {
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testFile = temporaryFolder.newFile("test_gemini_usage_cache.preferences_pb")
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun init_whenCacheExists_immediatelyEmitsCachedSuccessThenUpdatesInBackground() = runBlocking {
        val cachedSnapshot = GeminiUsageState.Success(
            fiveHourPercent = 30,
            fiveHourResetText = "3시간 10분 후 초기화",
            weeklyPercent = 55,
            weeklyResetText = "월요일 오전 9:00 초기화",
            updatedAt = 1_000L
        )
        val freshSnapshot = GeminiUsageState.Success(
            fiveHourPercent = 45,
            fiveHourResetText = "2시간 40분 후 초기화",
            weeklyPercent = 60,
            weeklyResetText = "월요일 오전 9:00 초기화",
            updatedAt = 2_000L
        )

        val remoteBarrier = CompletableDeferred<GeminiUsageState>()
        val fakeCache = InMemoryUsageCacheDataSource(initial = cachedSnapshot)
        val repoScope = CoroutineScope(Dispatchers.Unconfined)

        val repository = GeminiUsageRepository(
            remoteDataSource = { remoteBarrier.await() },
            cacheDataSource = fakeCache,
            scope = repoScope,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = true
        )

        // 원격 응답이 오기 전에도 즉시 캐시된 마지막 성공 값을 먼저 표시해야 함
        assertEquals(cachedSnapshot, repository.state.value)
        assertEquals(cachedSnapshot, repository.cachedSuccess.value)
        assertTrue(repository.isRefreshing.value)

        // 백그라운드 원격 스크래핑 완료 시 최신 값으로 원자적 갱신 및 캐시 저장
        remoteBarrier.complete(freshSnapshot)

        assertEquals(freshSnapshot, repository.state.value)
        assertEquals(freshSnapshot, repository.cachedSuccess.value)
        assertEquals(freshSnapshot, fakeCache.saved)
        assertFalse(repository.isRefreshing.value)
    }

    @Test
    fun refresh_whenSessionExpired_transitionsToAuthRequiredAndKeepsCachedSnapshot() = runBlocking {
        val cachedSnapshot = GeminiUsageState.Success(
            fiveHourPercent = 72,
            fiveHourResetText = "1시간 20분 후 초기화",
            weeklyPercent = 88,
            weeklyResetText = "월요일 오전 9:00 초기화",
            updatedAt = 5_000L
        )
        val fakeCache = InMemoryUsageCacheDataSource(initial = cachedSnapshot)
        val repoScope = CoroutineScope(Dispatchers.Unconfined)

        val repository = GeminiUsageRepository(
            remoteDataSource = { GeminiUsageState.AuthRequired },
            cacheDataSource = fakeCache,
            scope = repoScope,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = false
        )

        assertEquals(cachedSnapshot, repository.state.value)

        val refreshed = repository.refresh()
        assertEquals(GeminiUsageState.AuthRequired, refreshed)
        assertEquals(GeminiUsageState.AuthRequired, repository.state.value)
        assertEquals(cachedSnapshot, repository.cachedSuccess.value)
    }

    @Test
    fun dataStoreCache_savesAndRestoresLastUsageSuccess() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testFile }
        )
        val cacheStore = DataStoreGeminiUsageCacheDataSource(dataStore)

        val expected = GeminiUsageState.Success(
            fiveHourPercent = 91,
            fiveHourResetText = "45분 후 초기화",
            weeklyPercent = 84,
            weeklyResetText = "10월 19일 오전 9:00 초기화",
            updatedAt = 1_760_500_000_000L
        )

        cacheStore.saveLastSuccess(expected)
        val restored = cacheStore.loadLastSuccess()

        assertEquals(expected, restored)
    }

    private class InMemoryUsageCacheDataSource(
        initial: GeminiUsageState.Success? = null
    ) : GeminiUsageCacheDataSource {
        var saved: GeminiUsageState.Success? = initial

        override suspend fun loadLastSuccess(): GeminiUsageState.Success? = saved

        override suspend fun saveLastSuccess(state: GeminiUsageState.Success) {
            saved = state
        }
    }
}
