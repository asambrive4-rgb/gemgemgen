package com.example.gemgemgen.environment.usecase

import com.example.gemgemgen.environment.domain.EnvironmentStatus

interface EnvironmentGateway {
    fun check(): EnvironmentStatus
}

class CheckEnvironmentStatusUseCase(
    private val gateway: EnvironmentGateway
) {
    fun check(): EnvironmentStatus {
        return gateway.check()
    }
}
