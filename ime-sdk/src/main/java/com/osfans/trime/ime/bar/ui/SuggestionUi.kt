package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.R
import com.osfans.trime.sdk.SuggestionItem
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.lParams

/** 候选栏中的短建议视图，与 Rime 候选列表分开避免索引混淆。 */
class SuggestionUi(
    override val ctx: Context,
    private val scope: ThemeScope,
    private val onClick: (SuggestionItem) -> Unit,
) : Ui {
    private val theme: Theme get() = scope.theme
    private val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private var items: List<SuggestionItem> = emptyList()

    override val root = HorizontalScrollView(ctx).apply {
        id = R.id.suggestion_view
        isHorizontalScrollBarEnabled = false
        overScrollMode = HorizontalScrollView.OVER_SCROLL_NEVER
        visibility = android.view.View.GONE
        addView(row, lParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun setItems(value: List<SuggestionItem>) {
        items = value
        row.removeAllViews()
        value.forEach { item ->
            row.addView(createButton(item), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                marginStart = ctx.dp(theme.generalStyle.candidateSpacing).toInt()
                marginEnd = ctx.dp(theme.generalStyle.candidateSpacing).toInt()
            })
        }
        root.visibility = if (value.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    fun refreshColors() {
        if (items.isNotEmpty()) setItems(items)
    }

    private fun createButton(item: SuggestionItem): TextView = TextView(ctx).apply {
        text = item.text
        textSize = theme.generalStyle.candidateTextSize
        setTextColor(scope.colors.candidateTextColor)
        gravity = Gravity.CENTER
        includeFontPadding = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = ctx.dp(220)
        minWidth = ctx.dp(48)
        setPadding(ctx.dp(10), 0, ctx.dp(10), 0)
        contentDescription = item.source?.takeIf { it.isNotBlank() }?.let { "${item.text}，$it" } ?: item.text
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), background(scope.colors.hilitedCandidateBackColor))
            addState(intArrayOf(), background(scope.colors.candidateBackground))
        }
        setOnClickListener { onClick(item) }
    }

    private fun background(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = ctx.dp(theme.generalStyle.candidateCornerRadius)
        val border = ctx.dp(theme.generalStyle.candidateBorder)
        if (border > 0) setStroke(border, scope.colors.candidateTextColor)
    }
}
