// 역할: Iterable 및 Sequence 스트림 기반으로 GC 힙 객체 할당 없이 최단 거리 접근성 노드를 선택합니다.
package com.example.gemgemgen.automation.android

data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int
        get() = (left + right) / 2

    val centerY: Int
        get() = (top + bottom) / 2
}

object NearestNodeSelector {
    fun <T> nearestTo(
        anchor: NodeBounds,
        candidates: Iterable<T>,
        boundsOf: (T) -> NodeBounds
    ): T? {
        return candidates.minByOrNull { candidate ->
            squaredDistance(anchor, boundsOf(candidate))
        }
    }

    fun <T> nearestTo(
        anchor: NodeBounds,
        candidates: Sequence<T>,
        boundsOf: (T) -> NodeBounds
    ): T? {
        return candidates.minByOrNull { candidate ->
            squaredDistance(anchor, boundsOf(candidate))
        }
    }

    private fun squaredDistance(first: NodeBounds, second: NodeBounds): Long {
        val dx = first.centerX.toLong() - second.centerX.toLong()
        val dy = first.centerY.toLong() - second.centerY.toLong()
        return dx * dx + dy * dy
    }
}

