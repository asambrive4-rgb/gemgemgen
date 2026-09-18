// 역할: 앱 복원 시 사용할 마지막 자동화 실행 설정 스냅샷 저장소 인터페이스를 정의합니다.
package com.example.gemgemgen.automation.usecase

interface LastRunSnapshotRepository {
    fun load(): LastRunSnapshot?
    fun save(snapshot: LastRunSnapshot)
}
