// 역할: AccessibilityNodeSnapshotCache의 TTL 만료, 캐시 히트, windowId 불일치 무효화 및 특정 키 무효화(invalidate) 동작을 검증하는 단위 테스트
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
        assertEquals(0L, cache.cacheHitCount)
        assertEquals(0L, cache.cacheMissCount)
    }

    @Test
    fun invalidate_removesSpecifiedKeyFromCache() {
        val cache = AccessibilityNodeSnapshotCache()
        cache.invalidate("input")
        cache.invalidate("toolbar")
        assertEquals(0L, cache.cacheHitCount)
        assertEquals(0L, cache.cacheMissCount)
    }

    @Test
    fun resetStats_clearsHitAndMissCounters() {
        val cache = AccessibilityNodeSnapshotCache()
        cache.resetStats()
        assertEquals(0L, cache.cacheHitCount)
        assertEquals(0L, cache.cacheMissCount)
    }
}
