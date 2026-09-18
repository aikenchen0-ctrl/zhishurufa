# 中文输入资源包

本资源包位于 `ime-sdk/src/main/assets/shared`，由 SDK 的现有资源构建流程打包。独立 App 与宿主 App 读取相同方案和词库，不需要各自实现转换引擎。

## 已引入方案

| 显示名称 | schema_id | 主词库 | 编码或配置来源 |
| --- | --- | --- | --- |
| 自然码双拼 | `double_pinyin` | `luna_pinyin` | Rime 官方双拼仓库 |
| 小鹤双拼 | `double_pinyin_flypy` | `luna_pinyin` | Rime 官方双拼仓库 |
| 微软双拼 | `double_pinyin_mspy` | `luna_pinyin` | Rime 官方双拼仓库 |
| 智能 ABC 双拼 | `double_pinyin_abc` | `luna_pinyin` | Rime 官方双拼仓库 |
| 搜狗双拼 | `double_pinyin_sogou` | `luna_pinyin` | 雾凇拼音搜狗双拼编码映射 |
| 紫光双拼 | `double_pinyin_ziguang` | `luna_pinyin` | 雾凇拼音紫光双拼编码映射 |
| 五笔 86 | `wubi86` | `wubi86` | Rime 官方五笔仓库完整码表 |
| 九宫拼音 | `pinyin_t9` | `luna_pinyin` | 本工程数字映射，复用 Rime 拼音引擎 |

现有 `luna_pinyin_simp` 全拼与 `stroke` 笔画继续由子模块提供。此处只新增资源，没有改动原有子模块。

## 固定来源

下载日期：2026-09-18。

