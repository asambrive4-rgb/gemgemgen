// 역할: 기기의 시스템 환경 권한 상태 조회를 위한 인터페이스를 정의합니다.
package com.example.gemgemgen.environment.usecase

import com.example.gemgemgen.environment.domain.EnvironmentReport

interface EnvironmentGateway {
    fun check(): EnvironmentReport
}

class CheckEnvironmentStatusUseCase(
    private val gateway: EnvironmentGateway
) {
    fun check(): EnvironmentReport {
        return gateway.check()
    }
}
