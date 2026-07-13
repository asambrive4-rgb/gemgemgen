package com.example.gemgemgen.ui

enum class MainTab(val label: String) {
    AUTOMATION("자동화"),
    ANALYSIS("분석 생성"),
    WILDCARD("와일드카드");

    /** 순환 다음 탭. [order]에 없으면 첫 탭. */
    fun nextIn(order: List<MainTab>): MainTab {
        if (order.isEmpty()) return this
        val index = order.indexOf(this).takeIf { it >= 0 } ?: 0
        return order[(index + 1) % order.size]
    }

    /** 순환 이전 탭. [order]에 없으면 첫 탭. */
    fun previousIn(order: List<MainTab>): MainTab {
        if (order.isEmpty()) return this
        val index = order.indexOf(this).takeIf { it >= 0 } ?: 0
        return order[(index - 1 + order.size) % order.size]
    }
}

