package com.osfans.trime.sdk

/** 将草稿详情高度映射到键盘按键区高度，保持主题基准并限制极端尺寸。 */
object DraftKeyboardHeightPolicy {
    fun resolve(
        baseHeight: Int,
        bodyHeight: Int,
        normalBodyHeight: Int,
        minimumHeight: Int,
        maximumExtraHeight: Int,
    ): Int {
        require(baseHeight > 0 && normalBodyHeight > 0 && minimumHeight > 0 && maximumExtraHeight >= 0)
        val ratio = (bodyHeight.coerceAtLeast(0).toFloat() / normalBodyHeight).coerceAtLeast(0f)
        val target = minimumHeight + ((baseHeight - minimumHeight) * ratio).toInt()
        return target.coerceIn(minimumHeight, baseHeight + maximumExtraHeight)
    }
}

