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
