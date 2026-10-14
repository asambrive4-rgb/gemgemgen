// 역할: 제미나이 사용량 데이터의 로컬 캐시(즉시 표시)와 백그라운드 스크래핑 갱신 흐름을 조율하는 저장소입니다.
package com.example.gemgemgen.usage.usecase

import com.example.gemgemgen.core.AppDispatchers
import com.example.gemgemgen.usage.domain.GeminiUsageState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 숨겨진 WebView 등을 통해 원격 제미나이 사용량 페이지를 스크래핑하는 포트 인터페이스.
 */
fun interface GeminiUsageRemoteDataSource {
    suspend fun fetchUsage(): GeminiUsageState
}

/**
 * 마지막으로 성공한 사용량 데이터를 로컬 저장소(DataStore/SharedPreferences)에 캐싱하는 포트 인터페이스.
 */
interface GeminiUsageCacheDataSource {
    suspend fun loadLastSuccess(): GeminiUsageState.Success?
    suspend fun saveLastSuccess(state: GeminiUsageState.Success)
}

/**
 * 제미나이 사용량 조회 및 로컬 캐시 동기화를 담당하는 저장소.
 *
 * - 앱 시작 시 로컬 캐시(DataStore)에 저장된 마지막 사용량 데이터를 즉시 먼저 방출하여
 *   빈 화면/로딩 깜빡임 없이 마지막 값을 먼저 보여줍니다.
 * - 이어서 백그라운드에서 Headless WebView 스크래퍼를 호출해 최신 사용량으로 갱신합니다.
 */
class GeminiUsageRepository(
    private val remoteDataSource: GeminiUsageRemoteDataSource,
    private val cacheDataSource: GeminiUsageCacheDataSource,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers = AppDispatchers(),
    autoRefreshOnInit: Boolean = true
) {
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow<GeminiUsageState>(GeminiUsageState.Loading)
    val state: StateFlow<GeminiUsageState> = _state.asStateFlow()

    private val _cachedSuccess = MutableStateFlow<GeminiUsageState.Success?>(null)
    val cachedSuccess: StateFlow<GeminiUsageState.Success?> = _cachedSuccess.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        scope.launch {
            val cached = runCatching {
                withContext(dispatchers.io) {
                    cacheDataSource.loadLastSuccess()
                }
            }.getOrNull()

            if (cached != null) {
                _cachedSuccess.value = cached
                if (_state.value is GeminiUsageState.Loading) {
                    _state.value = cached
                }
            }

            if (autoRefreshOnInit) {
                refresh()
            }
        }
    }

    /**
     * 비동기 코루틴 스코프에서 사용량 백그라운드 갱신을 트리거합니다.
     */
    fun triggerRefresh() {
        scope.launch {
            refresh()
        }
    }

    /**
     * 원격 WebView 스크래퍼를 통해 최신 사용량을 조회하고 성공 시 로컬 캐시를 갱신합니다.
     */
    suspend fun refresh(): GeminiUsageState {
        return refreshMutex.withLock {
            _isRefreshing.value = true
            // 캐시된 Success가 이미 표시 중이면 화면 깜빡임(Flicker) 방지를 위해 기존 값을 유지한 채 백그라운드 갱신
            if (_state.value !is GeminiUsageState.Success) {
                _state.value = GeminiUsageState.Loading
            }

            try {
                val fetched = remoteDataSource.fetchUsage()
                when (fetched) {
                    is GeminiUsageState.Success -> {
                        _cachedSuccess.value = fetched
                        _state.value = fetched
                        runCatching {
                            withContext(dispatchers.io) {
                                cacheDataSource.saveLastSuccess(fetched)
                            }
                        }
                    }
                    is GeminiUsageState.AuthRequired -> {
                        _state.value = GeminiUsageState.AuthRequired
                    }
                    is GeminiUsageState.Error -> {
                        _state.value = fetched
                    }
                    GeminiUsageState.Loading -> {
                        _state.value = GeminiUsageState.Loading
                    }
                }
                _state.value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val errorState = GeminiUsageState.Error(
                    error.message ?: "제미나이 사용량 조회 중 오류가 발생했습니다."
                )
                _state.value = errorState
                errorState
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
