package com.osfans.trime.sdk.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import com.osfans.trime.R
import com.osfans.trime.sdk.DraftChange
import com.osfans.trime.sdk.DraftController
import com.osfans.trime.sdk.DraftItem
import com.osfans.trime.sdk.DraftState
import com.osfans.trime.sdk.DraftSurface
import com.osfans.trime.sdk.DraftHeightPolicy
import com.osfans.trime.sdk.EditorCommand
import com.osfans.trime.sdk.TrimeSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 可选的普通 Android 控件。展示与编辑分离；不申请悬浮窗权限，不自动生成或发送消息。 */
class DraftPanelView @JvmOverloads constructor(
    context: Context,
    private val controller: DraftController = TrimeSdk.drafts,
    private val surface: DraftSurface = DraftSurface.HOST,
) : LinearLayout(context) {
    private var token: Long? = null
    private var subscriptions: CoroutineScope? = null
    private var expanded = surface == DraftSurface.HOST
    private val normalBodyHeight = dp(60)
    private var bodyHeight = normalBodyHeight
    private var foreground = Color.rgb(45, 57, 61)
    private var backgroundColor = Color.rgb(241, 244, 245)
    private var displayed: DraftItem? = null
    private val compactRowHeight = if (surface == DraftSurface.IME) dp(72) else dp(48)
    /** 当前详情高度、常规详情高度；输入法宿主可据此调整键盘按键区高度。 */
    var onBodyHeightChanged: ((Int, Int) -> Unit)? = null
    private val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val detail = LinearLayout(context).apply { orientation = VERTICAL }
    private val preview = TextView(context).apply {
        textSize = 15f
        maxLines = if (surface == DraftSurface.IME) 3 else 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, dp(4), 0)
        isClickable = true
        setOnClickListener { toggleExpanded() }
    }
    private val title = TextView(context).apply {
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val body = TextView(context).apply {
        textSize = 16f
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }
    private val bodyScroll = ScrollView(context).apply { addView(body) }
    private val resize = icon(R.drawable.ic_baseline_expand_less_24, R.string.trime_draft_expand) { toggleExpanded() }
    private val retry = icon(R.drawable.ic_baseline_refresh_reversed_24, R.string.trime_draft_retry) {
        displayed?.let { if (!controller.requestRegeneration(it)) feedback(R.string.trime_draft_retry_unavailable) }
    }
    private val bind = icon(R.drawable.ic_baseline_text_fields_24, R.string.trime_draft_bind) {
        val item = displayed ?: return@icon
        TrimeSdk.setEditorContextEnabled(true)
        val snapshot = TrimeSdk.refreshEditorSnapshot()
        if (snapshot == null || !controller.bind(item.id, snapshot)) feedback(R.string.trime_draft_no_editor)
        render(controller.state.value)
    }
    private val applyButton = icon(R.drawable.ic_baseline_check_circle_24, R.string.trime_draft_apply) {
        val item = displayed ?: return@icon
        val expected = controller.binding(item.id)
        if (expected != null && controller.state.value.selected == item &&
            TrimeSdk.requestEditorCommand(EditorCommand.CommitText(item.text), expected)) {
            controller.remove(item.id)
        } else feedback(R.string.trime_draft_stale)
    }
    private val previous = icon(R.drawable.ic_baseline_arrow_left_24, R.string.trime_draft_previous) {
        val state = controller.state.value
        if (state.items.isNotEmpty()) controller.select(state.items[(state.items.indexOf(state.selected) + state.items.size - 1) % state.items.size].id)
    }
    private val next = icon(R.drawable.ic_baseline_arrow_right_24, R.string.trime_draft_next) { controller.next() }
    private val clear = icon(R.drawable.ic_baseline_delete_24, R.string.trime_draft_clear) { displayed?.let { controller.remove(it.id) } }

    init {
        id = R.id.trime_draft_panel
        orientation = VERTICAL
        visibility = GONE
        isFocusable = false
        row.addView(resize, LayoutParams(dp(40), compactRowHeight))
        row.addView(preview, LayoutParams(0, compactRowHeight, 1f))
        row.addView(retry, LayoutParams(dp(44), compactRowHeight))
        row.addView(bind, LayoutParams(dp(44), compactRowHeight))
        row.addView(applyButton, LayoutParams(dp(44), compactRowHeight))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, compactRowHeight))
        val navigation = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(title, LayoutParams(0, dp(40), 1f))
            addView(previous, LayoutParams(dp(44), dp(40)))
            addView(next, LayoutParams(dp(44), dp(40)))
            addView(clear, LayoutParams(dp(44), dp(40)))
        }
        detail.addView(bodyScroll, LayoutParams(LayoutParams.MATCH_PARENT, bodyHeight))
        detail.addView(navigation, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        addView(detail)
        var startY = 0f
        var startHeight = 0
        var dragged = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        resize.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startHeight = if (expanded) bodyHeight else 0
                    dragged = false
                    view.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val distance = startY - event.rawY
                    if (abs(distance) > slop) dragged = true
                    if (dragged) {
                        val resolved = DraftHeightPolicy.resolve(
                            startHeight = startHeight,
                            distance = distance.roundToInt(),
                            maximum = maximumBodyHeight(),
                            minimumExpandedHeight = dp(DraftHeightPolicy.MIN_EXPANDED_HEIGHT),
                            collapseThreshold = dp(DraftHeightPolicy.COLLAPSE_THRESHOLD),
                        )
                        expanded = resolved.expanded
                        bodyHeight = resolved.bodyHeight
                        render(controller.state.value)
                    }
                }
                MotionEvent.ACTION_UP -> { if (!dragged) view.performClick(); view.parent.requestDisallowInterceptTouchEvent(false) }
                MotionEvent.ACTION_CANCEL -> view.parent.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
        refreshColors(backgroundColor, foreground)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        token = controller.attach(surface)
        subscriptions = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch { controller.state.collect { render(it) } }
            scope.launch { TrimeSdk.editorSnapshot.collect { render(controller.state.value) } }
        }
    }

    override fun onDetachedFromWindow() {
        subscriptions?.cancel()
        subscriptions = null
        token?.let(controller::detach)
        token = null
        super.onDetachedFromWindow()
    }

    /** IME 配色变化时调用；宿主可使用自己的配色，不依赖全局 ThemeManager。 */
    fun refreshColors(background: Int, text: Int) {
        backgroundColor = background
        foreground = text
        setBackgroundColor(background)
        preview.setTextColor(text)
        title.setTextColor(text)
        body.setTextColor(text)
        listOf(resize, bind, previous, next, clear).forEach { it.imageTintList = ColorStateList.valueOf(text) }
        retry.imageTintList = ColorStateList.valueOf(Color.rgb(183, 43, 43))
        applyButton.imageTintList = ColorStateList.valueOf(Color.rgb(0, 119, 73))
        render(controller.state.value)
    }

    private fun render(state: DraftState) {
        displayed = state.selected
        val item = displayed
        visibility = if (token != null && state.owner == token && item != null) VISIBLE else GONE
        if (item == null) {
            onBodyHeightChanged?.invoke(normalBodyHeight, normalBodyHeight)
            return
        }
        preview.text = item.text
        preview.contentDescription = context.getString(R.string.trime_draft_preview)
        body.text = styled(item)
        title.text = "${state.items.indexOf(item) + 1}/${state.items.size}  ${item.title}"
        previous.isEnabled = state.items.size > 1
        next.isEnabled = state.items.size > 1
        retry.isEnabled = state.canRegenerate
        val expected = controller.binding(item.id)
        val current = TrimeSdk.editorSnapshot.value
        applyButton.isEnabled = item.text.isNotEmpty() && expected != null && current != null && expected.sessionId == current.sessionId
        applyButton.alpha = if (applyButton.isEnabled) 1f else 0.35f
        detail.visibility = if (expanded) VISIBLE else GONE
        val target = bodyHeight.coerceAtMost(maximumBodyHeight())
        if (bodyScroll.layoutParams.height != target) bodyScroll.layoutParams = bodyScroll.layoutParams.apply { height = target }
        onBodyHeightChanged?.invoke(target, normalBodyHeight)
        resize.setImageResource(if (expanded) R.drawable.ic_baseline_expand_more_24 else R.drawable.ic_baseline_expand_less_24)
        resize.contentDescription = context.getString(if (expanded) R.string.trime_draft_collapse else R.string.trime_draft_expand)
        TooltipCompat.setTooltipText(resize, resize.contentDescription)
    }

    private fun styled(item: DraftItem): CharSequence = SpannableStringBuilder().apply {
        item.segments.forEach { segment ->
            val start = length
            append(segment.text)
            if (segment.change != DraftChange.UNCHANGED) {
                val removed = segment.change == DraftChange.REMOVED
                val background = if (removed) Color.rgb(255, 226, 226) else Color.rgb(214, 245, 225)
                val text = if (removed) Color.rgb(139, 24, 24) else Color.rgb(0, 89, 47)
                setSpan(BackgroundColorSpan(background), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(text), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(if (removed) StrikethroughSpan() else UnderlineSpan(), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    private fun toggleExpanded() {
        expanded = !expanded
        if (expanded) {
            bodyHeight = bodyHeight.coerceAtLeast(dp(DraftHeightPolicy.MIN_EXPANDED_HEIGHT))
                .coerceAtMost(maximumBodyHeight())
        }
        render(controller.state.value)
    }
    private fun maximumBodyHeight() = minOf(dp(160), (resources.displayMetrics.heightPixels * 0.18f).toInt()).coerceAtLeast(dp(48))
    private fun feedback(message: Int) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun icon(drawable: Int, label: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(drawable)
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = context.getString(label)
        TooltipCompat.setTooltipText(this, contentDescription)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { action() }
    }
}
