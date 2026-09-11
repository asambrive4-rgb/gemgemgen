// 역할: 백그라운드 및 메인 스레드 비동기 작업을 위한 디스패처 모음을 제공합니다.
package com.example.gemgemgen.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO
)

