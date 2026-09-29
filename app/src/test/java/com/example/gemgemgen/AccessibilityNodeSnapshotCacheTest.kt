// 역할: AccessibilityNodeSnapshotCache의 null 루트 처리 및 특정 키 무효화(invalidate) 동작을 검증하는 단위 테스트
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.AccessibilityNodeSnapshotCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityNodeSnapshotCacheTest {

    @Test
    fun getOrFind_whenRootIsNull_returnsNullAndDoesNotInvokeFind() {
        val cache = AccessibilityNodeSnapshotCache()
        var findCallCount = 0

        val result = cache.getOrFind("test_key", root = null, nowMillis = 1000L) {
            findCallCount++
            null
        }

        assertNull(result)
        assertEquals(0, findCallCount)
    }

    @Test
    fun invalidate_removesSpecifiedKeyFromCache() {
        val cache = AccessibilityNodeSnapshotCache()
        cache.invalidate("input")
        cache.invalidate("toolbar")
    }
}

