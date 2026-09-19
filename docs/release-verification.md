# SDK 发布与稳定性验证

## 本轮修复

1. 中英文提示延时任务经 `withRimeContext` 回到引擎队列，避免与原生输入/退出并发读取同一会话；设备回归覆盖跨越提示时间的持续输入。
2. RimeConfig 读写与关闭共用实例锁，关闭幂等并清空指针；缺失配置的空句柄保持空值行为，关闭后的访问抛出明确异常。
3. 候选接口在 Kotlin 与 JNI 两层校验范围：起点非负，单次数量为 0 至 1024，避免负数变成原生超大分配。
4. 自动布局跟随状态不回写引擎模式；忽略滞后的中英文事件，修复快速切回中文后又被旧英文事件清空组合串。
5. 输入框策略按字段类型选择数字、电话、邮箱、密码、URI、日期时间布局；临时字段状态由服务级会话保存，输入连接真正结束时清理，避免跨编辑器恢复旧状态。
6. SDK 持久文件、符号历史和同步索引进入独立根目录；词库与 OpenCC 临时文件使用校验后的唯一操作目录，异常时只清理本次目录。

原生审查发现的线程竞争是确定缺陷，但没有证据证明它是历史 ARM64 SIGSEGV 的唯一原因。历史日志继续保留，不用一次通过覆盖历史失败。

## 发布产物

- 调试坐标：`com.zhishurufa:ime-sdk:0.1.0-SNAPSHOT`。
- 发布坐标：`com.zhishurufa:ime-sdk-release:0.1.0-SNAPSHOT`，附带源码 JAR。
- 仓库位置：`build/sdk-repository`，只发布到本机目录。
- Release AAR：`ime-sdk/build/outputs/aar/ime-sdk-release.aar`。
- 未提供正式签名参数时，独立输入法 Release APK 使用 Android debug 签名，仅用于本机安装验收；正式分发必须传入项目签名参数。
- 示例 Release APK 在 `verifyRelease=true` 时使用调试签名，仅用于本机验收。

```powershell
.\gradlew.bat :ime-sdk:publishReleasePublicationToLocalSdkRepository :app:assembleRelease '-PbuildABI=arm64-v8a,x86_64'
.\gradlew.bat :sample-host:assembleRelease :sample-host:assembleReleaseAndroidTest -PverifyRelease=true -PusePublishedSdk=true -PsdkPublication=release '-PbuildABI=arm64-v8a,x86_64'
```

示例宿主 Release 启用 R8 及资源压缩。仪器测试跨 APK 调用的 Kotlin、协程、Trace 和宿主 R 标识在验证专用配置中保留；它们不属于 SDK consumer rules，也不在普通 `verifyRelease=false` 的宿主构建生效。因此这是受控压缩验证，不代表每种宿主混淆配置都兼容。

## 可重复验收

```powershell
.\script\verify-sdk.ps1 -Variant release
.\script\verify-sdk.ps1 -Variant release -Abi x86_64
.\script\verify-device.ps1 -Serial emulator-5554 -Variant release -ConfigureIme -Adb 'C:/Users/Paifa/AppData/Local/Android/Sdk/platform-tools/adb.exe'
```

`ConfigureIme` 仅适用于明确用于验收的设备，会更改设备上当前启用和选择的输入法。`InstallAbi arm64-v8a` 可以在支持该 ABI 的模拟器执行转译对照。脚本在 `am instrument` 返回码为 0 但结果包含崩溃或失败时仍会返回失败。

十八组设备测试覆盖：十种方案候选及提交、方案切换、SDK 键盘布局请求、26键与旋转、九宫和编辑光标、输入法内上下文操作栏、键盘“换一个”真实按键、跨 App 输入、候选非法参数、真实原生配置关闭、快速中英文切换、编辑器安全写回、草稿面板保存恢复、短推荐授权与点击写回、内置 AI 设置对话框、AI 长按菜单防崩溃和设置页导航，并验证多 ABI 发布包。

425 项单元测试通过。本轮 Debug 十八组设备测试通过；本地隔离模拟器随后离线，草稿拖拽联动键盘高度的设备回归尚待重新启动模拟器后复跑；历史 Release x86_64 冷启动 88.329 秒（`build/device-release-x86_64.log`），ARM64 转译 91.080 秒（`build/device-release-arm64-v8a.log`，环境日志确认 `primaryCpuAbi=arm64-v8a`）。新增测试覆盖四类密码隔离、非主线程拒绝、旧会话拒绝、组合态拒绝、选区替换/删除/全选/提交，AI 并发取消、过期分片隔离、生命周期清理和多草稿绑定保留，草稿展示权、差异渲染、自动绑定、重试、保存恢复、确认写回、草稿高度策略、键盘高度策略、回车旁“换一个”、短推荐授权与点击写回、短推荐状态机、SDK 键盘布局请求、输入法内上下文操作栏、默认及标准主题 AI 工具栏、方案切换、内置 AI 设置对话框、AI 长按菜单防崩溃，以及三个设置页的内容与返回导航。AI 工具栏入口、五类 AI 操作、流式响应、SDK 内置重试、短推荐独立候选栏、输入法内上下文操作栏和独立设置页草稿面板随 Debug 构建验证；ARM64 物理机、32 位 ABI、所有厂商系统和正式商店签名分发尚未验收。

本轮真实修复：新建模拟器首次打开键盘，曾因主题尚未初始化在 `onCreateInputView -> getThemeScope` 崩溃（`build/editor-device-x86.log`）；等待主题就绪后替换占位视图，且系统回调不再重复挂载相同视图，冷启动回归通过。该堆栈是模拟器上的确定缺陷，不能反推此前手机闪退一定同源。安装签名失败也不等于运行时设置页崩溃。

隔离环境使用新 AVD `ZhishurufaSdkTest`、模拟器端口 `5590/5591`、ADB 端口 `5042`，未改动实体手机或停止 SuperDisplay。将 SDK 自带 adb 及两个 DLL 复制到忽略的 `build/isolated-adb`，以 `zhishurufa-adb.exe` 运行后连接稳定。脚本新增 `-AdbPort` 支持。发布 SDK 与编译 Maven 宿主分两次 Gradle 执行，避免同一任务图在 SDK 发布前解析旧 AAR。

2026-09-18 对照记录：双架构发布 APK 强制以 `primaryCpuAbi=arm64-v8a` 安装后，十三组测试通过，最新耗时见 `build/device-release-arm64-v8a.log`；原生 x86_64 发布测试十三组通过，见 `build/device-release-x86_64.log`。这属于有界验证，不能以单轮通过宣称历史间歇性 SIGSEGV 已被完全消除；其唯一根因仍未确认。

AI 实际联通验证：对配置的兼容端点 `https://api.cc2.cx/v1/chat/completions` 发起最小非流式请求，模型 `grok-4.6` 返回 HTTP 200 和预期测试文本。测试未把密钥、请求体或响应体写入仓库、日志或 APK；使用的凭据已在对话中暴露，后续应立即轮换。

新增生命周期的六项单元测试覆盖空配置兼容、重复关闭与关闭后读写拒绝；设备测试额外验证真实原生句柄。调试 AAR、调试独立 APK 与本地调试 Maven 坐标也已用相同修复重新构建。
