package com.osfans.trime.sdk

import android.app.Application
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Process
import android.os.Looper
import androidx.annotation.MainThread
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.receiver.RimeIntentReceiver
import com.osfans.trime.ui.main.MainActivity
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.isNightMode
import com.osfans.trime.worker.BackgroundSyncWork
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus

object TrimeSdk {
    const val INPUT_METHOD_SERVICE = "com.osfans.trime.ime.core.TrimeInputMethodService"

    private val initialization = SdkInitialization<Application>()
    private val hostActions = HostActionRouter()
    private val editorCommands = EditorCommandRouter()
    private val inputSchemeController = InputSchemeController()
    private val keyboardLayoutController = KeyboardLayoutController()
    private val aiController = AiController()
    private var aiCredentialStore: AiCredentialStore? = null
    @JvmStatic
    val drafts = DraftController()
    @JvmStatic
    val suggestions = SuggestionController()
    @JvmStatic
    val inputSchemes: InputSchemeController get() = inputSchemeController
    @JvmStatic
    val keyboardLayouts: KeyboardLayoutController get() = keyboardLayoutController
    @JvmStatic
    val ai: AiController get() = aiController
    private val _editorSnapshot = MutableStateFlow<EditorSnapshot?>(null)
    val isEditorContextEnabled: Boolean
        get() = editorContextOverride ?: AppPrefs.defaultInstance().advanced.editorContextEnabled.getValue()
    @Volatile
    private var editorContextOverride: Boolean? = null

    init {
        aiController.attachDraftController(drafts)
    }

    /** 建议注册应用级处理器；传入 null 可解除引用。 */
    @JvmStatic
    fun setHostActionHandler(handler: ImeHostActionHandler?) {
        hostActions.handler = handler
    }

    internal fun dispatchHostAction(action: String, payload: String): Boolean = hostActions.dispatch(action, payload)

    /** 返回已初始化的宿主 Application；必须在每个使用 SDK 的进程启动时调用 initialize。 */
    @JvmStatic
    fun requireInitialized(): Application = initialization.value

    internal val application: Application get() = requireInitialized()
    internal val coroutineScope by lazy { MainScope() + CoroutineName("TrimeSdk") }

    internal var lastPid: Int? = null
        private set

    internal var launcherComponent: ComponentName? = null
        private set

    internal var preferencesName = "trime_sdk.preferences"
        private set

    @JvmStatic
    val isInitialized: Boolean get() = initialization.isInitialized

    /** 当前编辑器上下文；宿主可订阅，用于草稿、续写和润色等上层能力。 */
    @JvmStatic
    val editorSnapshot: StateFlow<EditorSnapshot?> = _editorSnapshot.asStateFlow()

    internal fun publishEditorSnapshot(snapshot: EditorSnapshot?, enabled: Boolean = isEditorContextEnabled) {
        _editorSnapshot.value = if (enabled) snapshot else null
    }

    internal fun editorContextOverride(): Boolean? = editorContextOverride

    internal fun clearEditorSnapshot() {
        _editorSnapshot.value = null
    }

