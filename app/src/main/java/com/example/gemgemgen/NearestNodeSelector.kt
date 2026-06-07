package com.example.gemgemgen

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
        candidates: List<T>,
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
