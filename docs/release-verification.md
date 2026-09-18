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

八组设备测试覆盖：十种方案候选及提交、26键与旋转、九宫和编辑光标、跨 App 输入、候选非法参数、真实原生配置关闭及快速中英文切换期间的组合串，并验证多 ABI 发布包。

354 项单元测试通过；原生 x86_64 Release 宿主八组设备测试通过，ARM64 转译对照也通过八组。ARM64 物理机、32 位 ABI、所有厂商系统和正式商店签名分发尚未验收。

2026-09-18 对照记录：双架构发布 APK 强制以 `primaryCpuAbi=arm64-v8a` 安装后，八组测试通过，最新耗时见 `build/device-release-arm64-v8a.log`；原生 x86_64 发布测试八组通过，见 `build/device-release-x86_64.log`。这属于有界验证，不能以单轮通过宣称历史间歇性 SIGSEGV 已被完全消除；其唯一根因仍未确认。

新增生命周期的六项单元测试覆盖空配置兼容、重复关闭与关闭后读写拒绝；设备测试额外验证真实原生句柄。调试 AAR、调试独立 APK 与本地调试 Maven 坐标也已用相同修复重新构建。
