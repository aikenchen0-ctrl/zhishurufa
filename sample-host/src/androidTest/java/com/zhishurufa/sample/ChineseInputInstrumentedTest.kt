package com.zhishurufa.sample

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeConfig
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.ime.keyboard.Key
import com.osfans.trime.ime.keyboard.KeyBehavior
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.sdk.TrimeSdk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChineseInputInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val sessionName = "sdk-instrumentation"

    @Before
    fun configureTestInputMethod() {
        if (InstrumentationRegistry.getArguments().getString("configureIme") == "true") {
            val component = TrimeSdk.serviceComponent(instrumentation.targetContext.packageName)
            device.executeShellCommand("ime enable $component")
            device.executeShellCommand("ime set $component")
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        }
    }

    @Test
    fun delayedStatusRestorePreservesCompositionDuringTyping() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(R.id.input_test)
                editor.setText("")
                editor.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            waitForKeyboard()
            val session = RimeDaemon.createSession(sessionName)
            try {
                awaitSchemes(session)
                session.runOnReady {
                    clearComposition()
                    selectSchema("luna_pinyin_simp")
                    setRuntimeOption("ascii_mode", false)
                    repeat(4) {
                        clearComposition()
                        setRuntimeOption("ascii_mode", true)
                        setRuntimeOption("ascii_mode", false)
                        "nihao".forEach {
                            processKey(it.code, 0u, true)
                            delay(250)
                        }
                        assertEquals("nihao", getRawInput())
                        assertTrue(getCandidates(0, 20).any { it.text == "你好" })
                    }
                    clearComposition()
                }
            } finally {
                RimeDaemon.destroySession(sessionName)
            }
        }
    }

    @Test
    fun closingNativeConfigurationTwiceIsSafe() = runBlocking<Unit> {
        val session = RimeDaemon.createSession(sessionName)
        try {
            awaitSchemes(session)
            session.runOnReady {
                val config = RimeConfig.openSchema("luna_pinyin_simp")
                assertEquals("luna_pinyin_simp", config.getString("schema/schema_id"))
                config.close()
                config.close()
                assertThrows(IllegalStateException::class.java) { config.getString("schema/schema_id") }
                RimeConfig.openSchema("luna_pinyin_simp").use {
                    assertEquals("luna_pinyin_simp", it.getString("schema/schema_id"))
                }
            }
        } finally {
            RimeDaemon.destroySession(sessionName)
        }
    }

    @Test
    fun invalidCandidateRangesFailWithoutNativeCrash() = runBlocking<Unit> {
        val session = RimeDaemon.createSession(sessionName)
        try {
            awaitSchemes(session)
            session.runOnReady {
                for ((start, size) in listOf(-1 to 1, 0 to -1, 0 to 1025)) {
                    try {
                        getCandidates(start, size)
                        fail("非法候选范围未被拒绝")
                    } catch (_: IllegalArgumentException) {
                        // 预期在 JNI 之前拒绝非法参数。
                    }
                }
                assertEquals(0, getCandidates(0, 0).size)
            }
            // 直接入口也必须先校验，不能将负数传给 vector::reserve。
            assertThrows(IllegalArgumentException::class.java) { Rime.getRimeCandidates(0, -1) }
        } finally {
            RimeDaemon.destroySession(sessionName)
        }
    }

    @Test
    fun bundledSchemesProduceAndCommitChineseCandidates() = runBlocking<Unit> {
        assertTrue(TrimeSdk.isInitialized)
        val session = RimeDaemon.createSession(sessionName)
        try {
            awaitSchemes(session)
            withTimeout(180_000) {
                session.runOnReady {
                    val samples = listOf(
                        Triple("luna_pinyin_simp", "nihao", "你好"),
                        Triple("pinyin_t9", "64426", "你好"),
                        Triple("pinyin_t9", "94664486", "中国"),
                        Triple("pinyin_t9", "64'426", "你好"),
                        Triple("double_pinyin", "nihk", "你好"),
                        Triple("double_pinyin_flypy", "nihc", "你好"),
                        Triple("double_pinyin_mspy", "bzj;", "北京"),
                        Triple("double_pinyin_abc", "nihk", "你好"),
                        Triple("double_pinyin_sogou", "bzj;", "北京"),
                        Triple("double_pinyin_ziguang", "bkj;", "北京"),
                        Triple("wubi86", "wqvb", "你好"),
                        Triple("stroke", "h", "一"),
                    )
                    val enabled = selectedSchemata().map { it.id }
                    assertTrue("缺少内置方案：$enabled", enabled.containsAll(samples.map { it.first }.distinct()))
                    for ((schema, code, expected) in samples) {
                        clearComposition()
                        assertTrue("方案不可选择：$schema", selectSchema(schema))
                        setRuntimeOption("ascii_mode", false)
                        code.forEach { processKey(it.code, 0u, true) }
                        val candidates = getCandidates(0, 100)
                        val index = candidates.indexOfFirst { it.text == expected }
                        assertTrue("$schema 输入 $code 未找到 $expected：${candidates.take(15).map { it.text }}", index >= 0)
                        val commit = async(start = CoroutineStart.UNDISPATCHED) {
                            withTimeout(5_000) { messageFlow.first { it is RimeMessage.CommitTextMessage && !it.data.text.isNullOrEmpty() } }
                        }
                        assertTrue(selectCandidate(index, true))
                        assertEquals(expected, (commit.await() as RimeMessage.CommitTextMessage).data.text)
                    }
                    selectSchema("pinyin_t9")
                    setRuntimeOption("ascii_mode", false)
                    "64426".forEach { processKey(it.code, 0u, true) }
                    assertEquals("64426", getRawInput())
                    processKey(0xff08, 0u, true)
                    assertEquals("6442", getRawInput())
                    processKey('6'.code, 0u, true)
                    assertTrue(getCandidates(0, 100).any { it.text == "你好" })
                    clearComposition()
                    selectSchema("luna_pinyin_simp")
                }
            }
        } finally {
            RimeDaemon.destroySession(sessionName)
        }
    }

    @Test
    fun touchInputCommitsToHostAndKeyboardFitsRotation() = runBlocking<Unit> {
        assertTrue("请先在测试设备启用示例输入法", TrimeSdk.isEnabled())
        assertTrue("请先在测试设备选择示例输入法", TrimeSdk.isSelected())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<EditText>(R.id.input_test)
                editor.setText("")
                editor.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            val session = RimeDaemon.createSession(sessionName)
            try {
                awaitSchemes(session)
                withTimeout(180_000) {
                    session.runOnReady {
                        clearComposition()
                        selectSchema("luna_pinyin_simp")
                        setRuntimeOption("ascii_mode", false)
                    }
                }
                waitForKeyboard()
                for (keyCode in listOf(KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_O)) {
                    tapKey { it.getCode(KeyBehavior.CLICK) == keyCode }
                }
                withTimeout(10_000) {
                    while (session.runOnReady { getRawInput() } != "nihao") delay(50)
                }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_SPACE }
                withTimeout(10_000) {
                    while (editorText(scenario) != "你好") delay(50)
                }
                screenshot("portrait")
                tapKey { it.click?.select == "number" }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_NUMPAD_1 }
                withTimeout(10_000) {
                    while (editorText(scenario) != "你好1") delay(50)
                }
                tapKey { it.click?.select == ".default" }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_DEL }
                withTimeout(10_000) {
                    while (editorText(scenario) != "你好") delay(50)
                }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                assertTrue(device.wait(Until.gone(By.res("com.zhishurufa.sample", "keyboard_view")), 2_000) || device.displayWidth > device.displayHeight)
                waitForKeyboard()
                val bounds = device.findObject(By.res("com.zhishurufa.sample", "keyboard_view")).visibleBounds
                assertTrue(bounds.width() > 0 && bounds.height() > 0)
                assertTrue("横屏布局没有降低高度", bounds.height() < device.displayHeight * 0.55)
                assertTrue(bounds.right <= device.displayWidth && bounds.bottom <= device.displayHeight)
                assertEquals("你好", editorText(scenario))
                val editorBounds = device.findObject(By.res("com.zhishurufa.sample", "input_test")).visibleBounds
                assertTrue("横屏输入框被键盘遮住", editorBounds.top < bounds.top && editorBounds.height() > 0)
                scenario.onActivity { activity ->
                    val editor = activity.findViewById<EditText>(R.id.input_test)
                    val position = IntArray(2)
                    editor.getLocationOnScreen(position)
                    val line = editor.layout.getLineForOffset(editor.selectionStart.coerceAtLeast(0))
                    val textBottom = position[1] + editor.paddingTop + editor.layout.getLineBottom(line) - editor.scrollY
                    assertTrue("横屏光标所在文本行被遮住", textBottom <= bounds.top)
                }
                screenshot("landscape")
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            } finally {
                RimeDaemon.destroySession(sessionName)
            }
        }
    }

    @Test
    fun touchNineKeyAndEditingPreserveModeAndMoveCursor() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val editor = activity.findViewById<EditText>(R.id.input_test)
                editor.setText("")
                editor.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            val session = RimeDaemon.createSession(sessionName)
            try {
                awaitSchemes(session)
                session.runOnReady {
                    clearComposition()
                    selectSchema("pinyin_t9")
                    setRuntimeOption("ascii_mode", false)
                }
                waitForKeyboard()
                for (code in listOf(6, 4, 4, 2, 6)) {
                    tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_0 + code }
                }
                withTimeout(10_000) {
                    while (session.runOnReady { getRawInput() } != "64426") delay(50)
                }
                screenshot("nine-key")
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_DEL }
                assertEquals("6442", session.runOnReady { getRawInput() })
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_6 }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_SPACE }
                withTimeout(10_000) {
                    while (editorText(scenario) != "你好") delay(50)
                }
                assertTrue(device.wait(Until.hasObject(By.desc("编辑")), 5_000))
                device.findObject(By.desc("编辑")).click()
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_DPAD_LEFT }
                assertFalse(session.runOnReady { getRuntimeOption("ascii_mode") })
                withTimeout(5_000) {
                    var cursor = -1
                    do {
                        scenario.onActivity { cursor = it.findViewById<EditText>(R.id.input_test).selectionStart }
                        if (cursor != 1) delay(50)
                    } while (cursor != 1)
                }
                screenshot("editing")
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_DEL }
                withTimeout(5_000) {
                    while (editorText(scenario) != "好") delay(50)
                }
                tapKey { it.click?.select == ".default" }
            } finally {
                RimeDaemon.destroySession(sessionName)
            }
        }
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        device.waitForIdle()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "verification").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
    }

    @Test
    fun temporaryFieldsRestoreChineseAndEnglishPreferences() = runBlocking<Unit> {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            setEditorType(scenario, InputType.TYPE_CLASS_TEXT)
            val session = RimeDaemon.createSession(sessionName)
            try {
                awaitSchemes(session)
                session.runOnReady {
                    clearComposition()
                    selectSchema("pinyin_t9")
                    setRuntimeOption("ascii_mode", false)
                }
                awaitKeyboardMode(session, ascii = false) { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_6 }

                setEditorType(scenario, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                awaitKeyboardMode(session, ascii = true) { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_NUMPAD_1 }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_NUMPAD_1 }
                awaitEditorText(scenario, "1")

                setEditorType(scenario, InputType.TYPE_CLASS_PHONE)
                awaitKeyboardMode(session, ascii = true) { it.click?.commit == "+" }
                tapKey { it.click?.commit == "+" }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_1 }
                tapKey { it.click?.commit == "#" }
                awaitEditorText(scenario, "+1#")
                screenshot("phone")

                setEditorType(scenario, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                awaitKeyboardMode(session, ascii = true) { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_A }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_A }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_B }
                awaitEditorText(scenario, "ab")

                setEditorType(scenario, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                awaitKeyboardMode(session, ascii = true) { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_C }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_C }
                awaitEditorText(scenario, "c")
                assertEquals("", session.runOnReady { getRawInput() })

                setEditorType(scenario, InputType.TYPE_CLASS_TEXT)
                awaitKeyboardMode(session, ascii = false) { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_6 }
                assertEquals("pinyin_t9", session.runOnReady { selectedSchemaId() })
                for (code in listOf(6, 4, 4, 2, 6)) {
                    tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_0 + code }
                }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_SPACE }
                awaitEditorText(scenario, "你好")

                session.runOnReady { setRuntimeOption("ascii_mode", true) }
                awaitKeyboardMode(session, ascii = true) { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_A }
                setEditorType(scenario, InputType.TYPE_CLASS_PHONE)
                awaitKeyboardMode(session, ascii = true) { it.click?.commit == "+" }
                setEditorType(scenario, InputType.TYPE_CLASS_TEXT)
                awaitKeyboardMode(session, ascii = true) { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_A }
                tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_A }
                awaitEditorText(scenario, "a")
            } finally {
                RimeDaemon.destroySession(sessionName)
            }
        }
    }

    private fun setEditorType(scenario: ActivityScenario<MainActivity>, inputType: Int) {
        scenario.onActivity { activity ->
            val editor = activity.findViewById<EditText>(R.id.input_test)
            editor.setText("")
            editor.inputType = inputType
            editor.requestFocus()
            val manager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.restartInput(editor)
            manager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        waitForKeyboard()
    }

    private suspend fun awaitKeyboardMode(session: RimeSession, ascii: Boolean, key: (Key) -> Boolean) {
        var actualAscii: Boolean? = null
        var hasKey = false
        var keyCount = 0
        val reached = withTimeoutOrNull(10_000) {
            while (true) {
                instrumentation.runOnMainSync {
                    keyCount = KeyboardWindow.currentKeyboard.keys.size
                    hasKey = KeyboardWindow.currentKeyboard.keys.any(key)
                }
                actualAscii = session.runOnReady { getRuntimeOption("ascii_mode") }
                if (hasKey && actualAscii == ascii) break
                delay(50)
            }
        }
        if (reached == null) {
            fail("键盘路由超时：目标英文=$ascii，实际英文=$actualAscii，目标键存在=$hasKey，按键数=$keyCount")
        }
    }

    private suspend fun awaitEditorText(scenario: ActivityScenario<MainActivity>, expected: String) {
        withTimeout(10_000) {
            while (editorText(scenario) != expected) delay(50)
        }
    }

    @Test
    fun keyboardCommitsIntoAnApplicationWithoutSdk() = runBlocking<Unit> {
        val session = RimeDaemon.createSession(sessionName)
        try {
            awaitSchemes(session)
            session.runOnReady {
                clearComposition()
                selectSchema("luna_pinyin_simp")
                setRuntimeOption("ascii_mode", false)
            }
            instrumentation.targetContext.startActivity(Intent().apply {
                component = ComponentName("com.zhishurufa.sample.test", "com.zhishurufa.sample.test.ExternalEditorActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            assertTrue(device.wait(Until.hasObject(By.desc("external-editor")), 10_000))
            device.findObject(By.desc("external-editor")).click()
            waitForKeyboard()
            for (code in listOf(KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_O)) {
                tapKey { it.getCode(KeyBehavior.CLICK) == code }
            }
            tapKey { it.getCode(KeyBehavior.CLICK) == KeyEvent.KEYCODE_SPACE }
            withTimeout(10_000) {
                while (device.findObject(By.desc("external-editor"))?.text != "你好") delay(50)
            }
            screenshot("cross-app")
            device.pressBack()
            device.pressBack()
        } finally {
            RimeDaemon.destroySession(sessionName)
        }
    }

    @Test
    fun settingsRoutesOpenWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.res("com.zhishurufa.sample", "ime_settings")), 10_000))
            device.findObject(By.res("com.zhishurufa.sample", "ime_settings")).click()
            assertTrue(device.wait(Until.hasObject(By.text("虚拟键盘")), 10_000))
            for (route in listOf("虚拟键盘", "候选窗口", "高级")) {
                assertTrue("设置入口不存在：$route", device.wait(Until.hasObject(By.text(route)), 5_000))
                device.findObject(By.text(route)).click()
                assertTrue("设置页未打开：$route", device.wait(Until.hasObject(By.text(route)), 5_000))
                device.pressBack()
                assertTrue("设置页返回失败：$route", device.wait(Until.hasObject(By.text(route)), 5_000))
            }
        }
    }

    private fun waitForKeyboard() {
        assertTrue("键盘未显示", device.wait(Until.hasObject(By.res("com.zhishurufa.sample", "keyboard_view")), 20_000))
        device.waitForIdle()
    }

    private suspend fun awaitSchemes(session: RimeSession) {
        // 引擎报告就绪时，首次词库维护必须已完成。
        withTimeout(300_000) {
            session.runOnReady {
                assertTrue("引擎就绪时缺少已部署方案", selectedSchemata().size >= 10)
            }
        }
    }

    private fun tapKey(predicate: (Key) -> Boolean) {
        waitForKeyboard()
        val bounds = device.findObject(By.res("com.zhishurufa.sample", "keyboard_view")).visibleBounds
        var location: Pair<Int, Int>? = null
        instrumentation.runOnMainSync {
            val key = KeyboardWindow.currentKeyboard.keys.first(predicate)
            location = bounds.left + key.x + key.width / 2 to bounds.top + key.y + key.height / 2
        }
        val (x, y) = requireNotNull(location)
        assertTrue(device.click(x, y))
        device.waitForIdle()
    }

    private fun editorText(scenario: ActivityScenario<MainActivity>): String {
        var text = ""
        scenario.onActivity { text = it.findViewById<EditText>(R.id.input_test).text.toString() }
        return text
    }
}
