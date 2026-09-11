// 역할: 시스템 클립보드 복사 및 조회 인터페이스를 정의합니다.
package com.example.gemgemgen.core

interface ClipboardGateway {
    fun readText(): String
    fun writeText(text: String)
}
