package com.example.gemgemgen.core

/**
 * 사용자에게 청각적 피드백(짧은 알림음 등)을 제공하기 위한 게이트웨이 포트.
 * 클린 아키텍처 원칙에 따라 플랫폼(Android 오디오) 세부 구현을 비즈니스/UI 로직과 격리합니다.
 */
interface SoundAlertGateway {
    /**
     * 작업을 방해하지 않는 아주 짧은 알림음(약 120ms 비프음)을 재생합니다.
     */
    fun playShortAlert()
}

object NoOpSoundAlertGateway : SoundAlertGateway {
    override fun playShortAlert() = Unit
}