    /** 由宿主在用户主动启用上下文功能后调用；默认关闭，不持久化，不上传文本。 */
    @MainThread
    @JvmStatic
    fun setEditorContextEnabled(enabled: Boolean) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "请在主线程调用编辑器接口" }
        editorContextOverride = enabled
        clearEditorSnapshot()
        if (!enabled) drafts.clearBindings()
        editorCommands.contextEnabledChanged()
        refreshEditorSnapshot()
    }

    /** 主动获取当前片段；不可读、密码框或未启用时返回 null 并清除旧快照。 */
    @MainThread
    @JvmStatic
    fun refreshEditorSnapshot(): EditorSnapshot? {
        if (Looper.myLooper() != Looper.getMainLooper()) return null
        publishEditorSnapshot(if (isEditorContextEnabled) editorCommands.snapshot() else null)
        return _editorSnapshot.value
    }

    /**
     * 必须携带生成建议时的快照。过期会话、选区/文本变化、组合输入和非主线程调用均拒绝。
     * true 仅表示编辑器接受请求，不保证目标 App 已完成持久化，更不代表发送消息。
     */
    @MainThread
    @JvmStatic
    fun requestEditorCommand(command: EditorCommand, expected: EditorSnapshot): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper() || !isEditorContextEnabled) return false
        val accepted = editorCommands.dispatch(command, expected)
        refreshEditorSnapshot()
        return accepted
    }

    /** 返回 SDK 内置方案清单；该接口只读，不改变宿主状态。 */
    @MainThread
    @JvmStatic
    fun supportedInputSchemes(): List<InputScheme> = InputScheme.entries

    @MainThread
    @JvmStatic
    fun requestInputScheme(scheme: InputScheme): Boolean = inputSchemeController.request(scheme)

    /** 返回 SDK 支持的稳定布局清单；具体布局仍需由当前主题提供。 */
    @MainThread
    @JvmStatic
    fun supportedKeyboardLayouts(): List<KeyboardLayout> = KeyboardLayout.entries

    @MainThread
    @JvmStatic
    fun requestKeyboardLayout(layout: KeyboardLayout): Boolean = keyboardLayoutController.request(layout)

    internal fun publishInputScheme(id: String?) = inputSchemeController.publish(id)
    internal fun attachInputSchemeHandler(handler: InputSchemeRequestHandler) = inputSchemeController.attach(handler)
    internal fun detachInputSchemeHandler(handler: InputSchemeRequestHandler) = inputSchemeController.detach(handler)

    internal fun publishKeyboardLayout(id: String?) = keyboardLayoutController.publish(id)
    internal fun attachKeyboardLayoutHandler(handler: KeyboardLayoutRequestHandler) = keyboardLayoutController.attach(handler)
    internal fun detachKeyboardLayoutHandler(handler: KeyboardLayoutRequestHandler) = keyboardLayoutController.detach(handler)

    internal fun attachEditorCommandHandler(handler: EditorCommandHandler) = editorCommands.attach(handler)
    internal fun detachEditorCommandHandler(handler: EditorCommandHandler) = editorCommands.detach(handler)

    /** 宿主在 Application.onCreate 中调用；不会设置全局崩溃处理器或日志策略。 */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        application: Application,
        launcherAlias: String? = null,
    ) {
        initialize(application, launcherAlias, "trime_sdk.preferences")
    }

    internal fun initialize(
        application: Application,
        launcherAlias: String?,
        preferencesName: String,
    ) {
        initialization.initialize(application) {
            this.preferencesName = preferencesName
            val preferences = application.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            val prefs = AppPrefs.initDefault(preferences)
            aiCredentialStore = AiCredentialStore(application)
            aiController.configureFromSettings(aiCredentialStore?.load() ?: AiSettings())
            lastPid = prefs.internal.pid.getValue()
            prefs.internal.pid.setValue(Process.myPid())
            launcherComponent = launcherAlias?.let { resolveLauncherAlias(application, it) }
            ClipboardHelper.init(application)
            CollectionHelper.init(application)
            application.registerComponentCallbacks(
                object : ComponentCallbacks {
                    override fun onConfigurationChanged(newConfig: Configuration) {
                        if (ColorManager.currentScope() != null) {
                            ColorManager.onSystemNightModeChange(newConfig.isNightMode())
                        }
                    }

                    override fun onLowMemory() = Unit
                },
            )
            ContextCompat.registerReceiver(
                application,
                RimeIntentReceiver(),
                IntentFilter().apply {
                    addAction(RimeIntentReceiver.ACTION_DEPLOY)
                    addAction(RimeIntentReceiver.ACTION_SYNC_USER_DATA)
                },
                "android.permission.READ_INPUT_STATE",
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
            if (prefs.profile.periodicBackgroundSync.getValue()) {
                BackgroundSyncWork.start(application)
            }
        }
    }

    /** 从输入法设置页或宿主安全界面保存内置 AI 配置；密钥不会写入普通偏好。 */
    @JvmStatic
    fun saveBuiltInAiSettings(settings: AiSettings): Boolean {
        val store = aiCredentialStore ?: AiCredentialStore(requireInitialized())
        val persisted = store.save(settings)
        aiCredentialStore = store
        aiController.configureFromSettings(settings)
        return persisted
    }

    internal fun loadBuiltInAiSettings(): AiSettings {
        val settings = (aiCredentialStore ?: AiCredentialStore(requireInitialized())).load()
        aiController.configureFromSettings(settings)
        return settings
    }

    @JvmStatic
    fun clearBuiltInAiSettings(): Boolean {
        val store = aiCredentialStore ?: AiCredentialStore(requireInitialized())
        aiController.configureFromSettings(AiSettings())
        return store.clear()
    }

    private fun resolveLauncherAlias(
        context: Context,
        alias: String,
    ): ComponentName? {
        val component = ComponentName(context.packageName, alias)
        val info = try {
            @Suppress("DEPRECATION")
            context.packageManager.getActivityInfo(component, PackageManager.GET_DISABLED_COMPONENTS)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return component.takeIf { info.targetActivity == MainActivity::class.java.name }
    }

    @JvmStatic
    fun serviceComponent(hostPackageName: String): String {
        require(hostPackageName.isNotBlank() && '/' !in hostPackageName) { "Invalid host package name" }
        val serviceName = if (INPUT_METHOD_SERVICE.startsWith("$hostPackageName.")) {
            INPUT_METHOD_SERVICE.removePrefix(hostPackageName)
        } else {
            INPUT_METHOD_SERVICE
        }
        return "$hostPackageName/$serviceName"
    }

    @JvmStatic
    fun openSettings(context: Context) {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @JvmStatic
    fun openInputMethodSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @JvmStatic
    fun showInputMethodPicker(context: Context) {
        ContextCompat.getSystemService(context, InputMethodManager::class.java)?.showInputMethodPicker()
    }

    @JvmStatic
    fun isEnabled(): Boolean {
        requireInitialized()
        return InputMethodUtils.checkIsTrimeEnabled()
    }

    @JvmStatic
    fun isSelected(): Boolean {
        requireInitialized()
        return InputMethodUtils.checkIsTrimeSelected()
    }
}
