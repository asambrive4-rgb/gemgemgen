// 역할: 와일드카드 디렉토리 접근 권한 정책을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.wildcard.domain.WildcardFolderAccessPolicy
import com.example.gemgemgen.wildcard.domain.WildcardFolderAction
import org.junit.Assert.assertEquals
import org.junit.Test

class WildcardFolderAccessPolicyTest {

    @Test
    fun decideAction_whenHasAllFilesAccess_returnsOpenDirectFolder() {
        val actionWithAccessibleDir = WildcardFolderAccessPolicy.decideAction(
            hasAllFilesAccess = true,
            isWildcardDirectoryAccessible = true
        )
        val actionWithInaccessibleDir = WildcardFolderAccessPolicy.decideAction(
            hasAllFilesAccess = true,
            isWildcardDirectoryAccessible = false
        )

        assertEquals(WildcardFolderAction.OpenDirectFolder, actionWithAccessibleDir)
        assertEquals(WildcardFolderAction.OpenDirectFolder, actionWithInaccessibleDir)
    }

    @Test
    fun decideAction_whenNoAllFilesAccessAndDirectoryInaccessible_returnsOpenStorageSettings() {
        val action = WildcardFolderAccessPolicy.decideAction(
            hasAllFilesAccess = false,
            isWildcardDirectoryAccessible = false
        )

        assertEquals(WildcardFolderAction.OpenStorageSettings, action)
    }

    @Test
    fun decideAction_whenNoAllFilesAccessAndDirectoryAccessible_returnsLaunchSafPicker() {
        val action = WildcardFolderAccessPolicy.decideAction(
            hasAllFilesAccess = false,
            isWildcardDirectoryAccessible = true
        )

        assertEquals(WildcardFolderAction.LaunchSafPicker, action)
    }
}
