// 역할: Iterable 및 Sequence 스트림 기반 최단 거리 접근성 노드 선정 알고리즘을 검증합니다.
package com.example.gemgemgen

import com.example.gemgemgen.automation.android.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearestNodeSelectorTest {
    @Test
    fun nearestTo_returnsCandidateClosestToAnchorCenter() {
        val anchor = NodeBounds(left = 100, top = 100, right = 200, bottom = 200)
        val far = "far" to NodeBounds(left = 900, top = 900, right = 1000, bottom = 1000)
        val near = "near" to NodeBounds(left = 180, top = 100, right = 280, bottom = 200)

        val selected = NearestNodeSelector.nearestTo(
            anchor = anchor,
            candidates = listOf(far, near)
        ) { it.second }

        assertEquals("near", selected?.first)
    }

    @Test
    fun nearestTo_supportsSequenceWithoutListAllocation() {
        val anchor = NodeBounds(left = 100, top = 100, right = 200, bottom = 200)
        val far = "far" to NodeBounds(left = 900, top = 900, right = 1000, bottom = 1000)
        val near = "near" to NodeBounds(left = 180, top = 100, right = 280, bottom = 200)

        val selected = NearestNodeSelector.nearestTo(
            anchor = anchor,
            candidates = sequenceOf(far, near)
        ) { it.second }

        assertEquals("near", selected?.first)
    }

    @Test
    fun nearestTo_returnsNullWhenThereAreNoCandidates() {
        val selected = NearestNodeSelector.nearestTo(
            anchor = NodeBounds(left = 0, top = 0, right = 10, bottom = 10),
            candidates = emptyList<Pair<String, NodeBounds>>()
        ) { it.second }

        assertNull(selected)
    }
}
