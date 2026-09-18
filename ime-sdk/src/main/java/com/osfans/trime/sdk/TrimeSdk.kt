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
    private val _editorSnapshot = MutableStateFlow<EditorSnapshot?>(null)

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

    internal fun publishEditorSnapshot(snapshot: EditorSnapshot) {
        _editorSnapshot.value = snapshot
    }

    internal fun clearEditorSnapshot() {
        _editorSnapshot.value = null
    }

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
