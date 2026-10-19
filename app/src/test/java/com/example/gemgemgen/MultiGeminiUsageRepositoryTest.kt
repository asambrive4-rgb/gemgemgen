// 역할: 5개 이상의 다중 제미나이 계정 추가·삭제(CRUD), 메인/서브 그룹 분리, 수동 소진/복구 및 대표 자동 위임, 선택 탭 우선 스마트 새로고침, 유휴 WebView 메모리 즉시 해제 및 독립 쿠키 세션 격리를 검증합니다.
package com.example.gemgemgen

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.usage.android.DataStoreMultiGeminiUsageDataSource
import com.example.gemgemgen.usage.android.GeminiAccountCookieVault
import com.example.gemgemgen.usage.domain.GeminiAccountGroup
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiAccountUsageEntry
import com.example.gemgemgen.usage.domain.GeminiQuotaExhaustionReason
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
        val baseNow = System.currentTimeMillis()
        val clearedProfiles = mutableListOf<String>()
        val fakeRemote = object : MultiProfileGeminiUsageRemoteDataSource {
            override suspend fun fetchUsage(profile: GeminiAccountProfile): GeminiUsageFetchResult {
                val basePct = (profile.sortOrder + 1) * 15
                return GeminiUsageFetchResult(
                    usageState = GeminiUsageState.Success(
                        fiveHourPercent = basePct.coerceAtMost(95),
                        fiveHourResetText = "2시간 15분 후",
                        weeklyPercent = (basePct + 10).coerceAtMost(99),
                        weeklyResetText = "3일 후 초기화",
                        updatedAt = baseNow + profile.sortOrder
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
        val initialUpdatedAt = System.currentTimeMillis() - 10_000L
        val syncedUpdatedAt = initialUpdatedAt + 5_000L
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
                weeklyResetText = "3일 후 초기화",
                updatedAt = initialUpdatedAt
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
                        weeklyResetText = "3일 후 초기화",
                        updatedAt = syncedUpdatedAt
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
        // 앱 시작 시에는 대표 계정(acc_1) 1개만 백그라운드 새로고침됨
        assertFalse(warmState.isRefreshingAll)
        assertTrue(warmState.accounts[0].isRefreshing)
        assertTrue(warmState.accounts.drop(1).none { it.isRefreshing })

        // 백그라운드 동기화 완료 후 대표 계정만 최신 수치로 갱신되고 나머지는 캐시 유지
        networkBarrier.complete(Unit)

        val syncedState = repository.state.value
        assertFalse(syncedState.isRefreshingAll)
        assertEquals("본계정 50% ▾", syncedState.summaryButtonText)
        assertEquals(50, syncedState.accounts[0].displayedSuccess?.fiveHourPercent)
        assertEquals(82, syncedState.accounts[4].displayedSuccess?.fiveHourPercent)
        assertEquals(initialUpdatedAt, syncedState.accounts[4].displayedSuccess?.updatedAt)
    }

    @Test
    fun refreshAll_executesSequentiallyStartingWithRepresentativeAccount() = runBlocking {
        val profiles = (1..5).map { idx ->
            GeminiAccountProfile(
                id = "acc_$idx",
                alias = "계정 $idx",
                email = "account$idx@gmail.com",
                sortOrder = idx - 1,
                createdAt = 1000L * idx
            )
        }
        val fakeLocal = InMemoryMultiUsageLocalDataSource(
            PersistedMultiGeminiUsageSnapshot(
                profiles = profiles,
                selectedAccountId = "acc_3"
            )
        )
        val fetchOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
        val concurrent = java.util.concurrent.atomic.AtomicInteger(0)
        val maxConcurrent = java.util.concurrent.atomic.AtomicInteger(0)

        val repository = MultiGeminiUsageRepository(
            remoteDataSource = { profile ->
                val now = concurrent.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, now) }
                fetchOrder.add(profile.id)
                kotlinx.coroutines.delay(20L)
                concurrent.decrementAndGet()
                GeminiUsageFetchResult(
                    usageState = GeminiUsageState.Success(
                        fiveHourPercent = 10,
                        fiveHourResetText = "1시간 후",
                        weeklyPercent = 20,
                        weeklyResetText = "10/19 오전 9시",
                        updatedAt = 1L
                    )
                )
            },
            localDataSource = fakeLocal,
            scope = CoroutineScope(Dispatchers.Unconfined),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = false
        )

        val results = repository.refreshAll(skipFreshWithinMillis = 0L)

        assertEquals(5, results.size)
        assertEquals(1, maxConcurrent.get())
        assertEquals(listOf("acc_3", "acc_1", "acc_2", "acc_4", "acc_5"), fetchOrder.toList())
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

    @Test
    fun legacyJsonWithoutGroup_migratesAutomaticallyToMainGroup() {
        // 'group' 필드가 아예 없는 구버전 DataStore JSON 데이터
        val legacyJson = """
            [
              {"id":"acc_1","alias":"본계정","email":"main@gmail.com","sort_order":0,"created_at":1000},
              {"id":"acc_2","alias":"작업용 1","email":"work@gmail.com","sort_order":1,"created_at":2000}
            ]
        """.trimIndent()

        val parsed = DataStoreMultiGeminiUsageDataSource.parseProfilesJson(legacyJson)
        assertEquals(2, parsed.size)
        // 두 계정 모두 자동으로 MAIN 그룹으로 마이그레이션되어야 함
        assertEquals(GeminiAccountGroup.MAIN, parsed[0].group)
        assertEquals(GeminiAccountGroup.MAIN, parsed[1].group)
    }

    @Test
    fun addAccountsWithMainAndSubGroups_filtersAndSortsPerGroup() = runBlocking {
        val fakeRemote = MultiProfileGeminiUsageRemoteDataSource { profile ->
            GeminiUsageFetchResult(
                usageState = GeminiUsageState.Success(
                    fiveHourPercent = 10,
                    fiveHourResetText = "2시간 후",
                    weeklyPercent = 20,
                    weeklyResetText = "내일",
                    updatedAt = 1000L
                )
            )
        }
        val fakeLocal = InMemoryMultiUsageLocalDataSource()
        val repository = MultiGeminiUsageRepository(
            remoteDataSource = fakeRemote,
            localDataSource = fakeLocal,
            scope = CoroutineScope(Dispatchers.Unconfined),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = false
        )

        // 메인 계정 2개, 서브 계정 3개 등록
        repository.addAccount(alias = "메인1", email = "m1@gmail.com", group = GeminiAccountGroup.MAIN)
        repository.addAccount(alias = "메인2", email = "m2@gmail.com", group = GeminiAccountGroup.MAIN)
        repository.addAccount(alias = "서브1", email = "s1@gmail.com", group = GeminiAccountGroup.SUB)
        repository.addAccount(alias = "서브2", email = "s2@gmail.com", group = GeminiAccountGroup.SUB)
        repository.addAccount(alias = "서브3", email = "s3@gmail.com", group = GeminiAccountGroup.SUB)

        val state = repository.state.value
        assertEquals(5, state.accountCount)
        assertEquals(2, state.mainAccountCount)
        assertEquals(3, state.subAccountCount)

        val mainList = state.availableAccountsSorted(group = GeminiAccountGroup.MAIN)
        assertEquals(2, mainList.size)
        assertTrue(mainList.all { it.profile.group == GeminiAccountGroup.MAIN })

        val subList = state.availableAccountsSorted(group = GeminiAccountGroup.SUB)
        assertEquals(3, subList.size)
        assertTrue(subList.all { it.profile.group == GeminiAccountGroup.SUB })
    }

    @Test
    fun moveAccountGroup_updatesTargetGroupAndPersists() = runBlocking {
        val fakeRemote = MultiProfileGeminiUsageRemoteDataSource {
            GeminiUsageFetchResult(usageState = GeminiUsageState.Loading)
        }
        val fakeLocal = InMemoryMultiUsageLocalDataSource()
        val repository = MultiGeminiUsageRepository(
            remoteDataSource = fakeRemote,
            localDataSource = fakeLocal,
            scope = CoroutineScope(Dispatchers.Unconfined),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = false
        )

        val created = repository.addAccount(alias = "테스트 계정", group = GeminiAccountGroup.MAIN)
        assertEquals(GeminiAccountGroup.MAIN, repository.state.value.accounts.single().profile.group)

        // 메인 -> 서브로 이동
        val moved = repository.moveAccountGroup(created.id, GeminiAccountGroup.SUB)
        assertNotNull(moved)
        assertEquals(GeminiAccountGroup.SUB, moved!!.group)
        assertEquals(GeminiAccountGroup.SUB, repository.state.value.accounts.single().profile.group)
        assertEquals(0, repository.state.value.mainAccountCount)
        assertEquals(1, repository.state.value.subAccountCount)
    }

    @Test
    fun refreshAll_prioritizesSelectedGroup_skipsFreshAccounts_andReleasesIdleResources() = runBlocking {
        val nowMillis = System.currentTimeMillis()
        val profiles = listOf(
            GeminiAccountProfile(id = "main_1", alias = "메인1", sortOrder = 0, group = GeminiAccountGroup.MAIN),
            GeminiAccountProfile(id = "main_2", alias = "메인2(최신)", sortOrder = 1, group = GeminiAccountGroup.MAIN),
            GeminiAccountProfile(id = "sub_1", alias = "서브1", sortOrder = 2, group = GeminiAccountGroup.SUB),
            GeminiAccountProfile(id = "sub_2", alias = "서브2", sortOrder = 3, group = GeminiAccountGroup.SUB)
        )
        // main_2는 5초 전에 이미 새로고침됨 (45초 이내이므로 스킵 대상)
        val cachedMap = mapOf(
            "main_2" to GeminiUsageState.Success(
                fiveHourPercent = 12,
                fiveHourResetText = "4시간 후",
                weeklyPercent = 20,
                weeklyResetText = "5일 후",
                updatedAt = nowMillis - 5_000L
            )
        )
        val fakeLocal = InMemoryMultiUsageLocalDataSource(
            PersistedMultiGeminiUsageSnapshot(
                profiles = profiles,
                cachedUsageByAccountId = cachedMap,
                selectedAccountId = "main_1"
            )
        )
        val fetchedIds = mutableListOf<String>()
        val releaseCalls = java.util.concurrent.atomic.AtomicInteger(0)

        val fakeRemote = object : MultiProfileGeminiUsageRemoteDataSource {
            override suspend fun fetchUsage(profile: GeminiAccountProfile): GeminiUsageFetchResult {
                fetchedIds.add(profile.id)
                return GeminiUsageFetchResult(
                    usageState = GeminiUsageState.Success(
                        fiveHourPercent = 25,
                        fiveHourResetText = "3시간 후",
                        weeklyPercent = 40,
                        weeklyResetText = "4일 후",
                        updatedAt = nowMillis
                    )
                )
            }

            override suspend fun releaseIdleResources() {
                releaseCalls.incrementAndGet()
            }
        }

        val repository = MultiGeminiUsageRepository(
            remoteDataSource = fakeRemote,
            localDataSource = fakeLocal,
            scope = CoroutineScope(Dispatchers.Unconfined),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = false
        )

        // 서브 탭(SUB) 우선 새로고침 실행:
        // 기대 순서: 대표 계정(main_1) -> 우선 그룹(sub_1, sub_2) -> 나머지 그룹 중 신선하지 않은 계정(main_2는 5초 전 갱신이므로 스킵!)
        val results = repository.refreshAll(
            prioritizedGroup = GeminiAccountGroup.SUB,
            skipFreshWithinMillis = 45_000L
        )

        assertEquals(listOf("main_1", "sub_1", "sub_2"), fetchedIds)
        assertEquals(12, (results["main_2"] as? GeminiUsageState.Success)?.fiveHourPercent)
        assertTrue("새로고침 완료 후 유휴 WebView 리소스 해제가 호출되어야 함", releaseCalls.get() >= 1)
    }

    @Test
    fun setAccountManuallyExhausted_movesAccountToExhaustedAndDelegatesRepresentative() = runBlocking {
        val fakeRemote = object : MultiProfileGeminiUsageRemoteDataSource {
            override suspend fun fetchUsage(profile: GeminiAccountProfile): GeminiUsageFetchResult {
                return GeminiUsageFetchResult(
                    usageState = GeminiUsageState.Success(
                        fiveHourPercent = 10,
                        fiveHourResetText = "3시간 후",
                        weeklyPercent = 20,
                        weeklyResetText = "4일 후",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        val fakeLocal = InMemoryMultiUsageLocalDataSource()
        val repository = MultiGeminiUsageRepository(
            remoteDataSource = fakeRemote,
            localDataSource = fakeLocal,
            scope = CoroutineScope(Dispatchers.Unconfined),
            dispatchers = AppDispatchers(io = Dispatchers.Unconfined, main = Dispatchers.Unconfined),
            autoRefreshOnInit = false
        )

        repository.addAccount(alias = "계정 1", accountId = "acc_1", autoRefresh = true)
        repository.addAccount(alias = "계정 2", accountId = "acc_2", autoRefresh = true)

        assertEquals("acc_1", repository.state.value.selectedAccountId)

        // acc_1을 수동 사용불가(내림) 처리
        val updated = repository.setAccountManuallyExhausted("acc_1", true)
        assertNotNull(updated)
        assertTrue(updated!!.isManuallyExhausted(System.currentTimeMillis()))

        val state = repository.state.value
        // 방안 X: 대표 계정이었던 acc_1이 소진 목록으로 내려가면서, 사용 가능한 1위 계정인 acc_2로 대표 계정 자동 위임
        assertEquals("acc_2", state.selectedAccountId)

        val acc1Entry = state.accounts.first { it.profile.id == "acc_1" }
        assertTrue(acc1Entry.isQuotaExhausted())
        assertEquals(GeminiQuotaExhaustionReason.MANUAL_EXHAUSTED, acc1Entry.quotaExhaustionReason())

        // acc_1을 다시 사용 가능으로 복구
        val recovered = repository.setAccountManuallyExhausted("acc_1", false)
        assertNotNull(recovered)
        assertFalse(recovered!!.isManuallyExhausted(System.currentTimeMillis()))
        assertFalse(repository.state.value.accounts.first { it.profile.id == "acc_1" }.isQuotaExhausted())
    }

    @Test
    fun manualExhaustion_automaticallyRecoversWhenResetTimeExpires() = runBlocking {
        val now = 1_000_000L
        val entry = GeminiAccountUsageEntry(
            profile = GeminiAccountProfile(
                id = "acc_test",
                alias = "테스트",
                sortOrder = 0,
                manualExhaustedUntilMillis = now + 60 * 60 * 1000L
            ),
            usageState = GeminiUsageState.Success(
                fiveHourPercent = 95,
                fiveHourResetText = "1시간 후",
                weeklyPercent = 30,
                weeklyResetText = "3일 후",
                updatedAt = now
            )
        )

        // 1시간 뒤 초기화 전 (30분 경과) -> 여전히 수동 소진 상태
        val duringWindow = now + 30 * 60 * 1000L
        assertTrue(entry.isQuotaExhausted(duringWindow))
        assertEquals(GeminiQuotaExhaustionReason.MANUAL_EXHAUSTED, entry.quotaExhaustionReason(duringWindow))

        // 방안 A: 1시간 10분 경과하여 5시간 리셋 시간이 지남 -> 자동 복구되어 정상(null) 반환
        val afterReset = now + 70 * 60 * 1000L
        assertFalse(entry.isQuotaExhausted(afterReset))
        assertNull(entry.quotaExhaustionReason(afterReset))
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
