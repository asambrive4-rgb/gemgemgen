// 역할: Gemini 계정 관리 및 다음 계정 순환 계산 유스케이스 로직을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.domain.GeminiAccountProfile
import com.example.gemgemgen.automation.usecase.GeminiAccountRepository
import com.example.gemgemgen.automation.usecase.ManageGeminiAccountsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ManageGeminiAccountsUseCaseTest {

    private class FakeGeminiAccountRepository(
        var accounts: List<GeminiAccountProfile> = emptyList()
    ) : GeminiAccountRepository {
        override fun loadAccounts(): List<GeminiAccountProfile> = accounts
        override fun saveAccounts(accounts: List<GeminiAccountProfile>) {
            this.accounts = accounts
        }
    }

    private lateinit var repository: FakeGeminiAccountRepository
    private lateinit var useCase: ManageGeminiAccountsUseCase

    @Before
    fun setUp() {
        repository = FakeGeminiAccountRepository()
        useCase = ManageGeminiAccountsUseCase(repository)
    }

    @Test
    fun getAccounts_emptyReturnsDefaultAccount() {
        val accounts = useCase.getAccounts()
        assertEquals(1, accounts.size)
        assertEquals("서브1", accounts[0].alias)
        assertTrue(accounts[0].isActive)
    }

    @Test
    fun getNextCycleAccount_cyclesCorrectly() {
        useCase.addAccount("서브2", "sub2@test.com")
        useCase.addAccount("서브3", "sub3@test.com")

        // 현재 서브1이 활성 상태일 때 다음은 서브2
        val next1 = useCase.getNextCycleAccount()
        assertNotNull(next1)
        assertEquals("서브2", next1!!.alias)

        // 서브2 활성화 후 다음은 서브3
        useCase.activateAccount(next1.id)
        val next2 = useCase.getNextCycleAccount()
        assertNotNull(next2)
        assertEquals("서브3", next2!!.alias)

        // 서브3 활성화 후 다음은 다시 서브1
        useCase.activateAccount(next2.id)
        val next3 = useCase.getNextCycleAccount()
        assertNotNull(next3)
        assertEquals("서브1", next3!!.alias)
    }

    @Test
    fun deleteAccount_keepsAtLeastOneAccount() {
        val initial = useCase.getAccounts()
        assertEquals(1, initial.size)
        val result = useCase.deleteAccount(initial[0].id)
        assertEquals(1, result.size)
    }

    @Test
    fun deleteActiveAccount_activatesNextRemaining() {
        useCase.addAccount("서브2", "sub2@test.com")
        val accounts = useCase.getAccounts()
        val sub1Id = accounts[0].id
        val sub2Id = accounts[1].id

        val afterDelete = useCase.deleteAccount(sub1Id)
        assertEquals(1, afterDelete.size)
        assertEquals(sub2Id, afterDelete[0].id)
        assertTrue(afterDelete[0].isActive)
    }
}
