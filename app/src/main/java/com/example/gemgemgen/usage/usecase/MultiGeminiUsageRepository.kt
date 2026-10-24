// 역할: 다중 제미나이 계정(5개 이상, 메인/서브 그룹 분리)의 CRUD, 계정 그룹 이동, 수동 소진/복구 및 당분간 사용 금지, 대표 자동 위임, DataStore Warm Start 캐시 즉시 방출, 원격 대표 계정 동기화 및 스마트 새로고침을 조율하는 저장소입니다.
package com.example.gemgemgen.usage.usecase

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.usage.domain.GeminiAccountGroup
import com.example.gemgemgen.usage.domain.GeminiAccountProfile
import com.example.gemgemgen.usage.domain.GeminiAccountUsageEntry
import com.example.gemgemgen.usage.domain.GeminiUsageParser
import com.example.gemgemgen.usage.domain.GeminiUsageState
import com.example.gemgemgen.usage.domain.MultiGeminiUsageState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
 * 각 계정의 격리된 쿠키 세션을 사용해 제미나이 사용량 페이지를 스크래핑하는 포트 인터페이스.
 */
fun interface MultiProfileGeminiUsageRemoteDataSource {
    suspend fun fetchUsage(profile: GeminiAccountProfile): GeminiUsageFetchResult

    /** 계정 삭제 시 해당 계정의 독립 WebView 쿠키/세션 데이터를 정리합니다. */
    suspend fun clearAccountSession(profile: GeminiAccountProfile) {}

    /** 모든 새로고침이 끝난 유휴 시점에 공유 WebView 인스턴스를 해제하여 RAM을 즉시 반환합니다. */
    suspend fun releaseIdleResources() {}
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
    val selectedAccountIdFlow: Flow<String?>
        get() = emptyFlow()
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
 * - 새로고침 시 대표 계정 및 현재 보고 있는 탭의 계정을 우선 순회하며,
 *   모든 조회가 끝나면 공유 WebView 메모리를 즉시 해제합니다.
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
                refreshRepresentativeAccount()
            }
        }

        scope.launch {
            localDataSource.selectedAccountIdFlow.collect { externalSelectedId ->
                if (!externalSelectedId.isNullOrBlank()) {
                    mutationMutex.withLock {
                        val current = _state.value
                        if (current.isInitialized &&
                            current.selectedAccountId != externalSelectedId &&
                            current.accounts.any { it.profile.id == externalSelectedId }
                        ) {
                            _state.value = current.copy(selectedAccountId = externalSelectedId)
                        }
                    }
                }
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
     * 계정의 소속 그룹(메인 ↔ 서브)을 이동하고 DataStore에 영속화합니다.
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
     * 계정의 수동 사용불가(내림) 상태를 변경하고 DataStore에 영속화합니다.
     * 방안 X: 만약 대표 계정을 사용불가로 내리는 경우, 상단에 남은 사용 가능 1위 계정에게 대표 계정을 자동 위임합니다.
     */
    suspend fun setAccountManuallyExhausted(
        accountId: String,
        isExhausted: Boolean
    ): GeminiAccountProfile? {
        return mutationMutex.withLock {
            val currentAccounts = _state.value.accounts
            val index = currentAccounts.indexOfFirst { it.profile.id == accountId }
            if (index < 0) return@withLock null

            val target = currentAccounts[index]
            val now = nowProvider()
            if (target.profile.isManuallyExhausted(now) == isExhausted) return@withLock target.profile

            // 다음 5시간 주기 도래 시각(롤오버 반영). 단서가 없는 신규 계정은 내린 시점 + 5시간
            val nextResetAt = now + (target.nextFiveHourResetRemainingMillis(now)
                ?: GeminiUsageParser.FIVE_HOUR_WINDOW_MILLIS)
            val updatedProfile = target.profile.copy(
                manualExhaustedUntilMillis = if (isExhausted) nextResetAt else 0L
            )
            val updatedAccounts = currentAccounts.toMutableList().apply {
                set(index, target.copy(profile = updatedProfile))
            }

            // 방안 X: 대표 계정을 사용불가로 내린 경우, 남은 사용 가능 1위 계정에게 대표 자리를 자동 위임
            var nextSelectedId = _state.value.selectedAccountId
            if (isExhausted && nextSelectedId == accountId) {
                val tempState = _state.value.copy(accounts = updatedAccounts)
                nextSelectedId = tempState.availableAccountsSorted().firstOrNull()?.profile?.id
                    ?: nextSelectedId
            }

            _state.update {
                it.copy(
                    accounts = updatedAccounts,
                    selectedAccountId = nextSelectedId
                )
            }
            persistProfilesInternal(updatedAccounts.map { it.profile }, nextSelectedId)
            updatedProfile
        }
    }

    /**
     * 비동기 코루틴 스코프에서 계정 수동 사용불가(내림)/복구를 트리거합니다.
     */
    fun triggerSetAccountManuallyExhausted(accountId: String, isExhausted: Boolean) {
        scope.launch {
            setAccountManuallyExhausted(accountId, isExhausted)
        }
    }

    /**
     * 특정 계정을 '당분간 사용 금지' 상태로 전환하거나 금지를 해제합니다.
     * 사용 금지 상태인 계정은 수동으로 해제하기 전까지 자동으로 복구되지 않습니다.
     * 만약 현재 대표 계정을 사용 금지한 경우 남은 사용 가능 1위 계정에게 대표 자리를 자동 위임합니다.
     */
    suspend fun setAccountSuspended(
        accountId: String,
        isSuspended: Boolean
    ): GeminiAccountProfile? {
        return mutationMutex.withLock {
            val currentAccounts = _state.value.accounts
            val index = currentAccounts.indexOfFirst { it.profile.id == accountId }
            if (index < 0) return@withLock null

            val target = currentAccounts[index]
            if (target.profile.isSuspended == isSuspended) return@withLock target.profile

            val updatedProfile = target.profile.copy(isSuspended = isSuspended)
            val updatedAccounts = currentAccounts.toMutableList().apply {
                set(index, target.copy(profile = updatedProfile))
            }

            var nextSelectedId = _state.value.selectedAccountId
            if (isSuspended && nextSelectedId == accountId) {
                val tempState = _state.value.copy(accounts = updatedAccounts)
                nextSelectedId = tempState.availableAccountsSorted().firstOrNull()?.profile?.id
                    ?: nextSelectedId
            }

            _state.update {
                it.copy(
                    accounts = updatedAccounts,
                    selectedAccountId = nextSelectedId
                )
            }
            persistProfilesInternal(updatedAccounts.map { it.profile }, nextSelectedId)
            updatedProfile
        }
    }

    /**
     * 비동기 코루틴 스코프에서 계정 당분간 사용 금지/해제를 트리거합니다.
     */
    fun triggerSetAccountSuspended(accountId: String, isSuspended: Boolean) {
        scope.launch {
            setAccountSuspended(accountId, isSuspended)
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
     * 특정 계정의 세션을 사용해 사용량을 개별 새로고침합니다.
     */
    suspend fun refreshAccount(accountId: String): GeminiUsageState? {
        return try {
            refreshAccountInternal(accountId)
        } finally {
            if (!_state.value.isAnyRefreshing) {
                runCatching { remoteDataSource.releaseIdleResources() }
            }
        }
    }

    private suspend fun refreshAccountInternal(accountId: String): GeminiUsageState? {
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
     * 대표 계정 1개만 새로고침합니다. (앱 시작/자동화 완료 후 가벼운 갱신용)
     */
    suspend fun refreshRepresentativeAccount(): GeminiUsageState? {
        val repId = _state.value.representativeAccount?.profile?.id ?: return null
        return refreshAccount(repId)
    }

    fun triggerRefreshRepresentativeAccount() {
        scope.launch {
            refreshRepresentativeAccount()
        }
    }

    /**
     * 등록된 계정을 스마트 순서(대표 계정 → 우선 그룹(현재 보고 있는 탭) → 나머지 그룹)로 1개씩 순차 새로고침합니다.
     *
     * @param prioritizedGroup 우선 새로고침할 탭 그룹 (미지정 시 대표 계정이 속한 그룹 우선)
     * @param skipFreshWithinMillis 방금 조회된 신선한 계정을 건너뛸 기준 시간(ms). 0이면 건너뛰지 않고 모두 조회.
     */
    suspend fun refreshAll(
        prioritizedGroup: GeminiAccountGroup? = null,
        skipFreshWithinMillis: Long = 0L
    ): Map<String, GeminiUsageState> {
        return refreshAllMutex.withLock {
            val currentState = _state.value
            val entries = currentState.accounts
            if (entries.isEmpty()) return@withLock emptyMap()

            _state.update { it.copy(isRefreshingAll = true) }
            val results = linkedMapOf<String, GeminiUsageState>()
            try {
                val repEntry = currentState.representativeAccount
                val repId = repEntry?.profile?.id
                val targetPrimaryGroup = prioritizedGroup ?: repEntry?.profile?.group

                val orderedEntries = buildList {
                    if (repEntry != null) add(repEntry)
                    if (targetPrimaryGroup != null) {
                        entries.filter { it.profile.id != repId && it.profile.group == targetPrimaryGroup }
                            .forEach(::add)
                        entries.filter { it.profile.id != repId && it.profile.group != targetPrimaryGroup }
                            .forEach(::add)
                    } else {
                        entries.filterNot { it.profile.id == repId }.forEach(::add)
                    }
                }

                for (entry in orderedEntries) {
                    val accountId = entry.profile.id
                    if (skipFreshWithinMillis > 0L) {
                        val latestEntry = _state.value.accounts.firstOrNull { it.profile.id == accountId }
                        val latestSuccess = latestEntry?.usageState as? GeminiUsageState.Success
                        val now = nowProvider()
                        if (latestSuccess != null &&
                            latestSuccess.updatedAt > 0L &&
                            now >= latestSuccess.updatedAt &&
                            now - latestSuccess.updatedAt < skipFreshWithinMillis &&
                            !latestEntry.isFiveHourWindowExpired(now)
                        ) {
                            results[accountId] = latestSuccess
                            continue
                        }
                    }

                    val result = refreshAccountInternal(accountId)
                    if (result != null) {
                        results[accountId] = result
                    }
                }
                results
            } finally {
                _state.update { it.copy(isRefreshingAll = false) }
                if (!_state.value.isAnyRefreshing) {
                    runCatching { remoteDataSource.releaseIdleResources() }
                }
            }
        }
    }

    fun triggerRefreshAll(
        prioritizedGroup: GeminiAccountGroup? = null,
        skipFreshWithinMillis: Long = DEFAULT_SMART_SKIP_FRESH_MS
    ) {
        scope.launch {
            refreshAll(
                prioritizedGroup = prioritizedGroup,
                skipFreshWithinMillis = skipFreshWithinMillis
            )
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

    companion object {
        /** 연속 새로고침 시 45초 이내에 이미 정상 조회된 계정은 중복 스크래핑을 생략합니다. */
        const val DEFAULT_SMART_SKIP_FRESH_MS = 45_000L
    }
}
