# 同文输入法 SDK 接入

## 本轮范围

根工程为同文。`ime-sdk` 提供输入法服务、设置页、Rime 引擎及内置布局；`app` 提供原独立输入法；`sample-host` 使用不同包名及自己的 Application 验证集成。

目前提供十种默认中文输入方案：简体全拼、九宫拼音、自然码/小鹤/微软/智能ABC/搜狗/紫光双拼、五笔86和笔画。独立主题 `zhishurufa.trime` 提供26键、九宫格、数字、电话、符号及编辑布局；微软等双拼有可参与编码的分号键。输入框策略按 Android `inputType` 自动路由数字、电话、邮箱和密码布局，并在临时字段之间保留首次中文方案与中英文偏好。

SDK 通过 `InputScheme` 提供稳定的内置方案标识枚举，宿主可用 `TrimeSdk.supportedInputSchemes()` 展示设置入口或自定义工具栏，并通过 `TrimeSdk.requestInputScheme(...)` 请求切换；方案实际执行仍由输入法服务中的 Rime 会话负责，SDK 不把宿主业务状态写入引擎。

键盘布局也有独立的稳定接口，不要求宿主拼接主题动作字符串：

```kotlin
TrimeSdk.requestKeyboardLayout(KeyboardLayout.T9_PINYIN)
TrimeSdk.requestKeyboardLayout(KeyboardLayout.EDIT)
val layouts = TrimeSdk.supportedKeyboardLayouts()
```

布局切换仅在当前主题实际提供该布局时生效；输入法服务会通过 `TrimeSdk.keyboardLayouts.current` 发布实际布局。未知布局、输入法未启用或当前主题缺少布局时请求返回 `false`。

## 内置 AI 草稿

SDK 内置 OpenAI 兼容的 Chat Completions 客户端，支持 Grok 和 OpenAI 两个预设，也支持自定义兼容代理。默认路由为 `DISABLED`；只有用户在输入法高级设置中配置并启用，或宿主明确配置并选择 `BUILT_IN` 后才会联网。输入法设置页使用 Android Keystore 加密保存密钥到不参与系统备份的私有目录；密钥不写入普通偏好、草稿、日志或 APK。Grok 预设默认使用 `https://api.cc2.cx`，OpenAI 预设默认使用 `https://api.openai.com`，端点和模型都可覆盖。

```kotlin
TrimeSdk.ai.configureBuiltIn(
    AiConfiguration.grok(
        apiKey = runtimeKey,
        endpoint = "https://api.cc2.cx",
        model = "grok-4.6",
    ),
)
TrimeSdk.ai.setRoute(AiProviderRoute.BUILT_IN)
val draft = TrimeSdk.ai.generateDraft(
    AiDraftRequest("按自然、简洁的语气回复", currentText),
    "ai-${System.currentTimeMillis()}",
    "Grok 草稿",
)
if (draft != null) TrimeSdk.drafts.replace(TrimeSdk.drafts.state.value.items + draft)
```

候选栏短预推荐由 SDK 自有控制器管理，与 Rime 候选索引分离。宿主或内置业务可推送最多六条短文本；用户点击后 SDK 读取当前编辑器快照并写入输入框，失败时保留推荐项，不自动发送：

```kotlin
TrimeSdk.suggestions.replace(
    listOf(
        SuggestionItem("confirm", "好的，我看到了"),
        SuggestionItem("later", "我晚点回复你"),
    ),
)
```

输入法内置 AI 菜单也提供“短推荐”操作。它使用同一套内置 Grok/OpenAI 路由生成多条短文本，再交给 `SuggestionController` 展示；用户仍需先开启编辑器上下文，点击建议才会写回输入框。

同一输入上下文的 AI 生成采用最新请求优先策略；旧流会被取消，过期分片不会覆盖新草稿。输入法结束编辑或服务销毁时，SDK 会取消未完成生成。多草稿按目标标识原子更新，其他草稿的编辑器绑定不会被清空。

