// 역할: 5개 이상의 다중 제미나이 계정 추가·삭제(CRUD), DataStore JSON 직렬화/역직렬화, Warm Start 즉시 캐시 방출, 동시 병렬 새로고침 및 독립 쿠키 세션 격리를 검증합니다.
package com.example.gemgemgen

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.usage.android.DataStoreMultiGeminiUsageDataSource
import com.example.gemgemgen.usage.android.GeminiAccountCookieVault
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.domain.GeminiUsageWarningLevel
import com.example.gemgemgen.usage.usecase.GeminiUsageFetchResult
import com.example.gemgemgen.usage.usecase.MultiGeminiUsageLocalDataSource
import com.example.gemgemgen.usage.usecase.MultiGeminiUsageRepository
import com.example.gemgemgen.usage.usecase.MultiProfileGeminiUsageRemoteDataSource
import com.example.gemgemgen.usage.usecase.PersistedMultiGeminiUsageSnapshot
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MultiGeminiUsageRepositoryTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var testFile: File

    @Before
    fun setUp() {
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testFile = temporaryFolder.newFile("test_multi_gemini_usage.preferences_pb")
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun addAndRemoveFivePlusAccounts_maintainsSortOrderAndClearsRemovedSession() = runBlocking {
        val clearedProfiles = mutableListOf<String>()
        val fakeRemote = object : MultiProfileGeminiUsageRemoteDataSource {
            override suspend fun fetchUsage(profile: GeminiAccountProfile): GeminiUsageFetchResult {
                val basePct = (profile.sortOrder + 1) * 15
                return GeminiUsageFetchResult(
                    usageState = GeminiUsageState.Success(
                        fiveHourPercent = basePct.coerceAtMost(95),
                        fiveHourResetText = "2시간 15분 후",
                        weeklyPercent = (basePct + 10).coerceAtMost(99),
                        weeklyResetText = "10/19 오전 9시",
                        updatedAt = 1_000L + profile.sortOrder
                    )
                )
            }

            override suspend fun clearAccountSession(profile: GeminiAccountProfile) {
                clearedProfiles.add(profile.webViewProfileName)
            }
        }
        val fakeLocal = InMemoryMultiUsageLocalDataSource()
        val repoScope = CoroutineScope(Dispatchers.Unconfined)

        val repository = MultiGeminiUsageRepository(
            remoteDataSource = fakeRemote,
            localDataSource = fakeLocal,
            scope = repoScope,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = false
        )

        // 5개 이상의 계정 등록 검증 (총 6개 계정 추가)
        val aliases = listOf("개인 본계정", "작업용 1", "작업용 2", "작업용 3", "서브 계정 4", "서브 계정 5")
        aliases.forEachIndexed { index, alias ->
            repository.addAccount(
                alias = alias,
                email = "user${index + 1}@gmail.com",
                accountId = "acc_${index + 1}",
                autoRefresh = true
            )
        }

        val stateAfterAdd = repository.state.value
        assertEquals(6, stateAfterAdd.accountCount)
        assertEquals("acc_1", stateAfterAdd.selectedAccountId)
        assertEquals("개인 본계정 ▾", stateAfterAdd.summaryAccountLabel)
        assertEquals("15%", stateAfterAdd.summaryBadgeText)
        assertEquals("2시간 15분 후", stateAfterAdd.summaryResetTimeText)
        assertEquals("개인 본계정 15% ▾", stateAfterAdd.summaryButtonText)

        // 각 계정이 서로 다른 독립 WebView 프로필명을 가지는지 검증
        val distinctProfileNames = stateAfterAdd.accounts.map { it.profile.webViewProfileName }.toSet()
        assertEquals(6, distinctProfileNames.size)

        // 대표 계정을 5번째 계정(75% - 주의 단계)으로 변경
        repository.selectRepresentativeAccount("acc_5")
        assertEquals("서브 계정 4 ▾", repository.state.value.summaryAccountLabel)
        assertEquals("75%", repository.state.value.summaryBadgeText)
        assertEquals("서브 계정 4 75% ▾", repository.state.value.summaryButtonText)
        assertEquals(GeminiUsageWarningLevel.CAUTION, repository.state.value.summaryWarningLevel)

        // 5번째 계정 삭제 시 대표 계정이 첫 번째 계정으로 안전하게 폴백되고, 정렬 순서가 재정규화되는지 검증
        assertTrue(repository.removeAccount("acc_5"))
        val stateAfterRemove = repository.state.value
        assertEquals(5, stateAfterRemove.accountCount)
        assertEquals("acc_1", stateAfterRemove.selectedAccountId)
        assertEquals(listOf(0, 1, 2, 3, 4), stateAfterRemove.accounts.map { it.profile.sortOrder })
        assertTrue(clearedProfiles.contains("gemini_profile_acc_5"))
    }

    @Test
    fun warmStart_withFiveCachedAccounts_immediatelyEmitsSnapshotsBeforeNetworkSync() = runBlocking {
        val initialProfiles = (1..5).map { idx ->
            GeminiAccountProfile(
                id = "acc_$idx",
                alias = if (idx == 1) "본계정" else "작업용 ${idx - 1}",
                email = "account$idx@gmail.com",
                sortOrder = idx - 1,
                createdAt = 1000L * idx
            )
        }
        val initialCachedMap = initialProfiles.associate { profile ->
            profile.id to GeminiUsageState.Success(
                fiveHourPercent = 42 + profile.sortOrder * 10,
                fiveHourResetText = "2시간 15분 후",
                weeklyPercent = 50 + profile.sortOrder * 10,
                weeklyResetText = "10/19 오전 9시",
                updatedAt = 10_000L
            )
        }

        val networkBarrier = CompletableDeferred<Unit>()
        val fakeLocal = InMemoryMultiUsageLocalDataSource(
            PersistedMultiGeminiUsageSnapshot(
                profiles = initialProfiles,
                cachedUsageByAccountId = initialCachedMap,
                selectedAccountId = "acc_1"
            )
        )
        val repoScope = CoroutineScope(Dispatchers.Unconfined)

        val repository = MultiGeminiUsageRepository(
            remoteDataSource = { profile ->
                networkBarrier.await()
                GeminiUsageFetchResult(
                    usageState = GeminiUsageState.Success(
                        fiveHourPercent = 50 + profile.sortOrder * 10,
                        fiveHourResetText = "1시간 50분 후",
                        weeklyPercent = 60 + profile.sortOrder * 10,
                        weeklyResetText = "10/19 오전 9시",
                        updatedAt = 20_000L
                    )
                )
            },
            localDataSource = fakeLocal,
            scope = repoScope,
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = true
        )

        // 네트워크 응답 전에도 Warm Start로 5개 계정의 로컬 캐시가 즉시 표시되어야 함
        val warmState = repository.state.value
        assertTrue(warmState.isInitialized)
        assertEquals(5, warmState.accountCount)
        assertEquals("본계정 42% ▾", warmState.summaryButtonText)
        assertEquals(42, warmState.accounts[0].displayedSuccess?.fiveHourPercent)
        assertEquals(82, warmState.accounts[4].displayedSuccess?.fiveHourPercent)
        assertTrue(warmState.isRefreshingAll)
        assertTrue(warmState.accounts.all { it.isRefreshing })

        // 백그라운드 동기화 완료 후 최신 수치로 갱신됨
        networkBarrier.complete(Unit)

        val syncedState = repository.state.value
        assertFalse(syncedState.isRefreshingAll)
        assertEquals("본계정 50% ▾", syncedState.summaryButtonText)
        assertEquals(50, syncedState.accounts[0].displayedSuccess?.fiveHourPercent)
        assertEquals(90, syncedState.accounts[4].displayedSuccess?.fiveHourPercent)
        assertEquals(GeminiUsageWarningLevel.WARNING, syncedState.accounts[4].overallWarningLevel)
    }

    @Test
    fun dataStoreMultiUsageDataSource_serializesAndRestoresFiveAccountsAndSnapshots() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testFile }
        )
        val dataSource = DataStoreMultiGeminiUsageDataSource(dataStore)

        val profiles = (1..5).map { idx ->
            GeminiAccountProfile(
                id = "acc_$idx",
                alias = "계정별칭_$idx",
                email = "user$idx@gmail.com",
                sortOrder = idx - 1,
                createdAt = 1_760_000_000_000L + idx
            )
        }
        dataSource.saveProfiles(profiles = profiles, selectedAccountId = "acc_3")

        profiles.forEachIndexed { index, profile ->
            dataSource.saveAccountSuccess(
                accountId = profile.id,
                state = GeminiUsageState.Success(
                    fiveHourPercent = 20 + index * 18,
                    fiveHourResetText = "${index + 1}시간 15분 후",
                    weeklyPercent = 30 + index * 15,
                    weeklyResetText = "10/19 오전 9시",
                    updatedAt = 1_760_500_000_000L + index
                )
            )
        }

        val restored = dataSource.loadSnapshot()
        assertEquals(5, restored.profiles.size)
        assertEquals("acc_3", restored.selectedAccountId)
        assertEquals(profiles, restored.profiles)
        assertEquals(5, restored.cachedUsageByAccountId.size)
        assertEquals(92, restored.cachedUsageByAccountId["acc_5"]?.fiveHourPercent)
        assertEquals("5시간 15분 후", restored.cachedUsageByAccountId["acc_5"]?.fiveHourResetText)

        // 특정 계정 삭제 후 DataStore 상태 검증
        dataSource.removeAccountData("acc_3")
        val afterRemove = dataSource.loadSnapshot()
        assertEquals(4, afterRemove.profiles.size)
        assertNull(afterRemove.cachedUsageByAccountId["acc_3"])
        assertEquals("acc_1", afterRemove.selectedAccountId)
    }

    @Test
    fun cookieVault_isolatesCookiesAcrossFiveAccountsWithoutOverwriting() {
        val vault = GeminiAccountCookieVault(sharedPreferences = null)

        for (i in 1..5) {
            val profileName = GeminiAccountProfile.buildWebViewProfileName("acc_$i")
            vault.saveCookiesForProfile(
                profileName = profileName,
                cookiesByOrigin = mapOf(
                    "https://gemini.google.com" to listOf("SID=sid_token_$i", "__Secure-1PSID=sec_$i"),
                    "https://accounts.google.com" to listOf("LSID=lsid_token_$i")
                )
            )
        }

        for (i in 1..5) {
            val profileName = GeminiAccountProfile.buildWebViewProfileName("acc_$i")
            val restored = vault.loadCookiesForProfile(profileName)
            assertEquals(
                listOf("SID=sid_token_$i", "__Secure-1PSID=sec_$i"),
                restored["https://gemini.google.com"]
            )
            assertEquals(
                listOf("LSID=lsid_token_$i"),
                restored["https://accounts.google.com"]
            )
        }

        // 3번 계정 세션 삭제 시 다른 계정(1, 2, 4, 5번)의 쿠키는 그대로 보존되어야 함
        vault.clearProfile(GeminiAccountProfile.buildWebViewProfileName("acc_3"))
        assertTrue(vault.loadCookiesForProfile(GeminiAccountProfile.buildWebViewProfileName("acc_3")).isEmpty())
        assertNotNull(vault.loadCookiesForProfile(GeminiAccountProfile.buildWebViewProfileName("acc_1"))["https://gemini.google.com"])
        assertNotNull(vault.loadCookiesForProfile(GeminiAccountProfile.buildWebViewProfileName("acc_5"))["https://gemini.google.com"])
    }

    @Test
    fun cookieVault_deduplicatesPollutedCookiesAndSeparatesDomainFromHostOnlyCookies() {
        val pollutedHeader = "SID=sid_old; SIDCC=sidcc_stale; LSID=lsid_host; SID=sid_latest; SIDCC=sidcc_fresh"
        assertTrue(GeminiAccountCookieVault.hasDuplicateCookieNames(pollutedHeader))

        val split = GeminiAccountCookieVault.splitCookieHeader(pollutedHeader)
        assertEquals(listOf("LSID=lsid_host", "SID=sid_latest", "SIDCC=sidcc_fresh"), split)
        assertFalse(GeminiAccountCookieVault.hasDuplicateCookieNames(split.joinToString("; ")))

        val commands = GeminiAccountCookieVault.buildSetCookieCommands(
            mapOf(
                "https://gemini.google.com" to listOf(
                    "SID=sid_latest",
                    "SID=sid_latest",
                    "COMPASS=compass_gemini",
                    "LSID=lsid_leaked",
                    "SIDCC=sidcc_fresh"
                ),
                "https://accounts.google.com" to listOf(
                    "SID=sid_latest",
                    "LSID=lsid_accounts",
                    "__Host-GAPS=gaps_token"
                )
            )
        )

        // SID, SIDCC는 .google.com 도메인 쿠키로 단 1회씩만 설정되어야 함
        val sidCommands = commands.filter { it.second.startsWith("SID=") }
        assertEquals(1, sidCommands.size)
        assertEquals("https://www.google.com" to "SID=sid_latest; Domain=.google.com; Path=/; Secure", sidCommands.single())

        // __Host-GAPS 및 LSID는 Domain 속성 없이 accounts.google.com 호스트 전용으로만 설정되어야 함
        val gapsCommand = commands.single { it.second.startsWith("__Host-GAPS=") }
        assertEquals("https://accounts.google.com" to "__Host-GAPS=gaps_token; Path=/; Secure", gapsCommand)
        assertFalse(gapsCommand.second.contains("Domain="))

        val lsidCommand = commands.single { it.second.startsWith("LSID=") }
        assertEquals("https://accounts.google.com" to "LSID=lsid_accounts; Path=/; Secure", lsidCommand)
        assertFalse(lsidCommand.second.contains("Domain="))

        // COMPASS는 Domain 속성 없이 gemini.google.com 호스트 전용으로만 설정되어야 함
        val compassCommand = commands.single { it.second.startsWith("COMPASS=") }
        assertEquals("https://gemini.google.com" to "COMPASS=compass_gemini; Path=/; Secure", compassCommand)
        assertFalse(compassCommand.second.contains("Domain="))
    }

    private class InMemoryMultiUsageLocalDataSource(
        initial: PersistedMultiGeminiUsageSnapshot = PersistedMultiGeminiUsageSnapshot()
    ) : MultiGeminiUsageLocalDataSource {
        private var snapshot: PersistedMultiGeminiUsageSnapshot = initial

        override suspend fun loadSnapshot(): PersistedMultiGeminiUsageSnapshot = snapshot

        override suspend fun saveProfiles(
            profiles: List<GeminiAccountProfile>,
            selectedAccountId: String?
        ) {
            snapshot = snapshot.copy(
                profiles = profiles,
                selectedAccountId = selectedAccountId
            )
        }

        override suspend fun saveAccountSuccess(accountId: String, state: GeminiUsageState.Success) {
            val map = snapshot.cachedUsageByAccountId.toMutableMap()
            map[accountId] = state
            snapshot = snapshot.copy(cachedUsageByAccountId = map)
        }

        override suspend fun removeAccountData(accountId: String) {
            val map = snapshot.cachedUsageByAccountId.toMutableMap()
            map.remove(accountId)
            val remaining = snapshot.profiles.filterNot { it.id == accountId }
            snapshot = snapshot.copy(
                profiles = remaining,
                cachedUsageByAccountId = map
            )
        }

        override suspend fun saveSelectedAccountId(accountId: String?) {
            snapshot = snapshot.copy(selectedAccountId = accountId)
        }
    }
}
