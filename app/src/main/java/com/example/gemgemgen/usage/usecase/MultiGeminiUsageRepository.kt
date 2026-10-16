// 역할: 다중 제미나이 계정(5개 이상, 메인/서브 그룹 분리)의 CRUD, 계정 그룹 이동, DataStore Warm Start 캐시 즉시 방출, 독립 세션 동시 병렬 새로고침을 조율하는 저장소입니다.
package com.example.gemgemgen.usage.usecase

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.usage.domain.GeminiAccountGroup
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiAccountUsageEntry
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.domain.MultiGeminiUsageState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 계정별 원격 사용량 스크래핑 결과 모델.
 *
 * @property usageState 파싱된 사용량 상태
 * @property detectedEmail DOM에서 자동 감지된 Google 계정 이메일 (선택)
 */
data class GeminiUsageFetchResult(
    val usageState: GeminiUsageState,
    val detectedEmail: String? = null
)

/**
 * 각 계정의 독립된 WebView 세션 프로필을 사용해 제미나이 사용량 페이지를 스크래핑하는 포트 인터페이스.
 */
fun interface MultiProfileGeminiUsageRemoteDataSource {
    suspend fun fetchUsage(profile: GeminiAccountProfile): GeminiUsageFetchResult

    /** 계정 삭제 시 해당 계정의 독립 WebView 쿠키/세션 데이터를 정리합니다. */
    suspend fun clearAccountSession(profile: GeminiAccountProfile) {}
}

/**
 * 로컬 저장소(DataStore)에 영속화된 다중 계정 목록 및 계정별 마지막 성공 스냅샷 묶음.
 */
data class PersistedMultiGeminiUsageSnapshot(
    val profiles: List<GeminiAccountProfile> = emptyList(),
    val cachedUsageByAccountId: Map<String, GeminiUsageState.Success> = emptyMap(),
    val selectedAccountId: String? = null
)

/**
 * 다중 계정 메타데이터와 각 계정별 마지막 성공 사용량 스냅샷을 영구 저장하는 포트 인터페이스.
 */
interface MultiGeminiUsageLocalDataSource {
    suspend fun loadSnapshot(): PersistedMultiGeminiUsageSnapshot
    suspend fun saveProfiles(
        profiles: List<GeminiAccountProfile>,
        selectedAccountId: String? = null
    )
    suspend fun saveAccountSuccess(accountId: String, state: GeminiUsageState.Success)
    suspend fun removeAccountData(accountId: String)
    suspend fun saveSelectedAccountId(accountId: String?)
}

/**
 * 다중 Google Gemini Pro 계정(5개 이상)의 사용량 조회 및 로컬 캐시(Warm Start) 동기화를 총괄하는 저장소.
 *
 * - 앱 시작 시 DataStore에 보관된 계정 목록과 마지막 성공 스냅샷을 0.01초 내 즉시 방출하여
 *   로딩 깜빡임 없이 캐시 데이터를 먼저 표시합니다.
 * - 이후 백그라운드에서 각 계정의 독립 WebView 세션으로 `gemini.google.com/usage`를 동시에 병렬 동기화합니다.
 */
