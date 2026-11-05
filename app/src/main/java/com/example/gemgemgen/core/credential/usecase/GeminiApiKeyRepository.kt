// 역할: Gemini API 키 목록 및 모델 역할 설정을 영구 저장소에서 관리하는 저장소 인터페이스입니다.
package com.example.gemgemgen.core.credential.usecase

import com.example.gemgemgen.core.credential.domain.GeminiApiKeyRecord

interface GeminiApiKeyRepository {
    fun listKeys(): List<GeminiApiKeyRecord>
    fun addKey(label: String, rawKey: String): GeminiApiKeyRecord
    fun deleteKey(id: String)
    fun activateKey(id: String)
    fun activeKeyValue(): String?
    fun updateKeyLabel(id: String, newLabel: String)

    /** 단계(마스킹/생성/분류)별 프로바이더. role = masking | generation */
    fun getRoleProvider(role: String): String
    fun setRoleProvider(role: String, providerId: String)
    /** 단계별 모델 id */
    fun getRoleModel(role: String): String
    fun setRoleModel(role: String, modelId: String)
}
