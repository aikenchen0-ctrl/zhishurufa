// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.Point
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.osfans.trime.R
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import com.osfans.trime.util.isLandscape
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.systemservices.windowManager
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class KeyboardWindow(di: DI) :
    BoardWindow.NoBarBoardWindow(di),
    ResidentWindow,
    InputBroadcastReceiver {
    private val service: TrimeInputMethodService by instance()
    private val theme: Theme by instance()
    private val rime: RimeSession by instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by instance()
    private val popup: PopupDelegate by instance()
    private val enterKeyDisplay: EnterKeyDisplayDelegate by instance()

    private val cursorCapsMode: Int
        get() =
            service.currentInputEditorInfo.run {
                if (inputType != InputType.TYPE_NULL) {
                    service.currentInputConnection?.getCursorCapsMode(inputType) ?: 0
                } else {
                    0
                }
            }

    private val _currentKeyboardHeight =
        MutableSharedFlow<Int>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val currentKeyboardHeight = _currentKeyboardHeight.asSharedFlow()

    private lateinit var keyboardView: FrameLayout

    companion object : ResidentWindow.Key {
        lateinit var currentKeyboard: Keyboard
    }

    override val key: ResidentWindow.Key
        get() = KeyboardWindow

    private val presetKeyboardIds = theme.presetKeyboards.keys.toList()
    private var currentKeyboardId = ""
    private var lastKeyboardId = ""
    private var lastLockKeyboardId = ""
    private val inputFieldSession get() = service.inputFieldSession
    private val viewGeneration = service.inputViewGeneration
    private var layoutSchemaId = ""
    private val currentField get() = service.currentInputField
    private val cachedKeyboards = mutableMapOf<String, Pair<Keyboard, KeyboardView>>()
    private val activeKeyboard: Keyboard? get() = cachedKeyboards[currentKeyboardId]?.first
    private val currentKeyboardView: KeyboardView? get() = cachedKeyboards[currentKeyboardId]?.second

    private val keyboardActionListener = commonKeyboardActionListener.listener

    private var lastIsPortrait: Boolean? = null
    private var containerWidth: Int = 0
    private var allowedWidth: Int = 0

    private val onKeyboardViewLayoutChangeListener =
        View.OnLayoutChangeListener { v, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width > 0 && allowedWidth != width) {
                val isPortrait = !context.resources.configuration.isLandscape()
                lastIsPortrait = isPortrait
                containerWidth = width
                allowedWidth = width
                v.post { refreshKeyboards() }
            }
        }

    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        keyboardView.addOnLayoutChangeListener(onKeyboardViewLayoutChangeListener)
        layoutSchemaId = rime.run { statusCached }.schemaId
        val remembered = service.inputFieldKeyboardId?.takeIf { it in presetKeyboardIds }
        attachKeyboard(remembered ?: evalKeyboard(".default"), synchronizeMode = false)
        return keyboardView
    }

    private fun detachCurrentView() {
        currentKeyboardView?.also {
            it.onDetach()
            keyboardView.removeView(it)
        }
        activeKeyboard?.lastAsciiMode = rime.run { statusCached }.isAsciiMode
    }

    /** 计算键盘可用宽度：优先使用已测量的容器宽度，否则回退到系统窗口测量。 */
    private fun computeAllowedWidth(): Int {
        val isPortrait = !context.resources.configuration.isLandscape()

        if (containerWidth > 0 && lastIsPortrait == isPortrait) {
            return containerWidth
        }

        val padding = theme.generalStyle.run {
            if (context.isLandscapeMode()) keyboardPaddingLand else keyboardPadding
        }

        val safeWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = context.windowManager.maximumWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val displayWidth = context.resources.displayMetrics.widthPixels
            val windowWidth = windowMetrics.bounds.width() - insets.left - insets.right
            if (windowWidth < displayWidth - context.dp(1)) displayWidth else windowWidth
        } else {
            @Suppress("DEPRECATION")
            val size = Point()
            @Suppress("DEPRECATION")
            context.windowManager.defaultDisplay.getSize(size)
            size.x
        }

        val width = safeWidth - 2 * context.dp(padding)
        allowedWidth = width
        return width
    }

    private fun selectKeyboardConfig(name: String): TextKeyboard? {
        val config = theme.presetKeyboards[name] ?: theme.presetKeyboards["default"]
        val importPreset = config?.importPreset
        if (!importPreset.isNullOrEmpty()) {
            return selectKeyboardConfig(importPreset)
        }
        return config
    }

    private fun attachKeyboard(target: String, synchronizeMode: Boolean = true) {
        currentKeyboardId = target
        lastKeyboardId = target

        val config = selectKeyboardConfig(target)
        val keyboard = activeKeyboard ?: Keyboard(context, theme, computeAllowedWidth(), config)
        val view = currentKeyboardView ?: KeyboardView(context, theme, keyboard, popup, service, keyboardActionListener, enterKeyDisplay)

        if (activeKeyboard == null) {
            cachedKeyboards[target] = keyboard to view
            keyboard.lastAsciiMode = keyboard.asciiMode
        }

        keyboard.also {
            runBlocking { _currentKeyboardHeight.emit(it.keyboardHeight) }
            if (it.isLock) lastLockKeyboardId = target
            dispatchCapsState(it::setShifted)

            val currentMode = rime.run { statusCached }.isAsciiMode
            val targetMode = if (it.resetAsciiMode) it.asciiMode else it.lastAsciiMode

            if (synchronizeMode && !it.preserveAsciiMode && currentMode != targetMode) {
                service.postRimeJob {
                    commitComposition()
                    setRuntimeOption("ascii_mode", targetMode)
                }
            }

            currentKeyboard = it
        }

        view.let {
            keyboardView.apply {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                add(it, lParams(matchParent, matchParent))
            }
        }
    }

    private fun smartMatchKeyboard(): String {
        // 主题的布局中包含方案id，直接采用
        val currentSchema = rime.run { statusCached }.schemaId
        if (presetKeyboardIds.contains(currentSchema)) {
            return currentSchema
        }
        val alphabet = rime.run { schemaCached }.alphabet
        val layout =
            when {
                alphabet.all { it.isLetter() } -> "qwerty"

                // 包含 26 个字母
                alphabet.all { it.isLetter() || ",./;".any(it::equals) } -> "qwerty_"

                // 包含 26 个字母和,./;
                alphabet.all { it.isLetterOrDigit() } -> "qwerty0"

                // 包含 26 个字母和数字键
                else -> "default"
            }
        return if (presetKeyboardIds.contains(layout)) layout else "default"
    }

    private fun evalKeyboard(id: String): String {
        val currentIdx = presetKeyboardIds.indexOfFirst { currentKeyboardId == it }
        val dot =
            when (id) {
                ".default" -> smartMatchKeyboard()

                ".prior" -> presetKeyboardIds.getOrNull(currentIdx - 1) ?: currentKeyboardId

                ".next" -> presetKeyboardIds.getOrNull(currentIdx + 1) ?: currentKeyboardId

                ".last" -> lastKeyboardId

                ".last_lock" -> lastLockKeyboardId

                ".ascii" -> {
                    var ascii = activeKeyboard?.asciiKeyboard
                    if (ascii.isNullOrEmpty()) {
                        ascii = lastLockKeyboardId
                    }
                    if (presetKeyboardIds.contains(ascii)) ascii else currentKeyboardId
                }

                else -> {
                    id.ifEmpty {
                        service.inputFieldKeyboardId?.takeIf { it in presetKeyboardIds }
                            ?: if (activeKeyboard?.isLock == true) currentKeyboardId else lastLockKeyboardId
                    }
                }
            }
        var final = dot.ifEmpty { smartMatchKeyboard() }

        // 切换到横屏布局
        if (service.isLandscapeMode()) {
            val landscape =
                theme.presetKeyboards[final]?.landscapeKeyboard ?: ""
            if (landscape.isNotEmpty() && presetKeyboardIds.contains(landscape)) final = landscape
        }
        return final
    }

    fun switchKeyboard(to: String, synchronizeMode: Boolean = true) {
        val target = evalKeyboard(to)
        ContextCompat.getMainExecutor(service).execute {
            if (viewGeneration == service.inputViewGeneration) showKeyboard(target, synchronizeMode)
        }
        Timber.d("Switched to keyboard: $target")
    }

    private fun showKeyboard(target: String, synchronizeMode: Boolean) {
        service.inputFieldKeyboardId = target
        if (target == currentKeyboardId && target in cachedKeyboards) return
        detachCurrentView()
        attachKeyboard(target, synchronizeMode)
    }

    fun refreshKeyboards(isAll: Boolean = false) {
        if (viewGeneration != service.inputViewGeneration) return
        val id = currentKeyboardId.ifEmpty { return }
        detachCurrentView()
        if (isAll) {
            cachedKeyboards.clear()
        } else {
            cachedKeyboards.remove(id)
        }
        attachKeyboard(id, synchronizeMode = false)
    }

    /** Repaints the keyboard after a color-scheme switch; keys re-resolve their colors. */
    override fun refreshColors() {
        currentKeyboardView?.invalidateAllKeys()
    }

    override fun onStartInput(info: EditorInfo) {
        val field = InputFieldKeyboard.resolve(info.inputType, info.imeOptions)
        // 字段转换与按键共享顺序队列，不能用排队前的旧模式决定是否恢复。
        service.postRimeJob {
            val status = statusCached
            val transition = withContext(Dispatchers.Main.immediate) {
                if (viewGeneration != service.inputViewGeneration) return@withContext null
                service.currentInputField = field
                val schemaChanged = layoutSchemaId != status.schemaId
                layoutSchemaId = status.schemaId
                val current = TextInputState(
                    keyboardId = currentKeyboardId.ifEmpty { "default" },
                    schemaId = status.schemaId,
                    asciiMode = status.isAsciiMode,
                )
                val restored = inputFieldSession.enter(field, current)
                val target = when {
                    field.isTemporary -> temporaryFieldLayout()
                    restored != null -> restored.keyboardId.takeIf { it in presetKeyboardIds } ?: smartMatchKeyboard()
                    schemaChanged -> smartMatchKeyboard().let { id ->
                        if (status.isAsciiMode) selectKeyboardConfig(id)?.asciiKeyboard?.takeIf { it in presetKeyboardIds } ?: id else id
                    }
                    else -> evalKeyboard("")
                }
                val resolvedTarget = evalKeyboard(target)
                showKeyboard(resolvedTarget, synchronizeMode = false)
                val mode = when {
                    field.isTemporary -> true
                    restored != null -> restored.asciiMode
                    theme.generalStyle.resetAsciiModeOnFocusChange -> activeKeyboard?.let {
                        if (it.resetAsciiMode) it.asciiMode else it.lastAsciiMode
                    }
                    else -> null
                }
                restored to mode
            } ?: return@postRimeJob
            val (restored, targetMode) = transition
            if (restored != null && schemaCached.schemaId != restored.schemaId && restored.schemaId.isNotEmpty()) {
                clearComposition()
                selectSchema(restored.schemaId)
            }
            if (targetMode != null && getRuntimeOption("ascii_mode") != targetMode) {
                setRuntimeOption("ascii_mode", targetMode)
            }
        }
    }

    private fun temporaryFieldLayout(): String {
        val defaultId = smartMatchKeyboard()
        val asciiId = selectKeyboardConfig(defaultId)?.asciiKeyboard.orEmpty()
        val fallback = asciiId.takeIf { it in presetKeyboardIds } ?: defaultId
        return currentField.layout(presetKeyboardIds.toSet(), fallback)
    }

    private fun dispatchCapsState(setShift: (Boolean, Boolean) -> Unit) {
        val status = rime.run { statusCached }
        // TODO: 启用自动首句大写后，点击方向键时，保持Shift锁定状态功能将无法生效
        if (theme.generalStyle.autoCaps && status.isAsciiMode && currentKeyboardView?.isCapsOn == false) {
            setShift(false, cursorCapsMode != 0)
        }
    }

    override fun onKeyAppearanceUpdate(composing: Boolean, menu: Boolean, paging: Boolean) {
        if (!rime.run { statusCached }.isAsciiMode) {
            activeKeyboard?.appearanceStateKeys?.forEach { key ->
                currentKeyboardView?.invalidateKeyByIndex(key.index)
            }
        }
    }

    override fun onSelectionUpdate(
        start: Int,
        end: Int,
    ) {
        dispatchCapsState { on, shifted ->
            activeKeyboard?.setShifted(on, shifted)?.let { if (it) currentKeyboardView?.invalidateAllKeys() }
        }
    }

    override fun onRimeSchemaUpdated(schema: SchemaItem) {
        layoutSchemaId = schema.id
        if (currentField.isTemporary) {
            switchKeyboard(temporaryFieldLayout(), synchronizeMode = false)
        } else {
            // 方案事件携带的 id 比 statusCached 更及时，避免缓存落后时回退到锁定的 26 键。
            val target = schema.id.takeIf { it in presetKeyboardIds } ?: smartMatchKeyboard()
            switchKeyboard(target, synchronizeMode = false)
        }
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        val option = value.option
        // 事件可能落后于最新状态；跟随引擎切换视图时不得反向回写模式。
        if (option == "ascii_mode" && value.value != rime.run { statusCached }.isAsciiMode) return
        when {
            option == "ascii_mode" && currentField.isTemporary -> Unit
            option == "ascii_mode" && value.value && !activeKeyboard?.asciiKeyboard.isNullOrEmpty() -> switchKeyboard(".ascii", synchronizeMode = false)
            option == "ascii_mode" && !value.value && currentKeyboardId == "letter" -> {
                val remembered = service.inputFieldKeyboardId
                    ?.takeIf { it in presetKeyboardIds && it != "letter" }
                switchKeyboard(remembered ?: smartMatchKeyboard(), synchronizeMode = false)
            }
            option.startsWith("_keyboard_") -> {
                val target = option.removePrefix("_keyboard_")
                if (target.isNotEmpty()) {
                    switchKeyboard(target)
                }
            }

            option.startsWith("_key_") -> {
                val what = option.removePrefix("_key_")
                if (what.isNotEmpty() && value.value) {
                    commonKeyboardActionListener
                        .listener
                        .onAction(KeyActionManager.getAction(what))
                }
            }
        }
        currentKeyboardView?.invalidateAllKeys()
    }

    override fun onAttached() {
    }

    override fun onDetached() {
        currentKeyboardView?.onDetach()
    }
}
