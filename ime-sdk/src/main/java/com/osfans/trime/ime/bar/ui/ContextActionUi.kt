package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.osfans.trime.R
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.sdk.AiDraftOperation
import com.osfans.trime.sdk.EditorSnapshot
import com.osfans.trime.sdk.TrimeSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import splitties.dimensions.dp

/** 输入法窗口内的光标上下文操作栏，不使用悬浮窗或无障碍权限。 */
class ContextActionUi(
    context: Context,
    private val themeScope: ThemeScope,
    private val onAction: (AiDraftOperation) -> Unit,
) : HorizontalScrollView(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private var collector: CoroutineScope? = null
    private var actions: List<AiDraftOperation> = emptyList()

    init {
        id = R.id.ai_context_actions
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        minimumHeight = context.dp(40)
        visibility = View.GONE
        addView(row, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        collector?.cancel()
        collector = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                TrimeSdk.editorSnapshot.collect { snapshot ->
                    setSnapshot(snapshot)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        collector?.cancel()
        collector = null
        super.onDetachedFromWindow()
    }

    fun refreshColors() {
        setActions(actions)
    }

    private fun setSnapshot(snapshot: EditorSnapshot?) {
        setActions(snapshot?.let(AiDraftOperation::contextActions).orEmpty())
    }

    private fun setActions(value: List<AiDraftOperation>) {
        actions = value
        row.removeAllViews()
        value.forEach { action ->
            row.addView(button(action), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                marginStart = context.dp(4)
                marginEnd = context.dp(4)
            })
        }
        visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun button(action: AiDraftOperation): TextView = TextView(context).apply {
        text = label(action)
        textSize = 14f
        gravity = Gravity.CENTER
        includeFontPadding = false
        isSingleLine = true
        minWidth = context.dp(64)
        setPadding(context.dp(12), 0, context.dp(12), 0)
        setTextColor(themeScope.colors.candidateTextColor)
        contentDescription = text
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), background(themeScope.colors.hilitedCandidateBackColor))
            addState(intArrayOf(), background(themeScope.colors.candidateBackground))
        }
        setOnClickListener { onAction(action) }
    }

    private fun label(action: AiDraftOperation): String = context.getString(
        when (action) {
            AiDraftOperation.REPLY -> R.string.ai_action_reply
            AiDraftOperation.REWRITE -> R.string.ai_action_rewrite
            AiDraftOperation.POLISH -> R.string.ai_action_polish
            AiDraftOperation.CONTINUE -> R.string.ai_action_continue
            AiDraftOperation.SUGGEST -> R.string.ai_action_suggest
        },
    )

    private fun background(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(8).toFloat()
        // 主题边框键允许引用图片，按钮边框必须使用纯色键避免资源类型崩溃。
        setStroke(context.dp(1), themeScope.colors.candidateTextColor)
    }
}