SDK 内置的默认主题和标准主题都带有 `AI` 工具栏按钮，短按生成回复草稿，长按打开回复、重写、润色、续写和短推荐菜单；切换主题不会移除这组 SDK 入口。

当用户已开启编辑器上下文时，输入法窗口顶部还会显示光标上下文操作栏：有选区时提供重写和润色，无选区时提供重写、润色和续写，空输入提供回复和续写。操作结果进入草稿面板，不自动改写或发送原输入框。

长回复、多联系人草稿和红绿差异仍进入 `DraftController`。独立输入法设置页、宿主页面和输入法窗口共用同一控制器，展示权由 SDK 在同一时间只授予一个面板。

输入法默认中文主题的回车旁提供“换一个”动作，等价于 `TrimeSdk.drafts.next()`，只切换当前草稿，不会写入或发送输入框。草稿详情区支持向上增加高度、向下折叠详情，折叠后保留紧凑预览行。

宿主也可以保留同一套草稿 UI，注入自有服务或本地模型：

```kotlin
TrimeSdk.ai.setHostProvider(AiDraftProvider { request -> myBackend.generate(request) })
TrimeSdk.ai.setRoute(AiProviderRoute.HOST)
```

`DISABLED`、`BUILT_IN`、`HOST` 可运行时切换。AI 结果只进入 `DraftController`，SDK 会保留生成请求上下文，因此草稿红色重试按钮可由 SDK 内置重新生成；绿色确认按钮仍只写入当前输入框，不自动发送消息。网络失败、空响应和过期编辑器快照都不会自动改写输入框。协议参考：[xAI Chat Completions](https://docs.x.ai/developers/rest-api-reference/inference/chat-completions)。

键盘工具栏内置 `AI` 按钮，用户明确点击后才读取当前允许的编辑器上下文并生成草稿；密码框、不可读字段和未配置服务会直接拒绝。输入法内置设置与宿主注入是两条可选路径，共用同一套草稿和确认写回接口。

新安装使用应用专属目录，启用并选择系统输入法后即可部署输入。既有外部同步设置与自定义方案清单不会被覆盖；旧安装可在方案设置中启用新增方案，并选择“中文常用键盘”主题。

## 构建

在仓库根目录执行：

```powershell
git submodule update --init --recursive
.\gradlew.bat :ime-sdk:testDebugUnitTest :ime-sdk:assembleDebug :app:assembleDebug :sample-host:assembleDebug '-PbuildABI=arm64-v8a,x86_64'
```

环境采用仓库固定版本：JDK 25、Android SDK 36、NDK 28.0.13004108、CMake 3.31.6，并安装 Python 3。`local.properties` 中配置本机 `sdk.dir`。原生子模块与方案子模块保留在 `app` 下，构建与产物归属 `ime-sdk`。

当前验证产物同时包含 ARM64 和 x86_64。模拟器使用本机 x86_64 库；物理 ARM64 手机尚待验证。其他架构必须重新构建并验证对应原生库；省略 `buildABI` 会使用仓库默认的四种 ABI。

## 同仓接入

```kotlin
dependencies {
    implementation(project(":ime-sdk"))
}
```

保留宿主原有 Application，在 `onCreate` 调用：

```kotlin
override fun onCreate() {
    super.onCreate()
    TrimeSdk.initialize(this)
}
```

入口类为 `com.osfans.trime.sdk.TrimeSdk`。重复初始化相同 Application 不重复执行；初始化失败会保留并抛出原始异常。SDK 默认不替换宿主日志策略、崩溃处理器或启动入口。

宿主提供启用入口 `TrimeSdk.openInputMethodSettings(context)`，选择入口 `TrimeSdk.showInputMethodPicker(context)`，输入法设置入口 `TrimeSdk.openSettings(context)`。`isEnabled()` 与 `isSelected()` 查询当前状态。宿主安装后仍须用户在系统界面启用和选择输入法。嵌入 SDK 的每个宿主会注册一个独立输入法服务；若要求多个 App 共用一个系统输入法条目，应安装独立 `app` 并由宿主桥接，不能把多个 SDK 实例当成单一全局服务。

## 其他仓库接入

Release SDK 已提供独立坐标 `com.zhishurufa:ime-sdk-release:0.1.0-SNAPSHOT`。构建、压缩验收、签名边界和自动测试脚本见 [发布验证](release-verification.md)。原有调试坐标保持兼容。未配置正式签名时，独立 App Release 包使用 debug 签名，只用于本机安装验收。

```powershell
.\gradlew.bat :ime-sdk:publishDebugPublicationToLocalSdkRepository '-PbuildABI=arm64-v8a,x86_64'
.\gradlew.bat :sample-host:assembleDebug -PusePublishedSdk=true '-PbuildABI=arm64-v8a,x86_64'
```

该命令写入本地 `build/sdk-repository`，不上传远程服务。将此目录作为 Maven 仓库，依赖坐标为 `com.zhishurufa:ime-sdk:0.1.0-SNAPSHOT`。宿主仍需 `google()`、`mavenCentral()`、`https://jitpack.io` 解析传递依赖。

不要只引入裸 AAR 而遗漏依赖；Android AAR 不自动包含全部 AndroidX、Room、协程和其他库。Maven 发布同时提供依赖元数据。

## 宿主边界

- 最低系统版本 API 21，当前编译验证使用 SDK 36。
- SDK Java 包及 JNI 符号保持 `com.osfans.trime`；宿主 applicationId 可独立设置。
- 清单合入输入法服务、必要设置 Activity、DocumentsProvider 和通知/振动等权限，不合入 Launcher 或指定宿主 Application。
- SDK 设置 Activity 使用局部主题；示例宿主覆盖 `trime_app_name` 定制输入法显示名称。
- DocumentsProvider authority 使用 `${applicationId}.trime.documents`；仅暴露宿主外部文件目录下的 `trime-sdk` 子目录，检查文档路径不得越界。
- 偏好使用 `trime_sdk.preferences`，数据库使用 `trime_sdk.clipboard.db` 和 `trime_sdk.collection.db`。独立 App 显式保留旧存储路径以兼容原数据。
- 同一宿主的其他模块如使用 Rime JNI 或相同原生库名称，需要先核对版本，不能同时装入两套冲突实现。
- 已沿用的主题资源仍需要在接入时检查资源合并；现阶段不承诺任意宿主依赖版本组合均兼容。SDK 资源尚未全面加前缀，宿主应避免定义同名资源。

## 宿主动作接口

`TrimeSdk.setHostActionHandler(ImeHostActionHandler?)` 注册应用级动作处理器；传入 `null` 解除注册。必须注册 `Application` 级对象，不要捕获 Activity。回调参数为动作名与文本参数，返回值表示是否处理。回调同步执行在前端触发动作的线程，常规触摸来自主线程；SDK 不把业务回调调度到 Rime 线程。处理器必须快速返回，耗时工作由宿主自行调度，涉及 UI 时由宿主确保主线程。异常不会中断正常打字。

主题可以把明确的用户按键转交宿主，例如：

```yaml
preset_keys:
  OpenDraft:
    label: 草稿
    send: FUNCTION
    command: host_action
    select: draft.open
    option: "%1$s"
```

没有注册处理器时动作不执行。该接口不自动读聊天记录、发送消息或执行搜索跳转；草稿、短推荐和 AI 结果由 SDK 内部控制器统一承载，宿主只在需要时提供业务上下文或替代服务。按键、布局和 Rime 引擎不依赖具体宿主类。

独立输入法 App 默认关闭“读取当前输入框内容”，用户可在高级设置中主动开启；SDK 宿主也可通过 `setEditorContextEnabled(true/false)` 叠加控制。密码、数字密码、明确禁止个性化学习的输入框永不发布文本。快照最多接受 16384 个 UTF-16 字符，增量结果、超限或读取失败返回 `null`，不把缺失当空文本。`text` 是编辑器返回的片段，不保证全文；选区与组合索引相对于片段，`textOffset` 是片段起点，未知索引为 `-1`。`CursorAnchorInfo` 插入标记经矩阵转换后为屏幕像素坐标，编辑器未提供时 `cursor=null`，不用第一个字符的位置代替光标。

宿主可通过 `TrimeSdk.requestEditorCommand(command, expectedSnapshot)` 请求提交文本、替换选区、删除选区或全选。命令必须在主线程执行，且要求快照会话、文本、偏移、选区仍匹配；组合输入期间拒绝写回。失败返回 `false`，避免异步结果写入另一个输入框；宿主仍负责 AI 结果确认、撤销策略和发送时机。

`ReplaceSelection` 和 `DeleteSelection` 在没有选区时返回 `false`；`CommitText` 在当前光标插入或替换当前选区。`true` 仅表示目标编辑器接受调用，不保证已完成持久化。跨进程读取与写入不是原子事务，目标 App 可在两次调用间自行修改内容；对强一致场景宿主应使用自有编辑器协议。接口不发送消息、不模拟搜索，也不执行网络请求。

```kotlin
// 在主线程捕获建议的来源，异步生成后必须继续使用这份快照。
TrimeSdk.setEditorContextEnabled(true)
val source = TrimeSdk.refreshEditorSnapshot()
// 用户确认后，在主线程写入；过期则提示用户重新生成，不自动改写新输入框。
if (source != null) {
    val accepted = TrimeSdk.requestEditorCommand(EditorCommand.CommitText("confirmed text"), source)
}
```

编辑布局可设置 `preserve_ascii_mode: true`，在进入编辑面板时保留当前中英文模式及组合内容。字段策略覆盖普通文本、数字、电话、邮箱、密码、URI 和日期/时间；日期时间布局提供斜杠、短横线、冒号等分隔符，缺少专用布局的自定义主题会回退到数字布局。

所有 `TrimeSdk` 入口必须在宿主 `Application.onCreate` 调用 `TrimeSdk.initialize(this)` 后使用；可用 `TrimeSdk.requireInitialized()` 主动检查启动顺序。宿主若配置独立进程，必须在每个进程分别初始化。

## 验证记录

2026-09-19 最新记录：425 项单元测试通过；本轮双架构 Debug SDK、独立 App APK、示例宿主 APK 与候选栏短推荐编译验证通过。草稿详情高度现在会同步调整键盘按键区高度；Debug 模拟器十八组设备测试覆盖短推荐授权、点击写回、SDK 键盘布局切换、输入法内上下文操作栏和键盘“换一个”真实按键；AI 草稿生成会自动绑定生成时快照，新增并发取消、过期分片隔离、生命周期清理和多草稿绑定保留测试，草稿高度策略和键盘高度策略有单元覆盖，设置页挂载宿主草稿面板，默认及标准主题均提供 AI 工具栏入口。运行 `script/verify-sdk.ps1 -Variant release` 和 `script/verify-sdk.ps1 -Variant release -Abi x86_64` 可复查原生引擎、词库及资源哈希。SDK 发布与 Maven 宿主构建应分开执行，确保解析到刚发布的 AAR。

中文功能的设备验证命令：

```powershell
.\gradlew.bat :sample-host:connectedDebugAndroidTest '-PbuildABI=arm64-v8a,x86_64' '-Pandroid.testInstrumentationRunnerArguments.configureIme=true'
```

`configureIme=true` 仅用于测试设备，仪器测试会通过 shell 启用并选择示例输入法；产品本身仍由用户在系统界面启用和选择。测试覆盖真实 Rime 候选与上屏、触摸26键、九宫输入、退格、数字切换、编辑光标及横竖屏。当前设备是 API 36.1 模拟器，不能代替各厂商物理机兼容性验收。

最新 Release x86_64 干净模拟器和 ARM64 转译各十四组测试通过，包括草稿面板、本地保存恢复、方案切换、输入方案、内置 AI 设置、AI 长按菜单防崩溃、字段切换、跨 App 输入、编辑器安全写回及虚拟键盘/候选窗口/高级设置页。URI 与片段偏移的模型语义由单元测试覆盖。历史转译 SIGSEGV 的唯一根因仍未确认，ARM64 真机仍待验证。详见发布验证记录。