class MultiGeminiUsageRepository(
    private val remoteDataSource: MultiProfileGeminiUsageRemoteDataSource,
    private val localDataSource: MultiGeminiUsageLocalDataSource,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    private val nowProvider: () -> Long = System::currentTimeMillis,
    autoRefreshOnInit: Boolean = true
) {
    private val mutationMutex = Mutex()
    private val refreshAllMutex = Mutex()
    private val accountRefreshMutexes = mutableMapOf<String, Mutex>()
    private val accountMutexLock = Mutex()

    private val _state = MutableStateFlow(MultiGeminiUsageState())
    val state: StateFlow<MultiGeminiUsageState> = _state.asStateFlow()

    init {
        scope.launch {
            val snapshot = runCatching {
                withContext(dispatchers.io) {
                    localDataSource.loadSnapshot()
                }
            }.getOrDefault(PersistedMultiGeminiUsageSnapshot())

            mutationMutex.withLock {
                val currentInMemory = _state.value.accounts
                val restoredEntries = snapshot.profiles
                    .sortedBy { it.sortOrder }
                    .mapIndexed { index, profile ->
                        val normalizedProfile = profile.copy(sortOrder = index)
                        val cached = snapshot.cachedUsageByAccountId[profile.id]
                        GeminiAccountUsageEntry(
                            profile = normalizedProfile,
                            usageState = cached ?: GeminiUsageState.Loading,
                            cachedSuccess = cached,
                            isRefreshing = false
                        )
                    }

                val mergedEntries = if (currentInMemory.isEmpty()) {
                    restoredEntries
                } else {
                    val existingIds = restoredEntries.map { it.profile.id }.toSet()
                    val additional = currentInMemory.filterNot { it.profile.id in existingIds }
                    (restoredEntries + additional).mapIndexed { idx, entry ->
                        entry.copy(profile = entry.profile.copy(sortOrder = idx))
                    }
                }

                val resolvedSelectedId = _state.value.selectedAccountId
                    ?: snapshot.selectedAccountId?.takeIf { id -> mergedEntries.any { it.profile.id == id } }
                    ?: mergedEntries.firstOrNull()?.profile?.id

                _state.value = MultiGeminiUsageState(
                    accounts = mergedEntries,
                    selectedAccountId = resolvedSelectedId,
                    isRefreshingAll = false,
                    isInitialized = true
                )
            }

            if (autoRefreshOnInit && _state.value.accounts.isNotEmpty()) {
                refreshAll()
            }
        }
    }

    /**
     * 새 계정을 추가하고 DataStore에 즉시 영속화합니다.
     */
    suspend fun addAccount(
        alias: String,
        email: String = "",
        accountId: String? = null,
        group: GeminiAccountGroup = GeminiAccountGroup.MAIN,
        autoRefresh: Boolean = true
    ): GeminiAccountProfile {
        val createdProfile = mutationMutex.withLock {
            val currentAccounts = _state.value.accounts
            val resolvedId = accountId?.trim()?.takeIf { it.isNotEmpty() }
                ?: GeminiAccountProfile.generateAccountId()

            val existingIndex = currentAccounts.indexOfFirst { it.profile.id == resolvedId }
            if (existingIndex >= 0) {
                val existingEntry = currentAccounts[existingIndex]
                val updatedProfile = existingEntry.profile.copy(
                    alias = alias.trim().ifEmpty { existingEntry.profile.alias },
                    email = email.trim().ifEmpty { existingEntry.profile.email }
                )
                val updatedList = currentAccounts.toMutableList().apply {
                    set(existingIndex, existingEntry.copy(profile = updatedProfile))
                }
                val selectedId = _state.value.selectedAccountId ?: updatedProfile.id
                _state.update {
                    it.copy(
                        accounts = updatedList,
                        selectedAccountId = selectedId
                    )
                }
                persistProfilesInternal(updatedList.map { it.profile }, selectedId)
                return@withLock updatedProfile
            }

            val nextOrder = currentAccounts.size
            val defaultAlias = if (nextOrder == 0) "본계정" else "계정 ${nextOrder + 1}"
            val resolvedAlias = alias.trim().ifEmpty { defaultAlias }
            val newProfile = GeminiAccountProfile(
                id = resolvedId,
                alias = resolvedAlias,
                email = email.trim(),
                sortOrder = nextOrder,
                webViewProfileName = GeminiAccountProfile.buildWebViewProfileName(resolvedId),
                createdAt = nowProvider(),
                group = group
            )
            val newEntry = GeminiAccountUsageEntry(
                profile = newProfile,
                usageState = GeminiUsageState.Loading,
                cachedSuccess = null,
                isRefreshing = false
            )
            val updatedAccounts = currentAccounts + newEntry
            val selectedId = _state.value.selectedAccountId ?: newProfile.id

            _state.update {
                it.copy(
                    accounts = updatedAccounts,
                    selectedAccountId = selectedId
                )
            }
            persistProfilesInternal(updatedAccounts.map { it.profile }, selectedId)
            newProfile
        }

        if (autoRefresh) {
            refreshAccount(createdProfile.id)
        }
        return createdProfile
    }

    /**
     * 비동기 코루틴 스코프에서 새 계정 추가를 트리거합니다.
     */
    fun triggerAddAccount(
        alias: String,
        email: String = "",
        accountId: String? = null,
        group: GeminiAccountGroup = GeminiAccountGroup.MAIN,
        autoRefresh: Boolean = true
    ) {
        scope.launch {
            addAccount(
                alias = alias,
                email = email,
                accountId = accountId,
                group = group,
                autoRefresh = autoRefresh
            )
        }
    }

    /**
     * 계정의 소속 그룹(메인 ↔ 서브)을 이동하고 DataStore에 영속화합니다. (추후 탭 이동 기능 대비용)
     */
    suspend fun moveAccountGroup(
        accountId: String,
        targetGroup: GeminiAccountGroup
    ): GeminiAccountProfile? {
        return mutationMutex.withLock {
            val currentAccounts = _state.value.accounts
            val index = currentAccounts.indexOfFirst { it.profile.id == accountId }
            if (index < 0) return@withLock null

            val target = currentAccounts[index]
            if (target.profile.group == targetGroup) return@withLock target.profile

            val updatedProfile = target.profile.copy(group = targetGroup)
            val updatedAccounts = currentAccounts.toMutableList().apply {
                set(index, target.copy(profile = updatedProfile))
            }
            _state.update { it.copy(accounts = updatedAccounts) }
            persistProfilesInternal(updatedAccounts.map { it.profile }, _state.value.selectedAccountId)
            updatedProfile
        }
    }

    /**
     * 비동기 코루틴 스코프에서 계정 소속 그룹 이동을 트리거합니다.
     */
    fun triggerMoveAccountGroup(accountId: String, targetGroup: GeminiAccountGroup) {
        scope.launch {
            moveAccountGroup(accountId, targetGroup)
        }
    }

    /**
     * 기존 계정의 별칭 또는 이메일 메타데이터를 수정합니다.
     */
    suspend fun updateAccountProfile(
        accountId: String,
        alias: String,
        email: String = ""
    ): GeminiAccountProfile? {
        return mutationMutex.withLock {
            val currentAccounts = _state.value.accounts
            val index = currentAccounts.indexOfFirst { it.profile.id == accountId }
            if (index < 0) return@withLock null

            val target = currentAccounts[index]
            val updatedProfile = target.profile.copy(
                alias = alias.trim().ifEmpty { target.profile.alias },
                email = email.trim().ifEmpty { target.profile.email }
            )
            val updatedAccounts = currentAccounts.toMutableList().apply {
                set(index, target.copy(profile = updatedProfile))
            }
            _state.update { it.copy(accounts = updatedAccounts) }
            persistProfilesInternal(updatedAccounts.map { it.profile }, _state.value.selectedAccountId)
            updatedProfile
        }
    }

    fun triggerUpdateAccountProfile(accountId: String, alias: String, email: String = "") {
        scope.launch {
            updateAccountProfile(accountId = accountId, alias = alias, email = email)
        }
    }

    /**
     * 계정을 삭제하고 연결된 로컬 캐시 및 독립 WebView 세션을 정리합니다.
     */
    suspend fun removeAccount(accountId: String): Boolean {
        val removedProfile = mutationMutex.withLock {
            val currentAccounts = _state.value.accounts
            val targetEntry = currentAccounts.firstOrNull { it.profile.id == accountId }
                ?: return@withLock null

            val remaining = currentAccounts
                .filterNot { it.profile.id == accountId }
                .mapIndexed { index, entry ->
                    entry.copy(profile = entry.profile.copy(sortOrder = index))
                }

            val nextSelectedId = _state.value.selectedAccountId
                ?.takeIf { selected -> remaining.any { it.profile.id == selected } }
                ?: remaining.firstOrNull()?.profile?.id

            _state.update {
                it.copy(
                    accounts = remaining,
                    selectedAccountId = nextSelectedId
                )
            }

            runCatching {
                withContext(dispatchers.io) {
                    localDataSource.removeAccountData(accountId)
                    localDataSource.saveProfiles(
                        profiles = remaining.map { it.profile },
                        selectedAccountId = nextSelectedId
                    )
                }
            }
            targetEntry.profile
        } ?: return false

        runCatching {
            remoteDataSource.clearAccountSession(removedProfile)
        }
        return true
    }

    fun triggerRemoveAccount(accountId: String) {
        scope.launch {
            removeAccount(accountId)
        }
    }

    /**
     * 메인 화면 요약 버튼에 표시할 대표 계정을 변경합니다.
     */
    suspend fun selectRepresentativeAccount(accountId: String) {
        mutationMutex.withLock {
            if (_state.value.accounts.none { it.profile.id == accountId }) return@withLock
            _state.update { it.copy(selectedAccountId = accountId) }
            runCatching {
                withContext(dispatchers.io) {
                    localDataSource.saveSelectedAccountId(accountId)
                }
            }
        }
    }

    fun triggerSelectRepresentativeAccount(accountId: String) {
        scope.launch {
            selectRepresentativeAccount(accountId)
        }
    }

    /**
     * 특정 계정의 독립 WebView 세션을 사용해 사용량을 개별 새로고침합니다.
     */
    suspend fun refreshAccount(accountId: String): GeminiUsageState? {
        val accountMutex = getAccountMutex(accountId)
        return accountMutex.withLock {
            val initialEntry = _state.value.accounts.firstOrNull { it.profile.id == accountId }
                ?: return@withLock null

            // 캐시된 Success가 이미 있으면 화면 깜빡임(Flicker) 방지를 위해 기존 상태를 유지한 채 갱신
            _state.update { current ->
                current.copy(
                    accounts = current.accounts.map { entry ->
                        if (entry.profile.id == accountId) {
                            val nextUsageState = if (entry.usageState is GeminiUsageState.Success) {
                                entry.usageState
                            } else {
                                entry.cachedSuccess ?: GeminiUsageState.Loading
                            }
                            entry.copy(
                                usageState = nextUsageState,
                                isRefreshing = true
                            )
                        } else {
                            entry
                        }
                    }
                )
            }

            try {
                val fetchResult = remoteDataSource.fetchUsage(initialEntry.profile)
                val fetchedState = fetchResult.usageState
                val detectedEmail = fetchResult.detectedEmail?.trim().orEmpty()

                var updatedProfileToPersist: List<GeminiAccountProfile>? = null
                var selectedIdToPersist: String? = null

                mutationMutex.withLock {
                    val current = _state.value
                    if (current.accounts.none { it.profile.id == accountId }) {
                        return@withLock
                    }

                    val updatedAccounts = current.accounts.map { entry ->
                        if (entry.profile.id != accountId) return@map entry

                        val nextProfile = if (detectedEmail.isNotEmpty() && entry.profile.email != detectedEmail) {
                            entry.profile.copy(email = detectedEmail)
                        } else {
                            entry.profile
                        }

                        val nextCached = if (fetchedState is GeminiUsageState.Success) {
                            fetchedState
                        } else {
                            entry.cachedSuccess
                        }

                        entry.copy(
                            profile = nextProfile,
                            usageState = fetchedState,
                            cachedSuccess = nextCached,
                            isRefreshing = false
                        )
                    }

                    _state.value = current.copy(accounts = updatedAccounts)
                    if (detectedEmail.isNotEmpty()) {
                        updatedProfileToPersist = updatedAccounts.map { it.profile }
                        selectedIdToPersist = current.selectedAccountId
                    }
                }

                if (fetchedState is GeminiUsageState.Success) {
                    runCatching {
                        withContext(dispatchers.io) {
                            localDataSource.saveAccountSuccess(accountId, fetchedState)
                        }
                    }
                }
                updatedProfileToPersist?.let { profiles ->
                    persistProfilesInternal(profiles, selectedIdToPersist)
                }

                fetchedState
            } catch (cancelled: CancellationException) {
                clearAccountRefreshingFlag(accountId)
                throw cancelled
            } catch (error: Exception) {
                val errorState = GeminiUsageState.Error(
                    error.message ?: "제미나이 사용량 조회 중 오류가 발생했습니다."
                )
                mutationMutex.withLock {
                    _state.update { current ->
                        current.copy(
                            accounts = current.accounts.map { entry ->
                                if (entry.profile.id == accountId) {
                                    entry.copy(
                                        usageState = errorState,
                                        isRefreshing = false
                                    )
                                } else {
                                    entry
                                }
                            }
                        )
                    }
                }
                errorState
            }
        }
    }

    fun triggerRefreshAccount(accountId: String) {
        scope.launch {
            refreshAccount(accountId)
        }
    }

    /**
     * 등록된 모든 계정의 사용량을 동시에 병렬로 새로고침합니다.
     */
    suspend fun refreshAll(): Map<String, GeminiUsageState> {
        return refreshAllMutex.withLock {
            val accountIds = _state.value.accounts.map { it.profile.id }
            if (accountIds.isEmpty()) return@withLock emptyMap()

            _state.update { it.copy(isRefreshingAll = true) }
            val results = linkedMapOf<String, GeminiUsageState>()
            try {
                val fetchedPairs = coroutineScope {
                    accountIds.map { accountId ->
                        async {
                            accountId to refreshAccount(accountId)
                        }
                    }.awaitAll()
                }
                for ((accountId, result) in fetchedPairs) {
                    if (result != null) {
                        results[accountId] = result
                    }
                }
                results
            } finally {
                _state.update { it.copy(isRefreshingAll = false) }
            }
        }
    }

    fun triggerRefreshAll() {
        scope.launch {
            refreshAll()
        }
    }

    private suspend fun getAccountMutex(accountId: String): Mutex {
        return accountMutexLock.withLock {
            accountRefreshMutexes.getOrPut(accountId) { Mutex() }
        }
    }

    private fun clearAccountRefreshingFlag(accountId: String) {
        _state.update { current ->
            current.copy(
                accounts = current.accounts.map { entry ->
                    if (entry.profile.id == accountId) {
                        entry.copy(isRefreshing = false)
                    } else {
                        entry
                    }
                }
            )
        }
    }

    private suspend fun persistProfilesInternal(
        profiles: List<GeminiAccountProfile>,
        selectedAccountId: String?
    ) {
        runCatching {
            withContext(dispatchers.io) {
                localDataSource.saveProfiles(
                    profiles = profiles,
                    selectedAccountId = selectedAccountId
                )
            }
        }
    }
}
