// 역할: Gemini 계정 목록의 추가, 삭제, 순환 순서 계산 및 현재 활성 계정 전환 유스케이스를 제공합니다.
package com.example.gemgemgen.automation.usecase

import com.example.gemgemgen.automation.domain.GeminiAccountProfile
import java.util.UUID

class ManageGeminiAccountsUseCase(
    private val repository: GeminiAccountRepository
) {
    fun getAccounts(): List<GeminiAccountProfile> {
        val list = repository.loadAccounts()
        if (list.isEmpty()) {
            val defaultList = listOf(
                GeminiAccountProfile(
                    id = "default-1",
                    alias = "서브1",
                    identifier = "",
                    order = 1,
                    isActive = true
                )
            )
            repository.saveAccounts(defaultList)
            return defaultList
        }
        return list.sortedBy { it.order }
    }

    fun getActiveAccount(): GeminiAccountProfile {
        val accounts = getAccounts()
        return accounts.firstOrNull { it.isActive } ?: accounts.first()
    }

    fun getNextCycleAccount(): GeminiAccountProfile? {
        val accounts = getAccounts()
        if (accounts.size <= 1) return null
        val currentIndex = accounts.indexOfFirst { it.isActive }
        val nextIndex = if (currentIndex in accounts.indices) {
            (currentIndex + 1) % accounts.size
        } else {
            0
        }
        return accounts[nextIndex]
    }

    fun activateAccount(id: String): List<GeminiAccountProfile> {
        val current = getAccounts()
        val updated = current.map { it.copy(isActive = (it.id == id)) }
        repository.saveAccounts(updated)
        return updated
    }

    fun addAccount(alias: String, identifier: String): List<GeminiAccountProfile> {
        val current = getAccounts()
        val trimmedAlias = alias.trim().ifBlank { "서브${current.size + 1}" }
        val trimmedId = identifier.trim()
        val newAccount = GeminiAccountProfile(
            id = UUID.randomUUID().toString(),
            alias = trimmedAlias,
            identifier = trimmedId,
            order = (current.maxOfOrNull { it.order } ?: 0) + 1,
            isActive = false
        )
        val updated = current + newAccount
        repository.saveAccounts(updated)
        return updated
    }

    fun deleteAccount(id: String): List<GeminiAccountProfile> {
        val current = getAccounts()
        if (current.size <= 1) return current
        val target = current.firstOrNull { it.id == id } ?: return current
        val remaining = current.filter { it.id != id }
        val updated = if (target.isActive && remaining.isNotEmpty()) {
            remaining.mapIndexed { index, account ->
                account.copy(order = index + 1, isActive = (index == 0))
            }
        } else {
            remaining.mapIndexed { index, account ->
                account.copy(order = index + 1)
            }
        }
        repository.saveAccounts(updated)
        return updated
    }
}
