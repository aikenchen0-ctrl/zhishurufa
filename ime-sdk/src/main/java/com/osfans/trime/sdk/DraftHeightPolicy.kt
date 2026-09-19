package com.osfans.trime.sdk

/** 草稿详情区的拖拽结果；折叠时详情高度为零，保留紧凑预览行。 */
data class DraftHeight(val expanded: Boolean, val bodyHeight: Int)

object DraftHeightPolicy {
    const val COLLAPSE_THRESHOLD = 24
    const val MIN_EXPANDED_HEIGHT = 48

    fun resolve(
        startHeight: Int,
        distance: Int,
        maximum: Int,
        minimumExpandedHeight: Int = MIN_EXPANDED_HEIGHT,
        collapseThreshold: Int = COLLAPSE_THRESHOLD,
    ): DraftHeight {
        require(maximum >= minimumExpandedHeight) { "草稿最大高度无效" }
        val raw = startHeight + distance
        return if (raw <= collapseThreshold) {
            DraftHeight(expanded = false, bodyHeight = 0)
        } else {
            DraftHeight(expanded = true, bodyHeight = raw.coerceIn(minimumExpandedHeight, maximum))
        }
    }
}
