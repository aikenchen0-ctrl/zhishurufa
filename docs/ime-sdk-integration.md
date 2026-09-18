# 同文输入法 SDK 接入

## 本轮范围

根工程为同文。`ime-sdk` 提供输入法服务、设置页、Rime 引擎及内置布局；`app` 提供原独立输入法；`sample-host` 使用不同包名及自己的 Application 验证集成。

目前提供十种默认中文输入方案：简体全拼、九宫拼音、自然码/小鹤/微软/智能ABC/搜狗/紫光双拼、五笔86和笔画。独立主题 `zhishurufa.trime` 提供26键、九宫格、数字、电话、符号及编辑布局；微软等双拼有可参与编码的分号键。输入框策略按 Android `inputType` 自动路由数字、电话、邮箱和密码布局，并在临时字段之间保留首次中文方案与中英文偏好。

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

没有注册处理器时动作不执行。该接口不自动读聊天记录、生成草稿或请求网络；草稿库、推荐与其他业务由宿主实现。按键、布局和 Rime 引擎不依赖具体宿主类。

SDK 通过 `TrimeSdk.editorSnapshot` 暴露只读编辑器上下文流。快照包含全文、选区、光标前后是否有文本、组合区索引和 `CursorAnchorInfo` 映射后的编辑器局部光标框。宿主可用它实现草稿预览、续写、润色和光标浮标；SDK 不在该接口中执行 AI、网络、OCR、无障碍或自动发送。

编辑布局可设置 `preserve_ascii_mode: true`，在进入编辑面板时保留当前中英文模式及组合内容。字段策略覆盖普通文本、数字、电话、邮箱、密码、URI 和日期/时间；日期时间布局提供斜杠、短横线、冒号等分隔符，缺少专用布局的自定义主题会回退到数字布局。

所有 `TrimeSdk` 入口必须在宿主 `Application.onCreate` 调用 `TrimeSdk.initialize(this)` 后使用；可用 `TrimeSdk.requireInitialized()` 主动检查启动顺序。宿主若配置独立进程，必须在每个进程分别初始化。

## 验证记录

2026-09-18 最新记录：354 项单元测试通过；双架构 Debug/Release AAR、独立 App APK、示例宿主 APK、本地 Maven 发布与接入构建均通过。执行 `script/verify-sdk.ps1` 和 `script/verify-sdk.ps1 -Abi x86_64` 可复查原生引擎、词库及资源哈希；发布产物加上 `-Variant release`。

中文功能的设备验证命令：

```powershell
.\gradlew.bat :sample-host:connectedDebugAndroidTest '-PbuildABI=arm64-v8a,x86_64' '-Pandroid.testInstrumentationRunnerArguments.configureIme=true'
```

`configureIme=true` 仅用于测试设备，仪器测试会通过 shell 启用并选择示例输入法；产品本身仍由用户在系统界面启用和选择。测试覆盖真实 Rime 候选与上屏、触摸26键、九宫输入、退格、数字切换、编辑光标及横竖屏。当前设备是 API 36.1 模拟器，不能代替各厂商物理机兼容性验收。

最新 Release 原生 x86_64 模拟器八组测试通过，包括数字、电话、邮箱和密码字段路由，以及向未集成 SDK 的独立测试 APK 输入中文；URI 路由由单元策略测试覆盖。ARM64 强制转译的一轮相同测试也通过，但历史转译 SIGSEGV 的唯一根因仍未确认，ARM64 真机仍待验证。详见发布验证记录。