| 资源 | 一手来源 | 固定提交 |
| --- | --- | --- |
| 四套官方双拼方案 | [rime/rime-double-pinyin](https://github.com/rime/rime-double-pinyin) | `01a13287cbd27819be1c34fa1ddc1b3643d5001b` |
| 五笔方案与码表 | [rime/rime-wubi](https://github.com/rime/rime-wubi) | `152a0d3f3efe40cae216d1e3b338242446848d07` |
| 搜狗、紫光的编解码映射 | [iDvel/rime-ice](https://github.com/iDvel/rime-ice) | `59fcb4a6bfa71e6ba4fc83af07ee55f0c5b76081` |
| 既有朙月拼音 | [rime/rime-luna-pinyin](https://github.com/rime/rime-luna-pinyin) | `46acf03142c12b5aeed7002675046bf7255eed35` |
| 既有默认配置、标点及按键绑定 | [rime/rime-prelude](https://github.com/rime/rime-prelude) | `3803f09458072e03b9ed396692ce7e1d35c88c95` |
| 既有笔画方案与码表 | [rime/rime-stroke](https://github.com/rime/rime-stroke) | `7c9874c6b2e0b94947653e9a7de6f99623ff27e4` |
| 既有词频及词组资源 | [rime/rime-essay](https://github.com/rime/rime-essay) | `0766c929ec3e578c2c80861e988decd3703a1c3d` |

保留的来源许可证位于 `shared/licenses/rime-double-pinyin.txt`、`shared/licenses/rime-wubi.txt`、`shared/licenses/rime-ice.txt`；原文件版权与作者注释也保留。资源来源记录不代表更改各项目原有授权。

`wubi86.dict.yaml` 原样复制，文件大小为 2,426,526 字节，SHA-256：

```text
f10b8a1ad9414b6d2ef9563bc61fd390b6d75fd02024e5ed406e19e6e2941bfc
```

## 适配范围与依赖

1. 四套官方双拼只调整显示名称与 `simplification/reset: 1`，其编码、候选、反查流程保持上游实现。
2. 搜狗、紫光复用微软双拼的处理器、分词器、标点、简体过滤器和笔画反查；各自独立提供 `speller/algebra` 和 `translator/prism`。紫光另有独立的预编辑解码规则。映射提取自雾凇仓库 `others/no_lua_schema/` 下同名文件，暂存大写标记改为 ASCII，未引入其 Lua、英文词典、表情或拆字依赖。
3. 所有双拼依赖 `luna_pinyin.dict.yaml`、`stroke.schema.yaml`、`stroke.dict.yaml`、`default.yaml`、`key_bindings.yaml`、`punctuation.yaml` 和既有 OpenCC `t2s.json` 及其字典。各方案使用独立 prism，共享主词库，避免重复存储和互相覆盖编码表。
4. 五笔的主词库为原始简体码表。上游 `pinyin_simp` 反查改为已有的 `luna_pinyin`，方案部署依赖改为 `luna_pinyin_simp`；增加默认开启的简体过滤，使拼音反查候选也输出简体。标点继续使用已有 `symbols.yaml`。
5. 九宫继承 `luna_pinyin_simp.schema.yaml` 的简体设置和词库，使用独立 `pinyin_t9` prism。每个字母对应一次按键：`abcdefghijklmnopqrstuvwxyz` 对应 `22233344455566677778889999`。不同拼音落入同一数字编码后，由原生 `script_translator` 联合音节图和词频生成候选、组词；候选附带实际拼音以便消歧。
6. 九宫处理链不加载 `recognizer` 或 `matcher`，避免默认或宿主的数字正则将编码标成另一类输入。`speller` 位于 `selector` 之前，并把 `2..9` 同时列为编码字母和起始字符；这些数字被拼写器消费，不会触发数字选词。候选点击、空格确认、退格、方向键和翻页仍走既有引擎。
7. 九宫目前提供候选级消歧，不包含可固定某个音节的侧边拼音选择栏；它不依赖专有二进制或修改后的 Rime 引擎。独立的数字键盘应直接提交数字，避免把数字输入场景误交给九宫拼音编码。

源码依据来自工程已固定的 librime 提交 `33e78140250125871856cdc5b42ddc6a5fcd3cd4`：`gear/speller.cc` 接收 `speller/alphabet`，`gear/abc_segmentor.cc` 按同一字符集标记拼音段，`gear/script_translator.cc` 负责音节图及候选注音，`gear/selector.cc` 在其后处理候选选择。

## 引擎验收样例

预期词条应出现在候选中，不能把固定首选顺序作为断言，因为用户词频和历史会改变排序。双拼的分号必须作为编码键发送给引擎，不能通过直接提交标点绕过编码处理。

| schema_id | 输入编码 | 预期候选 |
| --- | --- | --- |
| `luna_pinyin_simp` | `nihao` | 你好 |
| `double_pinyin` | `nihk` / `vsgo` | 你好 / 中国 |
| `double_pinyin_flypy` | `nihc` / `vsgo` | 你好 / 中国 |
| `double_pinyin_mspy` | `nihk` / `bzj;` | 你好 / 北京 |
| `double_pinyin_abc` | `nihk` / `asgo` | 你好 / 中国 |
| `double_pinyin_sogou` | `nihk` / `bzj;` | 你好 / 北京 |
| `double_pinyin_ziguang` | `nihq` / `bkj;` | 你好 / 北京 |
| `wubi86` | `wqvb` / `khlg` | 你好 / 中国 |
| `pinyin_t9` | `64426` / `94664486` | 你好 / 中国 |
| `pinyin_t9` | `64'426` | 你好，验证手动分隔音节 |

九宫还应验证：逐个输入数字后组成串完整保留；`BackSpace` 只删最后一位；点选非首候选能提交对应汉字；退格后再次输入、方案切换后重输、应用重启后重输均正常。微软、搜狗与紫光应额外覆盖 `ing` 使用分号的情况。

当前资源已通过 YAML 解析、方案标识一致性、数字映射与五笔原文件哈希校验。API 36.1 模拟器已通过上述十种方案的真实 Rime 候选与提交验证，包括分号编码与九宫分词；九宫触摸、退格、编辑光标及跨 App 上屏已有仪器测试覆盖。未将模拟器结果视为所有物理机兼容性保证。
